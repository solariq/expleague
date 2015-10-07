angular.module('knuggetApiFactory', []).factory('knuggetApi', ['$http', '$q', 'fileBlob', function ($http, $q, $fileBlob) {

    function JabberClient(login, password, resource) {
        this.login = login;
        this.password = password;
        this.resource = resource;
        this.connection = new Strophe.Connection('http://toobusytosearch.net:5280/http-bind');
        //this.connection = new Strophe.Connection('http://localhost:5280/http-bind');

        this.loginUser = function(nick, callback) {
            //todo set resource
            this.connection.connect(nick ? this.login + '/' + nick : this.login, this.password, callback);
        };

        this.logout = function(reason) {
            this.connection.disconnect(reason);
            this.connection.reset();
        };

        this.send = function (message, callback) {
            console.log("sending to " + message.to);
            console.log("from " + this.connection.jid);
            console.log("text " + message.text);

            var msg = $msg({to: message.to, from: this.connection.jid, type: 'groupchat'})
                .c('body')
                .t(message.text);
            this.connection.send(msg.tree());
            callback();
        };

        this.addMessageListener = function(listener) {
            this.connection.addHandler(function(msg) {
                var to = msg.getAttribute('to');
                var from = msg.getAttribute('from');
                var type = msg.getAttribute('type');
                var elems = msg.getElementsByTagName('body');

                if (type == "chat" && elems.length > 0) {
                    var body = elems[0];
                    //listener({text: Strophe.getText(body), from: from, time: new Date().getTime()});
                    listener({question: Strophe.getText(body), owner: from, time: new Date().getTime(), id: new Date().getTime()});
                }
                return true;
            }, null, 'message', null, null, null);
        };
        
        this.addPresenceListener = function(listener, from) {
            this.connection.addHandler(function(presence) {
                var from = presence.getAttribute('from');
                var type = presence.getAttribute('type');
                if (!type) {
                    type = 'available';
                }
                listener({from: from, type: type});
                return true;
            }, null, 'presence', null, null, from)
        };

        this.addInviteListener = function(listener) {
            this.connection.addHandler(function(msg) {
                var to = msg.getAttribute('to');
                var room = msg.getAttribute('from');
                var elems = msg.getElementsByTagName('invite');
                if (elems && elems.length > 0) {
                    var from = elems[0].getAttribute('from');
                    obj = {room: room, from: from, time: new Date().getTime()};
                    params = msg.children[0].children;
                    for (var i = 0; i < params.length; i++) {
                        el = params[i];
                        name = el.nodeName;
                        if (name != 'invite') {
                            obj[name] = Strophe.getText(el.childNodes[0]);
                        }
                    }
                    listener(obj);
                }
                return true;
            }, null, 'message', null, null, null);
        };

        this.leaveRoom = function(room, nick) {
            this.sendPres({to: room + '/' + nick, type: 'unavailable'});
            //var pres = $pres({to: room + '/' + nick, type: 'unavailable'});
            //this.connection.send(pres.tree());
        };

        this.enterRoom = function(room, nick) {
            this.sendPres({to: room + '/' + nick});
            //var pres = $pres({to: room + '/' + nick})
            //    .c('priority')
            //    .t(5);
            //this.connection.send(pres.tree());
            //callback();
        };

        this.sendPres = function(presence) {
            var pres = $pres(presence);
            this.connection.send(pres.tree());
        }
    }

    var ExpertState = {
        READY: {
            value: 'ready',
            onSet: function() {
                jabberClient.sendPres({type: 'available'});
            }
        },
        AWAY: {
            value: 'away',
            onSet: function() {
                jabberClient.sendPres({type: 'unavailable'});
            }
        },
        CHECK: {
            value: 'check',
            validate: function(prevState) {
                return prevState == ExpertState.READY;
            },
            onSet: function(presence) {
                stateController.setState(ExpertState.STEADY, presence);
            }
        },
        STEADY: {
            value: 'steady',
            validate: function(prevState) {
                return prevState == ExpertState.CHECK;
            },
            onSet: function(presence) {
                jabberClient.sendPres({to: presence.from, type: ExpertState.STEADY.value});
                //todo handle timeout
            }
        },
        INVITE: {
            value: 'invite',
            validate: function(prevSate) {
                return prevSate == ExpertState.STEADY;
            },
            onSet: function(invite) {
                //todo remove this
                if (!invite.subj) {
                    invite.subj = 'Question stub!';
                }
                question = {question: invite.subj, owner: invite.from, time: invite.time, id: invite.time, room: invite.room};
                addQuestion(question, function(){});
            }
        },
        ACCEPT: {
            value: 'accept',
            validate : function(prevSrate) {
                return prevSrate == ExpertState.INVITE
            },
            onSet: function(data) {
                //jabberClient.sendPres(data.request.room, 'expert');
                jabberClient.enterRoom(data.request.room, 'expert');
                //todo remove this
                stateController.setState(ExpertState.GO);
            }
        },
        DENIED: {
            value: 'denied',
            validate: function(prevState) {
                return prevState == ExpertState.ACCEPT;
            },
            onSet: function(ctx) {
                //todododododo
            }
        },
        GO: {
            value: 'go',
            validate: function(prevState) {
                return prevState == ExpertState.ACCEPT;
            },
            onSet: function(ctx) {
                allowToShow(true);
            }
        }
    };

    function StateController() {
        this.state = ExpertState.AWAY;

        this.setState = function(newState,  context) {
            //validation is disabled
            if (true || newState.validate(this.state)) {
                this.state = newState;
                allowToShow(false);
                newState.onSet(context);
                return true;
            }
            return false;
        };

        this.rejectToEnterListener = function() {
            //todo
        };
    }

    var jabberClient = null;
    var stateController = null;

    //var connection = new Strophe.Connection('http://localhost:5280/http-bind');
    //var connection = new Strophe.Connection('http://toobusytosearch.net:5280/http-bind');

    // TODO get API url from config
    var apiUrl = KNUGGET.config.domain + "api";

    if (Notification.permission !== "granted") {
        alert('Пожалуйста, разрешите доступ к уведомлениям');
        Notification.requestPermission(function (permission) {
            if( permission != "granted" ) return false;
            var notify = new Notification("Thanks for letting notify you");
        });
    }

    addToBoardSync = function (answer, callback) {
        KNUGGET.storage.get("Board", function (value) {
            value = value ? JSON.parse(value) : [];
            value.push(answer);
            KNUGGET.storage.set("Board", JSON.stringify(value));
            callback();
        });
    };

    addQuestion = function(question, callback) {
        KNUGGET.storage.get("Requests", function (value) {
            value = value ? JSON.parse(value) : [];
            //value = [];
            value.push(question);
            KNUGGET.storage.set("Requests", JSON.stringify(value));
            callback();
        });
    };

    allowToShow = function(isAllowed) {
        KNUGGET.storage.set("AllowToShow", isAllowed);
    };

    removeRequest = function(request, callback) {
        KNUGGET.storage.get("Requests", function (value) {
            value = value ? JSON.parse(value) : [];
            result = [];
            value.forEach(function(el, i) {
                if (request.id != el.id)
                    result.push(el);
            });
            KNUGGET.storage.set("Requests", JSON.stringify(result));
            callback();
        });
    };

    // API METHODS
    return {

        resetBoard: function (data) {
            var defer = $q.defer();
            KNUGGET.storage.set("Board", JSON.stringify([]));
            defer.resolve({status: 200});
            return defer.promise;
        },

        SendResponse: function(data) {
            var defer = $q.defer();
            KNUGGET.storage.get("Board", function (board) {
                board = board ? JSON.parse(board) : [];
                jabberClient.send({
                        to: data.request.room,
                        text: JSON.stringify(board)
                    }, function () {
                        defer.resolve({status: 200});
                    }
                );
            });
            return defer.promise;
        },

        Finish: function(data) {
            var defer = $q.defer();
            //todo data.request
            //connection.send($pres().tree());
            jabberClient.leaveRoom(data.request.room, 'expert');
            defer.resolve({status: 200});
            return defer.promise;
        },

        Activate: function(data) {
            var defer = $q.defer();
            stateController.setState(ExpertState.ACCEPT, data);
            defer.resolve({status: 200});
            return defer.promise;
        },

        Reject: function(data) {
            alert('rejecting');
            var defer = $q.defer();
            removeRequest(data.request, function() {
                defer.resolve({status: 200});
            });
            jabberClient.setState(ExpertState.READY, null);
            return defer.promise;
        },

        Notify: function(data) {
            var defer = $q.defer();
            var promise = defer.promise;
            var message = new Notification(data.owner, {
                tag : data.tag,
                body : data.body
            });
            message.onclick = function(){
                defer.resolve({status: 200});
            };
            message.onclose = function(){
                if (promise.$$state.status == 0) {
                    defer.resolve({status: 501});
                }
            };

            setTimeout(function(){
                message.close();
                defer.reject({status : 500, messages : "Timeout"});
            }, 30000);

            //todo add timeout
            return promise;
        },

        Remove: function(data) {
            var defer = $q.defer();
            KNUGGET.storage.get("Board", function (value) {
                value = value ? JSON.parse(value) : [];
                if (value.length <= data.index) {
                    defer.resolve({status: 500});
                    return;
                }

                value.splice(data.index, 1);
                KNUGGET.storage.set("Board", JSON.stringify(value));
                defer.resolve({status: 200});
            });
            return defer.promise;
        },

        Move: function(data) {
            var defer = $q.defer();
            fromPos = data.fromPos;
            toPos = data.toPos;
            //alert(fromPos + ' -> ' + toPos);
            KNUGGET.storage.get("Board", function (value) {
                arr = value ? JSON.parse(value) : [];
                if (fromPos == toPos || fromPos > arr.length || toPos > arr.length || fromPos < 0 || toPos < 0) {
                    defer.resolve({status: 500});
                    return;
                }
                cur = fromPos;
                dir = toPos - fromPos > 0 ? 1 : -1;
                while (cur != toPos) {
                    tmp = arr[cur];
                    nextIdx = cur + dir;
                    arr[cur] = arr[nextIdx];
                    arr[nextIdx] = tmp;
                    cur = nextIdx;
                }
                KNUGGET.storage.set("Board", JSON.stringify(arr));
                defer.resolve({status: 200});
            });
            //defer.resolve({status: 200});
            return defer.promise;
        },

        addToBoard: function (data) {
            //KNUGGET.storage.set("Board", JSON.stringify([]));
            answer = data.answer;
            var defer = $q.defer();
            addToBoardSync(answer, function() {
                defer.resolve({status: 200});
            });
            return defer.promise;
        },

        loginUser: function (data) {
            var future = $q.defer();

            if (jabberClient) {
                jabberClient.logout();
            }
            jabberClient = new JabberClient(data.Username, data.Password, 'expert');
            stateController = new StateController();
            jabberClient.loginUser('expert', function(status) {
                if (status == Strophe.Status.CONNECTING) {
                }
                else if (status == Strophe.Status.AUTHFAIL) {
                    alert('Strophe failed to auth');
                    future.reject({status : 500, messages : "Wrong password or user name"});
                }
                else if (status == Strophe.Status.CONNECTED) {
                    future.resolve({status : 200});
                    KNUGGET.storage.set("UserActive", {Active: true, Username : data.Username});
                    KNUGGET.storage.set("IsConnected", true);
                    //

                    jabberClient.sendPres();

                } else if (
                    status == Strophe.Status.ERROR ||
                    status == Strophe.Status.CONNFAIL ||
                    status == Strophe.Status.DISCONNECTED ||
                    status == Strophe.Status.DISCONNECTING
                ){
                    alert('Strophe failed to connect.');
                    future.reject({status : 500, messages : "Connectiont to server failed"});
                    KNUGGET.storage.set("UserActive", {Active: false, Username : data.Username});
                    KNUGGET.storage.set("IsConnected", false);
                    //jabberClient.logout('going to reconnect');
                    //todo reconnect
                }
            });

            jabberClient.addMessageListener(function(msg) {
                alert('addMessageListener: ' + msg.text);
            });

            jabberClient.addInviteListener(function(invite) {
                stateController.setState(ExpertState.INVITE, invite);
            });
            jabberClient.addPresenceListener(function(presence) {
                ptype = presence.type.toUpperCase();
                if (ptype in ExpertState) {
                    this.setState(ptype, presence);
                }
            });

            return future.promise;
        },

        Logout: function (data) {
            var defer = $q.defer();
            reason = data.reason ? data.reason : 'unknown';
            jabberClient.logout(reason);
            KNUGGET.storage.set("UserActive", {Active: false, Username : ''});
            KNUGGET.storage.set("IsConnected", false);

            defer.resolve({status : 200});

            return defer.promise;
        },

        Upload: function (data, senderId) {

            data.SenderId = senderId;

            if ((data.Files && data.Files[0]) || data.Base64Image || data.MakeSnapshot || data.Image) {
                
                var fileProcessing = $fileBlob.fileProcessing(data);

                fileProcessing.success = function (fn) {
                    fileProcessing.then(function (response) {
                        fn(response.data, response.status, response.headers);
                    });
                    return fileProcessing;
                };

                fileProcessing.error = function (fn) {
                    fileProcessing.then(null, function (response) {
                        fn(response.data, response.status, response.headers);
                    });
                    return fileProcessing;
                };

                return fileProcessing;

            } else {

                // Define a promise
                var itemCreated = $http.post(apiUrl + '/item/add', data);

                return itemCreated;
            }
        },

    };
}]);

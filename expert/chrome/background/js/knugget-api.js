angular.module('knuggetApiFactory', []).factory('knuggetApi', ['$http', '$q', '$interval', 'fileBlob', function ($http, $q, $interval, $fileBlob) {

    function findByTagName(nl, tag) {
        for (i = 0; i < nl.length; ++i) {
            if (nl[i].tagName == tag) {
                return nl[i];
            }
        }
        return null;
    }

    function Offer(node) {
        this.room = node.getAttribute('room');
        this.client = node.getAttribute('client');
        this.subj = node.getElementsByTagName('subject')[0].textContent
        this.node = node;
    }

    function NotificationCache() {
        this.notifications = {};

        this.getDefer = function(data) {
            var id = data.id;
            var defer = this.notifications[id];
            if (defer) {
                return defer;
            }
            defer = $q.defer();

            var message = new Notification(data.owner, {
                tag : data.tag,
                body : data.body
            });
            var promise = defer.promise;
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
            }, 6000);

            this.notifications[id] = promise;

            var ntfctns = this.notifications;
            setTimeout(function() {
                delete ntfctns[id];
            }, 1000);

            return promise;

        }
    }

    var ncache = new NotificationCache();
    function BanManager() {
        this.getBanList = function(callback) {
            KNUGGET.storage.get("BanList", function (value) {
                value = value != '' ? value : '[]';
                callback(JSON.parse(value))
            });
        };

        this.banUser = function(user) {
            this.getBanList(function(list) {
               list.push(user);
                KNUGGET.storage.set("BanList", JSON.stringify(user));
            });
        };

        this.isBanned = function(user, callback) {
            this.getBanList(function(list){
               callback(list.indexOf(user) > -1);
            });
        };
    }

    function JabberClient(login, password, resource) {
        this.login = login;
        this.password = password;
        this.resource = resource;
        this.host = login.split('@')[1];

        //this.connection = new Strophe.Connection('http://expleague.com:5280/http-bind');
        //this.connection = new Strophe.Connection('http://' + this.host + ':5280/');
        this.connection = new Strophe.Connection('http://' + this.host + ':5280/');

        this.register = function (regCallback) {
            conn = this.connection;
            pass = this.password;
            login = this.login;
            var callback = function (status) {
                if (status === Strophe.Status.REGISTER) {
                    // fill out the fields
                    conn.register.fields.username = login;
                    conn.register.fields.password = pass;
                    // calling submit will continue the registration process
                    conn.register.submit();
                } else if (status === Strophe.Status.REGISTERED) {
                    console.log("registered!");
                    conn.authenticate(); //todo?
                    regCallback({registrated: true});
                } else if (status === Strophe.Status.CONFLICT) {
                    console.log("Contact already existed!");
                    regCallback({registrated: false, msg: "Такой логин уже существует!"});
                } else if (status === Strophe.Status.NOTACCEPTABLE) {
                    console.log("Registration form not properly filled out.")
                    regCallback({registrated: false, msg: "Отказ в регистрации, обратитесь к администратору!"});
                } else if (status === Strophe.Status.REGIFAIL) {
                    console.log("The Server does not support In-Band Registration")
                    regCallback({registrated: false, msg: "Отказ в регистрации, обратитесь к администратору!"});
                } else if (status === Strophe.Status.CONNECTED) {
                    console.log('connected')
                    // do something after successful authentication
                } else {
                    // Do other stuff
                }
            };
            this.connection.register.connect(this.host, callback);
        };

        this.loginUser = function (nick, callback) {
            //todo set resource
            this.connection.connect(nick ? this.login + '/' + nick : this.login, this.password, callback);
        };

        this.logout = function (reason) {
            this.connection.disconnect(reason);
            this.connection.reset();
        };


        this.unsafeSend = function(msg, callback) {
            this.connection.send(msg.tree());
            callback();
        };

        this.send = function (message, type, callback) {
            console.log("sending to " + message.to);
            console.log("from " + this.connection.jid);
            console.log("text " + message.text);

            var msg = $msg({to: message.to, from: this.connection.jid, type: type != null ? type : 'groupchat'})
                .c('body')
                .t(message.text);
            this.connection.send(msg.tree());
            callback();
        };

        this.addOfferListener = function(listener) {
            this.connection.addHandler(function(msg) {
                var offer = findByTagName(msg.childNodes, 'offer');
                if (offer) {
                    offer = new Offer(offer);
                    isCanceled = findByTagName(msg.childNodes, 'cancel') != null;
                    listener(msg.getAttribute('id'), msg.getAttribute('from'), offer, isCanceled);
                }
            }, null, 'message', null, null, null);
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
                    listener(body, {question: Strophe.getText(body), owner: from, time: new Date().getTime(), id: new Date().getTime()});
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
                var elems = msg.getElementsByTagName('invite');
                if (elems && elems.length > 0) {
                    var offer = new Offer(msg.getElementsByTagName('offer')[0]);
                    var invite = {};
                    invite.subj = offer.subj;
                    invite.owner = offer.client;
                    invite.room = offer.room.replace("/client", "");
                    invite.time= new Date().getTime();
                    invite.img = 'http://3.bp.blogspot.com/_f3d3llNlZKQ/SxrJWGZywvI/AAAAAAAABg0/2rV7MNks1lw/s400/Prova.jpg';
                    invite.confirmExpireTime =  Date.now() + 965 * 1000;
                    invite.resolveExpireTime = Date.now() + 9150 * 1000;
                    invite.map = { center: { latitude: 59.977755, longitude: 30.3343742 }, zoom: 15};
                    listener(invite);
                }
                return true;
            }, null, 'message', null, null, null);
        };

        this.leaveRoom = function(room, nick) {
            //todo this!
            //this.sendPres({to: room + '/' + nick, type: 'unavailable'});
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
        };

        this.sendUnavailable = function() {
            this.sendPres({type: 'unavailable'});
        };

        this.sendAvailable = function() {
            this.sendPres({type: 'available'});
        };
    }

    debug = true;
    var banManager = new BanManager();
    var ExpertState = {
        READY: {
            value: 'ready',
            onSet: function() {
                cleanAll();
                jabberClient.sendPres({type: 'available'});
            }
        },
        AWAY: {
            value: 'away',
            onSet: function() {
                cleanAll();
                jabberClient.sendPres({type: 'unavailable'});
            }
        },
        CHECK: {
            value: 'check',
            validate: function(prevState, ctx) {
                new BanManager().isBanned(ctx.user, function(isBanned) {
                    if (!isBanned) {
                        var msg = $msg({to: ctx.to, from: jabberClient.connection.jid, type: 'chat'})
                            .c('ok')
                            .attrs({xmlns: "http://expleague.com/scheme"})
                            .up()
                            .cnode(ctx.offer.node);
                        jabberClient.unsafeSend(msg, function(){});
                    } else {
                        stateController.setState(ExpertState.READY, {});
                    }
                });
                //todo check banManager.isBanned(user)
                return debug || prevState == ExpertState.READY;
            },
            onSet: function(presence) {
                stateController.setState(ExpertState.STEADY, presence);
            }
        },
        STEADY: {
            value: 'steady',
            validate: function(prevState, ctx) {
                return debug || prevState == ExpertState.CHECK;
            },
            onSet: function(presence) {
                jabberClient.sendPres({to: presence.from, type: ExpertState.STEADY.value});
                //todo handle timeout
            }
        },
        INVITE: {
            value: 'invite',
            validate: function(prevState, ctx) {
                return debug || prevState == ExpertState.STEADY;
            },
            onSet: function(invite) {
                //todo remove this
                if (!invite.subj) {
                    invite.subj = 'Question stub!';
                }
                //todo kvv insert real img url
                question = {
                    question: invite.subj,
                    owner: invite.owner,
                    time: invite.time,
                    id: invite.time,
                    room: invite.room,
                    img: 'http://3.bp.blogspot.com/_f3d3llNlZKQ/SxrJWGZywvI/AAAAAAAABg0/2rV7MNks1lw/s400/Prova.jpg',
                    confirmExpireTime: Date.now() + 965 * 1000,
                    resolveExpireTime: Date.now() + 9150 * 1000,
                    map: { center: { latitude: 59.977755, longitude: 30.3343742 }, zoom: 15}
                };
                addQuestion(question, function(){});
            }
        },
        ACCEPT: {
            value: 'accept',
            validate: function(prevState, ctx) {
                return debug || prevState == ExpertState.INVITE
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
            validate: function(prevState, ctx) {
                return debug || prevState == ExpertState.ACCEPT;
            },
            onSet: function(ctx) {
                //todododododo
            }
        },
        GO: {
            value: 'go',
            validate: function(prevState, ctx) {
                return debug || prevState == ExpertState.ACCEPT;
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
            if (newState.validate(this.state, context)) {
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
    //var connection = new Strophe.Connection('http://expleague.com:5280/http-bind');

    // TODO get API url from config
    var apiUrl = KNUGGET.config.domain + "api";

    if (Notification.permission !== "granted") {
        alert('Пожалуйста, разрешите доступ к уведомлениям');
        Notification.requestPermission(function (permission) {
            if( permission != "granted" ) return false;
            var notify = new Notification("Thanks for letting notify you");
        });
    }

    setUserData = function(login, password) {
        KNUGGET.storage.set("UserData", JSON.stringify({userLogin: login, userPassword: password}));
    };

    cleanAll = function() {
        KNUGGET.storage.set("Board", JSON.stringify([]));
        KNUGGET.storage.set("Requests", JSON.stringify([]));
        KNUGGET.storage.set("VisitedPages", JSON.stringify([]));
        KNUGGET.storage.set("AllowToShow", false);

        //$scope.board.update();
        //$scope.allowToShow.get(function(){});
        //$scope.activeRequest.get(function(){});
    };

    newStyleResponce = function(callback) {
        KNUGGET.storage.get("Board", function (value) {
            result = [];
            value = value ? JSON.parse(value) : [];
            value.forEach(function(el, i) {
                el = JSON.parse(el);
                if (el.Type == 'link') {
                    if (el.Base64Image != undefined) {
                        result.push({
                            image: {
                                image: el.Base64Image,
                                title: el.Title,
                                referer: el.Referer
                            }
                        });
                    } else {
                        result.push({
                            link: {
                                href: el.Href,
                                title: el.Title
                            }
                        });
                    }
                } else if (el.Type == 'text') {
                    result.push({
                        text: {
                            text: el.Text,
                            title: el.Title,
                            referer: el.Referer
                        }
                    });
                } else if (el.Type == 'picture') {
                    result.push({
                        image: {
                            image: el.Image, //todo make base64
                            title: el.Title,
                            referer: el.Referer
                        }
                    });
                }
            });
            callback(result);
        });
    };

    addToBoardSync = function(answer, callback) {
        KNUGGET.storage.get("Board", function (value) {
            value = value ? JSON.parse(value) : [];
            if (typeof answer == 'string') {
                value.push(answer);
            } else {
                value.push(JSON.stringify(answer));
            }
            KNUGGET.storage.set("Board", JSON.stringify(value));
            console.log(JSON.stringify(value));
            callback();
        });
    };

    addQuestion = function(question, callback) {
        KNUGGET.storage.get("Requests", function (value) {
            //value = value ? JSON.parse(value) : [];
            value = [];
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

    setUserAvailable = function(isAvailable) {
        if (isAvailable) {
            jabberClient.sendAvailable();
        } else {
            jabberClient.sendUnavailable();
        }
        KNUGGET.storage.set("UserAvailable", isAvailable);
    };

    //timeLimit = $interval(function () {
    //    KNUGGET.storage.get('ActiveRequest', function (e) {
    //        if (e) {
    //            e.timeleft -= 1;
    //        }
    //    });
    //}, 1000);

    // API METHODS
    return {

        resetBoard: function (data) {
            var defer = $q.defer();
            KNUGGET.storage.set("Board", JSON.stringify([]));
            defer.resolve({status: 200});
            return defer.promise;
        },

        PageVisited: function(data) {
            var defer = $q.defer();
            console.log(JSON.stringify(data));
            jabberClient.send({
                    to: data.request.room,
                    text: JSON.stringify({type: 'visitedPages', count: data.pages.length, links: data.pages})
                }, 'groupchat', function () {
                    defer.resolve({status: 200});
                }
            );
            return defer.promise;
        },

        Available: function(data) {
            var defer = $q.defer();
            setUserAvailable(data.isAvailable);
            defer.resolve({status: 200});
            return defer.promise;
        },

        BanUser: function(data) {
            var defer = $q.defer();
            //todo
            defer.resolve({status: 200});
            return defer.promise;
        },

        ShortCut: function(data) {
            var defer = $q.defer();
            console.log("shortcut");
            defer.resolve({status: 200});
            return defer.promise;
        },

        SendResponse: function(data) {
            var defer = $q.defer();
            newStyleResponce(function(result) {
                jabberClient.send({
                        to: data.request.room,
                        text: JSON.stringify({type: 'response', content : result})
                    }, 'groupchat', function () {
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
            //jabberClient.leaveRoom(data.request.room, 'expert');
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
            var defer = $q.defer();
            removeRequest(data.request, function() {
                defer.resolve({status: 200});
            });
            jabberClient.setState(ExpertState.READY, null);
            return defer.promise;
        },

        Notify: function(data) {
            return ncache.getDefer(data);
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

        ReplaceAnswer: function(data) {
            var defer = $q.defer();
            pos = data.pos;
            answer = data.answer;
            KNUGGET.storage.get("Board", function (value) {
                //console.log("was:");
                //console.log(value);
                arr = value ? JSON.parse(value) : [];
                if (pos >= arr.length || pos < 0) {
                    defer.resolve({status: 500});
                    return;
                }
                arr[pos] = JSON.stringify(answer);
                //console.log("got:");
                //console.log(JSON.stringify(arr));
                KNUGGET.storage.set("Board", JSON.stringify(arr));
                defer.resolve({status: 200});
            });
            //defer.resolve({status: 200});
            return defer.promise;
        },

        InsertAnswer: function(data) {
            var defer = $q.defer();
            pos = data.pos;
            answer = data.answer;
            KNUGGET.storage.get("Board", function (value) {
                //console.log("was:");
                //console.log(value);
                arr = value ? JSON.parse(value) : [];
                if (pos >= arr.length || pos < 0) {
                    defer.resolve({status: 500});
                    return;
                }
                arr.splice(pos, 0, JSON.stringify(answer));
                //console.log("got:");
                //console.log(JSON.stringify(arr));
                KNUGGET.storage.set("Board", JSON.stringify(arr));
                defer.resolve({status: 200});
            });
            //defer.resolve({status: 200});
            return defer.promise;
        },

        addToBoard: function(data) {
            answer = data.answer;
            var defer = $q.defer();
            addToBoardSync(answer, function() {
                defer.resolve({status: 200});
            });
            return defer.promise;
        },

        registerUser: function(data) {
            var future = $q.defer();
            if (jabberClient) {
                jabberClient.logout();
            }
            jabberClient = new JabberClient(data.Username, data.Password, 'expert');
            jabberClient.register(function(result) {
                if (result.registrated) {
                    setUserData(data.Username, data.Password);
                    future.resolve({status:200, msg: ''});
                } else{
                    future.resolve({status:500, msg: result.msg});
                }
            });
            return future.promise;
        },

        loginUser: function(data) {
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
                    future.reject({status : 500, messages : "Wrong password or user name"});
                }
                else if (status == Strophe.Status.CONNECTED) {
                    future.resolve({status : 200});
                    KNUGGET.storage.set("UserActive", {Active: true, Username : data.Username});
                    KNUGGET.storage.set("IsConnected", true);
                    setUserData(data.Username, data.Password);
                    //

                    jabberClient.sendPres();

                } else if (
                    status == Strophe.Status.ERROR ||
                    status == Strophe.Status.CONNFAIL ||
                    status == Strophe.Status.DISCONNECTED ||
                    status == Strophe.Status.DISCONNECTING
                ){
                    future.reject({status : 500, messages : "Connectiont to server failed"});
                    KNUGGET.storage.set("UserActive", {Active: false, Username : data.Username});
                    KNUGGET.storage.set("IsConnected", false);
                    //jabberClient.logout('going to reconnect');
                    //todo reconnect
                }
            });

            jabberClient.addOfferListener(function(id, from, offer, isCanceled) {
                if (!isCanceled) {
                    stateController.setState(ExpertState.CHECK, {to: from, id: id, offer: offer});
                }
            });

            jabberClient.addMessageListener(function(body, msg) {
                if (body.getElementsByTagName('room') && body.getElementsByTagName('room').length > 0) {
                    type = body.getElementsByTagName('room')[0].getAttribute('type');
                    if (type == 'check') {
                        user = Strophe.getText(body);
                        id = body.getElementsByTagName('room')[0].getAttribute('id');
                        stateController.setState(ExpertState.CHECK, {user: user, to: msg.owner, id: id});
                    }

                }
                console.log(msg);
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

        Logout: function(data) {
            var defer = $q.defer();
            reason = data.reason ? data.reason : 'unknown';
            jabberClient.logout(reason);
            KNUGGET.storage.set("UserActive", {Active: false, Username : ''});
            KNUGGET.storage.set("IsConnected", false);

            defer.resolve({status : 200});

            return defer.promise;
        },

        Upload: function(data, senderId) {

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

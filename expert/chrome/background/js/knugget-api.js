angular.module('knuggetApiFactory', []).factory('knuggetApi', ['$http', '$q', '$interval', '$timeout', 'fileBlob', function ($http, $q, $interval, $timeout, $fileBlob) {

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
    var latestOffer;
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

    function SyncMaster() {
        this.queue = [];

        this.progress = function(func, data) {
            if (data.isSync) {
                return;
            }
            if (latestOffer && stateController.state == ExpertState.GO) {
                var sync = $msg({to: latestOffer.room, from: jabberClient.connection.jid, type: 'chat'})
                    .c('sync')
                    .attrs({xmlns: "http://expleague.com/scheme", func: func, data: JSON.stringify(data)});
                //.replace(/"/g, '\'')
                console.log("store: " + func + "\t" + data);
                //jabberClient.unsafeSend(sync, function () {});
            }
        };


        this.sync = function(func, data) {
            data['isSync'] = true;
            this.queue.push({func: api[func], data: data});
        };


        var self = this;
        var lock = false;
        $interval(function() {
            if (!lock && self.queue.length > 0) {
                lock = true;
                var sync = self.queue.shift();
                sync.func(sync.data).then(function() {
                    lock = false;
                });
            }
        }, 100);


    }


    function JabberClient(login, password, resource) {
        this.login = login;
        this.password = password;
        this.resource = resource;
        this.host = login.split('@')[1];

        //this.connection = new Strophe.Connection('http://expleague.com:5280/http-bind');
        //this.connection = new Strophe.Connection('http://' + this.host + ':5280/');
        if (this.host == 'localhost')
            this.connection = new Strophe.Connection('http://' + this.host + ':5280/');
        else
            this.connection = new Strophe.Connection('http://' + this.host + '/http-bind');

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

        this.addResumeListener = function(listener) {
            this.connection.addHandler(function(msg) {
                //don't handle messages, that have something except 'offer' or 'cancel'
                var resume = findByTagName(msg.childNodes, 'resume');
                if (resume) {
                    var offer = findByTagName(resume.childNodes, 'offer');
                    offer = new Offer(offer);
                    listener(msg.getAttribute('id'), msg.getAttribute('from'), offer);
                }
                return true;
            }, null, 'message', null, null, null);
        };

        this.addOfferListener = function(listener) {
            this.connection.addHandler(function(msg) {
                //don't handle messages, that have something except 'offer' or 'cancel'
                var children = msg.childNodes;
                for (var i = 0; i < children.length; i++) {
                    var name = children[i].tagName;
                    if (!(name == 'offer' || name == 'cancel')) {
                        return true;
                    }
                }
                var offer = findByTagName(msg.childNodes, 'offer');
                if (offer) {
                    offer = new Offer(offer);
                      isCanceled = findByTagName(msg.childNodes, 'cancel') != null;
                    listener(msg.getAttribute('id'), msg.getAttribute('from'), offer, isCanceled);
                }
                return true;
            }, null, 'message', null, null, null);
        };

        this.addSyncListener = function(listener) {
            this.connection.addHandler(function(msg) {
                var sync = msg.getElementsByTagName('sync')[0]
                var func = sync.getAttribute('func');
                var data = JSON.parse(sync.getAttribute('data'));
                data['isSync'] = true;
                listener(func, data);
                return true;
            }, null, 'message', 'sync', null, null);
        };

        this.addMessageListener = function(listener) {
            this.connection.addHandler(function(msg) {
                var to = msg.getAttribute('to');
                var from = msg.getAttribute('from');
                var type = msg.getAttribute('type');
                var elems = msg.getElementsByTagName('body');

                if (elems.length > 0) {
                    var body = elems[0];
                    //listener({text: Strophe.getText(body), from: from, time: new Date().getTime()});
                    from = from.split('/');
                    from = from[from.length - 1];
                    listener(Strophe.getText(body), from + '@' + jabberClient.host);
                }
                return true;
            }, null, 'message', 'groupchat', null, null);
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
                    var details = JSON.parse(offer.subj);
                    invite.subj = details.topic;
                    invite.owner = offer.client;
                    invite.room = offer.room.replace("/client", "");
                    invite.time = new Date().getTime();
                    invite.img = null;//'http://3.bp.blogspot.com/_f3d3llNlZKQ/SxrJWGZywvI/AAAAAAAABg0/2rV7MNks1lw/s400/Prova.jpg';
                    invite.confirmExpireTime =  Date.now() + 965 * 1000;
                    invite.resolveExpireTime = Date.now() + 9150 * 1000;
                    invite.map = { center: { latitude: 59.977755, longitude: 30.3343742 }, zoom: 15};
                    invite.offer = offer;
                    listener(invite);
                }
                return true;
            }, null, 'message', null, null, null);
        };

        this.leaveRoom = function(room, nick) {
            //todo this!
            //this.sendPres({to: room + '/' + nick, type: 'unavailable'});
            //this.sendPres({to: jabberClient.host+ '/' + nick, type: 'unavailable'});
            //this.sendPres({to: jabberClient.host+ '/' + nick, type: 'unavailable'});
            this.sendAvailable();
            //var pres = $pres({to: room + '/' + nick, type: 'unavailable'});
            //this.connection.send(pres.tree());
        };

        this.enterRoom = function(room, nick) {
            console.log("entering in room: " + room + " with nick " + nick);
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
            this.connection.flush();
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
            validate: function(prevState, ctx) {
                return true;
            },
            onSet: function() {
                cleanAll();
                jabberClient.sendPres({type: 'available'});
            }
        },
        AWAY: {
            value: 'away',
            validate: function(prevState, ctx) {
             return true;
            },
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
                        //var msg = $msg({to: ctx.to, from: jabberClient.connection.jid, type: 'chat'})
                        var msg = $msg({to: jabberClient.host, from: jabberClient.connection.jid, type: 'chat'})
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
                //stateController.setState(ExpertState.STEADY, presence);
            }
        },
        STEADY: {
            value: 'steady',
            validate: function(prevState, ctx) {
                return debug || prevState == ExpertState.CHECK;
            },
            onSet: function(presence) {
                //jabberClient.sendPres({to: presence.from, type: ExpertState.STEADY.value});
                jabberClient.sendPres({to: jabberClient.host, type: ExpertState.STEADY.value});
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
                latestOffer = invite.offer;
                //todo kvv insert real img url
                var question = {
                    question: invite.subj,
                    owner: invite.owner,
                    time: invite.time,
                    id: invite.time,
                    room: invite.room,
                    img: null,//'http://3.bp.blogspot.com/_f3d3llNlZKQ/SxrJWGZywvI/AAAAAAAABg0/2rV7MNks1lw/s400/Prova.jpg',
                    confirmExpireTime: Date.now() + 965 * 1000,
                    resolveExpireTime: Date.now() + 9150 * 1000,
                    map: { center: { latitude: 59.977755, longitude: 30.3343742 }, zoom: 15},
                    isResumed: invite.isResumed ? true : false
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
                //toda place start here

                var msg = $msg({to: jabberClient.host, from: jabberClient.connection.jid, type: 'chat'})
                .c(data.request.isResumed ? 'resume' : 'start')
                    .attrs({xmlns: "http://expleague.com/scheme"});
                jabberClient.unsafeSend(msg, function(){});

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

    function ChatController() {
        this.msgQueue = [];

        this.addChatMsg = function(msg) {
            console.log("add: " + JSON.stringify(msg));
            this.msgQueue.push({type: 'msg', msg: msg});
        };

        this.clear = function() {
            this.msgQueue = [];
        };

        this.readAllChat = function() {
            this.msgQueue.unshift({type: 'readall'});
        };


        var me = this;
        $interval(function() {
            if (me.msgQueue.length > 0) {
                var cmd = me.msgQueue.shift();
                if (cmd.type == 'msg') {
                    var msg = cmd.msg;
                    KNUGGET.storage.get('ChatLog', function (value) {
                        var log = value ? JSON.parse(value) : {history: [], unread: 0};
                        //for back capability
                        if (Array.isArray(log) || log == null) {
                            log = {history: [], unread: 0};
                        }
                        log.history.push(msg);
                        log.unread += msg.isOwn ? 0 : 1;
                        KNUGGET.storage.set("ChatLog", JSON.stringify(log));
                        if (!msg.isOwn) {
                            var message = new Notification('Новое сообщение', {
                                tag: 'chat',
                                body: msg.text
                            });
                        }
                    });
                } else if (cmd.type == 'readall') {
                    KNUGGET.storage.get('ChatLog', function (value) {
                        var log = value ? JSON.parse(value) : {history: [], unread: 0};
                        //for back capability
                        if (Array.isArray(log) || log == null) {
                            log = {history: [], unread: 0};
                        }
                        log.unread = 0;
                        KNUGGET.storage.set("ChatLog", JSON.stringify(log));
                    });
                } else {
                    console.log('WARNING! UNEXPECTED CMD: ' + cmd);
                }
            }
        }, 50);
    }

    var jabberClient = null;
    var stateController = null;
    var chatController = new ChatController();
    var syncMaster = new SyncMaster();
    var visitedPages = [];

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
        latestOffer = null;
        KNUGGET.storage.set('Board', JSON.stringify([]));
        KNUGGET.storage.set('Requests', JSON.stringify([]));
        KNUGGET.storage.set('AllowToShow', false);
        KNUGGET.storage.set('ChatLog', {history:[], unread: 0});
        chatController.clear();
        visitedPages = [];
        //$scope.board.update();
        //$scope.allowToShow.get(function(){});
        //$scope.activeRequest.get(function(){});
    };

    newStyleResponce = function(callback) {
        $timeout(function() {
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
                    } else if (el.Type == 'picture' || el.Type == 'apicture') {
                        result.push({
                            image: {
                                image: el.Image, //todo make base64
                                title: el.Title,
                                referer: el.Referer
                            }
                        });
                    } else {
                        console.log('WARNING! UNEXPECTED RESPONCE ELEMENT TYPE: ' + el.Type);
                        console.log(el);
                    }
                });
                callback(result);
            });
        }, 100);
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



    //if (window.chrome && window.chrome.storage) {
    //    window.chrome.storage.onChanged.addListener(function (data) {
    //        if (latestOffer && stateController.state == ExpertState.GO) {
    //            for (var key in data) {
    //                var sync = $msg({to: latestOffer.room, from: jabberClient.connection.jid, type: 'chat'})
    //                    .c('sync')
    //                    .attrs({xmlns: "http://expleague.com/scheme", name: key, value: JSON.stringify(data[key].newValue)});
    //                console.log("store: " + key);
    //                jabberClient.unsafeSend(sync, function () {});
    //            }
    //        }
    //    });
    //}

    //timeLimit = $interval(function () {
    //    KNUGGET.storage.get('ActiveRequest', function (e) {
    //        if (e) {
    //            e.timeleft -= 1;
    //        }
    //    });
    //}, 1000);

    // API METHODS
    api = {

            resetBoard: function (data) {
            var defer = $q.defer();
            KNUGGET.storage.set("Board", JSON.stringify([]));
            defer.resolve({status: 200});
            return defer.promise;
        },

        SengMsg: function(data) {
            var defer = $q.defer();
            console.log(JSON.stringify(data));
            jabberClient.send({
                    to: data.request.room,
                    text: JSON.stringify(data.text)
                }, 'groupchat', function () {
                    defer.resolve({status: 200});
                }
            );
            chatController.addChatMsg({isOwn: true, text: data.text});
            return defer.promise;
        },

        PageVisited: function(data) {

            console.log(JSON.stringify(data));
            var tabUrl = data.tabUrl;
            KNUGGET.storage.get('ActiveRequest', function (request) {
                if (request) {
                    var allreadyVisited = false;
                    for (var i = 0; !allreadyVisited && i < visitedPages.length; i++) {
                        if (visitedPages[i] == tabUrl) {
                            allreadyVisited = true;
                        }
                    }
                    if (!allreadyVisited) {
                        visitedPages.push(tabUrl);
                        jabberClient.send({
                                to: request.room,
                                text: JSON.stringify({
                                    type: 'visitedPages',
                                    count: visitedPages.length,
                                    links: visitedPages
                                })
                            }, 'chat', function () {}
                        );

                    }
                }
            });
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
                if (latestOffer) {
                    var msg = $msg({to: jabberClient.host, from: jabberClient.connection.jid, type: 'chat'})
                        .c('done')
                        .attrs({xmlns: "http://expleague.com/scheme"})
                        .up()
                        .cnode(latestOffer.node);
                    jabberClient.unsafeSend(msg, function(){});
                    latestOffer = null;
                }
                stateController.setState(ExpertState.READY);
            });
            return defer.promise;
        },

        Finish: function(data) {
            var defer = $q.defer();
            //connection.send($pres().tree());
            if (data.request && data.request.room) {
                jabberClient.leaveRoom(data.request.room, 'expert');
            }
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
                if (latestOffer) {
                    var msg = $msg({to: jabberClient.host, from: jabberClient.connection.jid, type: 'chat'})
                        .c('cancel')
                        .attrs({xmlns: "http://expleague.com/scheme"})
                        .up()
                        .cnode(latestOffer.node);
                    jabberClient.unsafeSend(msg, function(){});
                    latestOffer = null;
                }
                stateController.setState(ExpertState.READY, null);
                defer.resolve({status: 200});
            });
            return defer.promise;
        },

        Notify: function(data) {
            return ncache.getDefer(data);
        },

        Remove: function(data) {
            syncMaster.progress('Remove', data);
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
            syncMaster.progress('Move', data);
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
            syncMaster.progress('ReplaceAnswer', data);
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
            syncMaster.progress('InsertAnswer', data);
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
            syncMaster.progress('addToBoard', data);
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
                } else {
                    stateController.setState(ExpertState.READY, null);
                }
            });

            jabberClient.addResumeListener(function(id, from, offer) {
                //todo make function offer -> request
                var invite = {};
                var details = JSON.parse(offer.subj);
                invite.subj = details.topic;
                invite.owner = offer.client;
                invite.room = offer.room.replace("/client", "");
                invite.time= new Date().getTime();
                invite.img = null;//'http://3.bp.blogspot.com/_f3d3llNlZKQ/SxrJWGZywvI/AAAAAAAABg0/2rV7MNks1lw/s400/Prova.jpg';
                invite.confirmExpireTime =  Date.now() + 965 * 1000;
                invite.resolveExpireTime = Date.now() + 9150 * 1000;
                invite.map = { center: { latitude: 59.977755, longitude: 30.3343742 }, zoom: 15};
                invite.offer = offer;
                //set resuming prop
                invite.isResumed = true;
                stateController.setState(ExpertState.INVITE, invite);
            });

            //jabberClient.addMessageListener(function(body, msg) {
            //    if (body.getElementsByTagName('room') && body.getElementsByTagName('room').length > 0) {
            //        type = body.getElementsByTagName('room')[0].getAttribute('type');
            //        if (type == 'check') {
            //            user = Strophe.getText(body);
            //            id = body.getElementsByTagName('room')[0].getAttribute('id');
            //            stateController.setState(ExpertState.CHECK, {user: user, to: msg.owner, id: id});
            //        }
            //
            //    }
            //    console.log(msg);
            //});

            jabberClient.addMessageListener(function(text, from) {

                chatController.addChatMsg({isOwn: latestOffer ? from != latestOffer.client : false, text: text});
            });

            jabberClient.addSyncListener(function(func, data) {
                console.log('call sync ' + func + '\t\t' + data);
                syncMaster.sync(func, data);

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

        ReadAllChat: function(data) {
            var defer = $q.defer();
            chatController.readAllChat();
            defer.resolve({status : 200});

            return defer.promise;
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

    return api;
}]);

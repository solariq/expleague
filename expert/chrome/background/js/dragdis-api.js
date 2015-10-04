angular.module('dragdisApiFactory', []).factory('dragdisApi', ['$http', '$q', 'fileBlob', function ($http, $q, $fileBlob) {

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


        this.addInviteListener = function(listener) {
            this.connection.addHandler(function(msg) {
                alert('addInviteListener');
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
                    //listener({room: room, from: fromroom: room, from: from, subj: 'Здесь мог бы быть Ваш вопрос!', time: new Date().getTime()});
                }
                return true;
            }, null, 'message', null, null, null);
        };

        this.leaveRoom = function(room, nick) {
            var pres = $pres({to: room + '/' + nick, type: 'unavailable'});
            this.connection.send(pres.tree());
        };

        this.enterRoom = function(room, nick) {
            var pres = $pres({to: room + '/' + nick})
                .c('priority')
                .t(5);
            this.connection.send(pres.tree());
            //callback();
        };

        this.sendPres = function() {
            this.connection.send($pres().tree());
        }
    }


    var jabberClient = null;

    //var connection = new Strophe.Connection('http://localhost:5280/http-bind');
    //var connection = new Strophe.Connection('http://toobusytosearch.net:5280/http-bind');

    // TODO get API url from config
    var apiUrl = DRAGDIS.config.domain + "api";

    if (Notification.permission !== "granted") {
        alert('Пожалуйста, разрешите доступ к уведомлениям');
        Notification.requestPermission(function (permission) {
            if( permission != "granted" ) return false;
            var notify = new Notification("Thanks for letting notify you");
        });
    }

    addToBoardSync = function (answer, callback) {
        DRAGDIS.storage.get("Board", function (value) {
            value = value ? JSON.parse(value) : [];
            value.push(answer);
            DRAGDIS.storage.set("Board", JSON.stringify(value));
            callback();
        });
    };

    addQuestion = function(question, callback) {
        DRAGDIS.storage.get("Requests", function (value) {
            value = value ? JSON.parse(value) : [];
            //value = [];
            value.push(question);
            DRAGDIS.storage.set("Requests", JSON.stringify(value));
            callback();
        });
    };

    removeRequest = function(request, callback) {
        DRAGDIS.storage.get("Requests", function (value) {
            value = value ? JSON.parse(value) : [];
            result = [];
            value.forEach(function(el, i) {
                if (request.id != el.id)
                    result.push(el);
            });
            DRAGDIS.storage.set("Requests", JSON.stringify(result));
            callback();
        });
    };

    // API METHODS
    return {

        resetBoard: function (data) {
            var defer = $q.defer();
            DRAGDIS.storage.set("Board", JSON.stringify([]));
            defer.resolve({status: 200});
            return defer.promise;
        },

        SendResponse: function(data) {
            var defer = $q.defer();
            DRAGDIS.storage.get("Board", function (board) {
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
            jabberClient.enterRoom(data.request.room, 'expert');
            //connection.send($pres().tree());
            defer.resolve({status: 200});
            return defer.promise;
        },

        Reject: function(data) {
            alert('rejecting');
            var defer = $q.defer();
            removeRequest(data.request, function() {
                defer.resolve({status: 200});
            });
            jabberClient.leaveRoom(data.request.room, 'expert');
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
                    alert('close11');
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
            DRAGDIS.storage.get("Board", function (value) {
                value = value ? JSON.parse(value) : [];
                if (value.length <= data.index) {
                    defer.resolve({status: 500});
                    return;
                }

                value.splice(data.index, 1);
                DRAGDIS.storage.set("Board", JSON.stringify(value));
                defer.resolve({status: 200});
            });
            return defer.promise;
        },

        Move: function(data) {
            var defer = $q.defer();
            fromPos = data.fromPos;
            toPos = data.toPos;
            //alert(fromPos + ' -> ' + toPos);
            DRAGDIS.storage.get("Board", function (value) {
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
                DRAGDIS.storage.set("Board", JSON.stringify(arr));
                defer.resolve({status: 200});
            });
            //defer.resolve({status: 200});
            return defer.promise;
        },

        addToBoard: function (data) {
            //DRAGDIS.storage.set("Board", JSON.stringify([]));
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
            jabberClient.loginUser('expert', function(status) {
                if (status == Strophe.Status.CONNECTING) {
                }
                else if (status == Strophe.Status.AUTHFAIL) {
                    alert('Strophe failed to auth');
                    future.reject({status : 500, messages : "Wrong password or user name"});
                }
                else if (status == Strophe.Status.CONNECTED) {
                    future.resolve({status : 200});
                    DRAGDIS.storage.set("UserActive", {Active: true, Username : data.Username});
                    DRAGDIS.storage.set("IsConnected", true);
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
                    DRAGDIS.storage.set("UserActive", {Active: false, Username : data.Username});
                    DRAGDIS.storage.set("IsConnected", false);
                    //jabberClient.logout('going to reconnect');
                    //todo reconnect
                }
            });

            jabberClient.addMessageListener(function(msg) {
                alert('addMessageListener: ' + msg.text);
            });

            jabberClient.addInviteListener(function(invite) {
                //question = {question: invite.subj, owner: invite.from, time: invite.time, id: invite.time, room: invite.from};
                question = {question: invite.subj, owner: invite.from, time: invite.time, id: invite.time, room: invite.room};
                addQuestion(question, function(){});
            });

            return future.promise;
        },

        Logout: function (data) {
            var defer = $q.defer();
            reason = data.reason ? data.reason : 'unknown';
            jabberClient.logout(reason);
            DRAGDIS.storage.set("UserActive", {Active: false, Username : ''});
            DRAGDIS.storage.set("IsConnected", false);

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

        UpdateLastUpload: function (data) {
            return $http.post(apiUrl + '/item/update', data);
        },

        DeleteLastUpload: function (lastItem) {
            return $http.post(apiUrl + '/item/delete', { Id: lastItem.ID });
        },

        GetLastItemUrl: function (folder) {
            return $http.post(apiUrl + '/item/getitemurl?itemid=' + folder.lastUploadID, { itemId: folder.lastUploadID });
        },

        // MANUALLY UPDATE FOLDERS IN LOCAL STORAGE
        FolderList: function (params) {

            return $http.get(apiUrl + '/folder/list?hideSpecial=true&version=2.1').
            
            success(function (response, status) {
                DRAGDIS.storage.set("CurrentSender", 'forceUpdate');
                DRAGDIS.storage.set("FoldersList", response);
            });
        },

        // SEND SORTED FOLDER TO SERVER
        FolderSort: function (data, senderId) {
            if (data.parent == 0) {
                data.parent = null;
            }
            return $http.post(apiUrl + '/folder/sort', {
                Id: data.id,
                OrderIndex: data.order,
                Parent: data.parent,
                Sender: senderId
            });
        },

        // ADD NEW FOLDER 
        FolderCreate: function (data, senderId) {
            return $http.post(apiUrl + '/folder/add', data);
        },

        // ADD NEW GROUP 
        GroupCreate: function (data, senderId) {

            //Modify item fields to ensure it is saved correctly
            data.Parent = null;
            data.Type = 0;
            data.Sender = senderId;

            return $http.post(apiUrl + '/folder/add', data);
        },

        FolderConvert: function (data, senderId) {

            //Modify item fields to ensure it is saved correctly
            data.Sender = senderId;

            return $http.post(apiUrl + '/folder/convert', data);
        },

        FolderUpdate: function (data, senderId) {

            //Modify item fields to ensure it is saved correctly
            data.Sender = senderId;
            data.Status = (data.Status) ? 1 : 0;

            return $http.post(apiUrl + '/folder/update', data);
        },

        FolderDelete: function (data, senderId) {

            //Modify item fields to ensure it is saved correctly
            data.Sender = senderId;
            data.Status = (data.Status) ? 1 : 0;

            return $http.post(apiUrl + '/folder/delete', data);
        },

        FolderShare: function (data, senderId) {
            return $http.post(apiUrl + "/folder/share", {
                AppId: data.appId,
                FolderId: data.folderId,
                Message: data.msg
            });
        },

        FolderGetSharingLinks: function (data, senderId) {
            return $http.post(apiUrl + "/folder/GetFolderUrls", null, {
                params: {
                    folderId: data.ID
                }
            });
        },

        FolderGetCollaborationLink: function (data, senderId) {
            return $http.post(apiUrl + "/folder/ChangeFolderUrl", {
                FolderId: data.ID,
                Collaboration: true
            });
        },

        FolderDestroyCollaborationLink: function (data, senderId) {
            return $http.post(apiUrl + "/folder/ChangeFolderUrl", {
                FolderId: data.ID,
                Collaboration: true,
                Remove: true
            });
        },

        FolderRevokeCollaboratorAccess: function (data, senderId) {
            return $http.post(apiUrl + "/folder/RevokeFolderAccess", {
                FolderId: data.ID,
                Collaboration: true
            });
        },

        // ADD NEW APP 
        AppCreate: function (data, senderId) {

            return $http.post(apiUrl + '/app/add', {
                Name: data.name,
                Config: data.config,
                Type: 0,
                Sender: senderId
            });
        },

        // CHANGE GROUP STATUS
        GroupChangeStatus: function (data, senderId) {
            return $http.post(apiUrl + '/folder/ChangeStatus', {
                Id: data.id,
                Type: data.type,
                Status: data.status,
                Sender: senderId
            });
        },

        // LOAD APPLICATION CONFIG TEMPLATE
        AppConfigTemplate: function (data) {
            return $http.get(DRAGDIS.config.domain + "LinkedServices/Settings?serviceTypeId=" + data.serviceProvider + "&linkedServiceId=" + data.serviceId);
        },

        // LOAD APPLICATION CONFIG TEMPLATE
        registerNewUser: function (data) {
            return $http.post(DRAGDIS.config.domain + '/account/register', data);
        },

        Tracker: function (data) {
            return $http.post(apiUrl + "/main/statistics", data);
        },


        /*=====================================
        =            User settings            =
        =====================================*/

        getUserSettings: function () {
            return $http.get(apiUrl + '/main/userSettings');
        },

        setUserSettings: function (data) {

            var settings = {};
            var deferred = $q.defer();

            DRAGDIS.storage.get("userSettings", function (userSettings) {

                settings = angular.extend(userSettings, data.params);

                DRAGDIS.storage.set("userSettings", settings);

                $http.post(apiUrl + '/main/userSettings', settings, {
                    params: {
                        name: data.namespace
                    }
                }).then(function (response) {
                    deferred.resolve(response);
                });
            });

            return deferred.promise;

        },


        /*=====================================
        =            Notifications            =
        =====================================*/
        
        getUserNotifications: function() {
            return $http.get(apiUrl + '/main/userNotifications');
        },

        deleteNotification: function(data) {
            
            DRAGDIS.storage.get("userNotifications", function (userNotifications) {

                var unshownNotifications = $.grep(userNotifications, function (notification) { 
                    return notification.Id !== data.Id; 
                });

                DRAGDIS.storage.set("userNotifications", unshownNotifications);

            });

            return $http.delete(apiUrl + '/main/userNotification', {
                params: {
                    id: data.Id
                }
            });
        }


    };
}]);

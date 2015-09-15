angular.module('dragdisApiFactory', []).factory('dragdisApi', ['$http', '$q', 'fileBlob', function ($http, $q, $fileBlob) {

    // TODO get API url from config
    var apiUrl = DRAGDIS.config.domain + "api";

    // API METHODS
    return {

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

        loginUser: function (data) {
            return $http.post(DRAGDIS.config.domain + '/account/MiniLoginExtension', data);
        },

        Logout: function () {
            return $http.get(DRAGDIS.config.domain + '/account/logout', {});
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
       =    Foldes limiting and payment   =
       =====================================*/
        getUserInfo: function () {
            return $http.get(apiUrl + '/main/userinfo');
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

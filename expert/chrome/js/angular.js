dragdisSidebar.controller('DRAGDIS_SIDEBAR_CTRL', ['$window', '$scope', '$controller', '$timeout', '$rootScope', 'dataService', 'dialogService', function ($window, $scope, $controller, $timeout, $rootScope, dataService, dialogService) {

    $scope.domain = DRAGDIS.config.domain;

    $scope.dialogIsActive = false;
    $scope.renderComplete = false;

    //default auth form
    $scope.isLoginForm = true;

    $scope.menu = {
        active: false,
        openMoreFoldersDialog: function () {
            $scope.hide(true);
            dialogService.template = DRAGDIS.config.sidebarTemplatesRoot + "dialog.moreFolders";
        }
    };
    $scope.active = false;
    $scope.connectionFail = false;
    $scope.appMode = 0;
    $scope.timeoutShow = 0;
    $scope.timeoutHide = 0;
    $scope.uploadError = 0;
    $scope.expandedView = 0;

    $scope.msg = {
        default: "",
        loading: "Loading folders...",
        noFolders: "You don't have folders. Create one by clicking 'Add +'",
        timeout: "Cannot connect to server. Please try again later."
    };

    if (DRAGDIS.config.isExtension) {
        $scope.$on('$locationChangeStart', function (ev) {
            ev.preventDefault();
        });
    }

    $scope.sidebarTemplate = function () {

        var templatesRoot = DRAGDIS.config.sidebarTemplatesRoot;

        //If uppload error is flagged show error-box instead of sidebar
        if ($scope.uploadError)
            return templatesRoot + "uploadError";

        //If connection error is flagged, display Oops... message
        if ($scope.connectionFail)
            return templatesRoot + "error";

        //If user is connected, return sidebar template, else load login template
        var template = ($scope.isConnected === true) ? templatesRoot + "sidebar" : $scope.isLoginForm ? templatesRoot + "login" : templatesRoot + "registration";

        $scope.oldTime = $scope.newTime;
        $scope.newTime = new Date().getTime();

        //console.log(template + " " + ($scope.newTime - $scope.oldTime));

        return template;
    };

    // STORAGE LISTENERS
    if (window.chrome && window.chrome.storage) {
        window.chrome.storage.onChanged.addListener(function (data) {

            //console.log("storage updated", data);

            if (data.FoldersList) {
                DRAGDIS.storage.get("CurrentSender", function (currentSender) {
                    // Skip folder updates if sender is the same tab
                    if (currentSender == DRAGDIS.browserInstanceId) {
                        return;
                    } else {
                        $scope.folders.update(data.FoldersList.newValue);
                    }
                });

            } else if (data.UserActive) {
                //console.log("UserActive", data.UserActive.newValue);
                //console.log("storage UserActive");
                $scope.user.update();
            } else if (data.ConnectionFail) {
                //console.log("storage ConnectionFail");
                $scope.user.update();
            } else if (data.IsConnected) {

                $scope.isConnected = data.IsConnected.newValue;

                if ($scope.isConnected) {
                    //console.log("storage isConnected");
                    $scope.user.update();
                    $scope.folders.update();
                } else {
                    if (!$scope.$$phase) {
                        $scope.$digest();
                    }
                }

            } else if (data.IsReconnecting) {
                $scope.isReconnecting = data.IsReconnecting.newValue;

                if (!$scope.$$phase) {
                    $scope.$digest();
                }
            }
        });
    }

    // DATA OBJECTS
    $scope.folders = {
        list: undefined,
        update: function (foldersList) {
            if (foldersList && foldersList.length) {
                this.goToProcessResults(foldersList);
            } else {
                this.updateFromStorage();
            }
        },
        updateFromStorage: function () {
            DRAGDIS.storage.get("FoldersList", function (foldersList) {
                $scope.folders.goToProcessResults(foldersList);
            });
        },
        goToProcessResults: function (foldersList) {
            //console.log("goToProcessResults");

            if (!foldersList || !foldersList.length) {
                $scope.msg.default = foldersList.timeout ? $scope.msg.timeout : $scope.msg.noFolders;
            } else {
                $scope.msg.default = $scope.msg.loading;
            }

            $scope.folders.list = foldersList;

            this.getUserInfo();

        },
        getByID: function (folderId) {
            for (var i = 0; i < this.list.length; i++) {
                //Return group if it's ID matches
                if (this.list[i].ID == folderId) return this.list[i];

                //Search for folder ID in group
                if (this.list[i].Childs) {
                    var folder = $.grep(this.list[i].Childs, function (e) {
                        return e.ID == folderId;
                    })[0];

                    if (folder) {
                        return folder;
                    }
                }
            };
            //If nothing found, return false
            return false;
        },
        getUserInfo: function () {
            DRAGDIS.api("getUserInfo", {}).then(function (response) {

                dataService.updateReferralSystemValues(response);

                $scope.folders.usedFoldersCount = parseInt(response.UsedFoldersCount);
                $scope.folders.freeFoldersCount = parseInt(response.FreeFoldersCount);

                $scope.folders.subscriptionActive = response.SubscriptionActive;
                $scope.folders.subscriptionSoonToExpire = response.SubscriptionSoonToExpire;

                dataService.updateFoldersCounters();

                if (!$scope.$$phase) {
                    $scope.$apply();
                }

            }).fail(function (error) {
                new TrackException("Failed to get userInfo on getUserInfo").send();
                console.error(error);
            });
        },
        calculateFolders: function () {
            var count = 0;

            for (var key in this.list) {
                var folder = this.list[key];

                //parent
                count = count + 1;

                //childs
                if (folder.Childs && folder.Childs.length > 0) {
                    count = count + folder.Childs.length;
                }
            }

            console.log("total folders:", count);
            return count;
        },

        usedFoldersCount: 1,
        freeFoldersCount: 1,
        totalAvailableFolders: 4,
        subscriptionActive: false,
        subscriptionSoonToExpire: false,
        barWidthForMenu: 0,
    };

    $scope.user = {
        username: "",
        avatar: "",
        active: 0,
        apps: {},
        update: function () {

            var currentUser = this;
            //var laikas = new Date().getTime();

            DRAGDIS.storage.get(["UserActive", "IsConnected", "ConnectionFail"], function (values) {

                //console.log("storage", new Date().getTime() - laikas);

                if (values.hasOwnProperty("UserActive")) {
                    currentUser.active = values.UserActive.Active;
                    currentUser.username = values.UserActive.Username;

                    if (values.UserActive.Avatar) {
                        currentUser.avatar = values.UserActive.Avatar;
                    } else {
                        currentUser.avatar = DRAGDIS.extensionFileUrl("images/default_avatar.gif");
                    }
                }

                if (values.hasOwnProperty("IsConnected")) {
                    $scope.user.active = values.isConnected;
                }

                if (values.hasOwnProperty("ConnectionFail")) {
                    $scope.connectionFail = values.ConnectionFail;
                }

                if (!$scope.$$phase) {
                    //console.log("multi storage digest");
                    $scope.$digest();
                }
            });
        }
    };

    $scope.closeSidebar = function () {
        DRAGDIS_SIDEBAR.openedByIcon = false;
        $scope.hide(true, true);
    };

    $scope.show = function (needReset, isOpenedManually) {

        //console.log("SHOW", needReset + " " + isOpenedManually);

        $timeout.cancel($scope.timeoutHide);
        $timeout.cancel($scope.timeoutShow);

        DRAGDIS.storage.get("IsConnected", function (value) {
            if (!value) {
                $scope.user.active = 0;

                if (!$scope.$$phase) {
                    $scope.$apply();
                }
            }
        });

        $scope.timeoutShow = $timeout(function () {

            //var timeStart = new Date().getTime();

            $timeout.cancel($scope.timeoutHide);

            if (isOpenedManually) {
                new TrackEvent("Sidebar", "Sidebar opened manually").send();
            } else {
                new TrackEvent("Sidebar", "Sidebar opened").send();
            }


            if (DRAGDIS.config.firstTimeLoad) {
                $timeout(function () {

                    //var lastTime = new Date().getTime();
                    //console.log("LAIKAS" + (lastTime - timeStart));

                    $scope.active = true;

                    delete DRAGDIS.config.firstTimeLoad;

                    //if (!$scope.$$phase) {
                    //    $scope.$apply();
                    //}
                });
            } else {
                $scope.active = true;
            }

            if (needReset) {
                $scope.resetSidebar();
            }

            if (DRAGDIS_SIDEBAR.dragActive) {
                $scope.dragStart();
            }

            if (!$scope.$$phase) {
                $scope.$apply();
            }

        }, isOpenedManually ? 0 : DRAGDIS.config.timing.dragDelay);
    };

    $scope.hide = function (closeFast, isClosedManually) {

        $timeout.cancel($scope.timeoutShow);
        $timeout.cancel($scope.timeoutHide);

        if (DRAGDIS_SIDEBAR.openedByIcon || $scope.appMode) {
            return false;
        }

        //If user expanded sidebar panel, while dragging, ignore Hide event 
        if (!DRAGDIS_SIDEBAR.openedByIcon && $scope.expandedView) {
            return false;
        }

        $scope.timeoutHide = $timeout(function () {
            if ($scope.appMode) {
                return;
            }

            // Check if mouse is on sidebar. If true, register eve;nt to close sidebar after mouse leave
            if (!closeFast && DRAGDIS.mouseIsOnSidebar) {
                $("#" + DRAGDIS_SIDEBAR_NAME).one("mouseleave", function () {
                    $scope.hide();
                });
                return;
            }

            $timeout.cancel($scope.timeoutShow);

            if (isClosedManually) {
                new TrackEvent("Sidebar", "Sidebar closed manually").send();
            } else {
                new TrackEvent("Sidebar", "Sidebar closed").send();
            }

            DRAGDIS.sendMessage({
                Type: "SendTrackData"
            });

            DRAGDIS_SIDEBAR.dragActive = false;
            $scope.active = false;
            $scope.expandedView = false;

            $scope.resetSidebar();

            if (!$scope.$$phase) {
                $scope.$apply();
            }

        }, closeFast ? 0 : DRAGDIS.config.timing.sidebarClose);
    };

    $scope.resetSidebar = function () {
        $scope.menu.active = false;

        $scope.folderIDwithActiveActions = null;

        $scope.$broadcast("resetSidebar");

        if (!$scope.$$phase) {
            $scope.$apply();
        }
    };

    $scope.dragStart = function () {
        $scope.$broadcast("dragStart");
    };

    $scope.dragEnd = function () {
        $scope.$broadcast("dragEnd");
    };

    $scope.getCollection = function (callback) {

        var dragElement = DRAGDIS.Drag.Target;

        dragElement.dragdisCollection(function (data) {
            DRAGDIS.Drag.Data = data;
            callback();
        });

    };

    $scope.upload = function (folderId, callback) {

        this.getCollection(function () {

            DRAGDIS.Drag.Data.FolderId = folderId;
            DRAGDIS.Drag.Data.Status = 1;

            DRAGDIS.api("Upload", DRAGDIS.Drag.Data, function (response) {
                if (typeof (callback) == "function") {
                    callback(response);
                }
            });
        });

    };

    $scope.hideUploadError = function () {

        //Hide sidebar if it was not force opened
        if (!DRAGDIS_SIDEBAR.openedByIcon) {
            $scope.active = false;
            $scope.resetSidebar();
        };

        $scope.uploadError = false;
    };

    $scope.retryItemUpload = function () {

        if (!$scope.retryingUpload) {

            //Set retryingUpload flag to show loader and prevent rapid requests 
            $scope.retryingUpload = true;

            DRAGDIS.api("retryItemUpload", {}, function (response) {

                //If reupload succeeded, display success message and close error block after some time
                if (response.status == 200) {
                    $scope.reuploadSuccessfull = true;
                    $scope.retryingUpload = false;

                    if (!$scope.$$phase) {
                        $scope.$apply();
                    }

                    $timeout(function () {
                        $scope.reuploadSuccessfull = 0;
                        $scope.hideUploadError();
                    }, 1500);

                    //Else display loader for 1s.
                } else {
                    $timeout(function () {
                        $scope.retryingUpload = false;

                        if (!$scope.$$phase) {
                            $scope.$apply();
                        }
                    }, 1000);
                }
            });
        }
    };

    $scope.reconnect = function () {

        DRAGDIS.sendMessage({
            Type: "RECONNECT"
        }, function () { });

        $scope.isReconnecting = true;

        $timeout(function () {
            $scope.isReconnecting = false;
        }, 1500);
    };


    $scope.logout = function ($event) {

        $event.preventDefault();
        $scope.resetSidebar();

        new TrackPageView("Sidebar", "User logged out").send();

        DRAGDIS.api("Logout", {}, function () { });
    };

    $scope.init = function () {

        DRAGDIS.sidebarController = $scope;

        DRAGDIS.storage.get("IsConnected", function (userConnected) {
            if (userConnected) {
                $scope.folders.update();
                $scope.user.update();
            }

            $scope.isConnected = userConnected ? true : false;
        });

        //Instantly show sidebar if it is not set to initialize in hidden mode
        if (!DRAGDIS.config.initHidden) {
            if (DRAGDIS.config.firstTimeLoad) {
                $scope.show(null, true);
            } else {
                $scope.show(null, DRAGDIS.config.isInitializedManually);
            }
        }
    };

    $scope.init();
}]);

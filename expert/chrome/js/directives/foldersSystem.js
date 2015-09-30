dragdisSidebarDirectives.directive('folder', ['folderFactory', 'dataService', '$window', '$rootScope', '$timeout', '$sce', function (folderFactory, dataService, $window, $rootScope, $timeout, $sce) {

    var folderLayout = DRAGDIS.config.sidebarTemplatesRoot + 'sidebar.folder_@';

    return {
        restrict: "A",
        link: function (scope, element) {
            //console.log("FOLDER");

            // Reload scrollbar after last list element rendered 
            if ((scope.$parent.$last && scope.$last) || (!scope.$parent.$last && scope.$last && scope.folder.Parent == 0 && !scope.folder.Childs.length) || scope.folder.isNewFolder) {
                $timeout(function () {
                    dataService.updateScroll();
                }, 100);
            }


            element.mouseenter(function () {
                scope.folder.loadControls = true;

                $(this).off("mouseenter");

                if (!scope.$$phase) {
                    scope.$digest();
                }
            });
        },
        controller: ['$scope', function ($scope) {

            //indicate new subfolder
            if ($scope.folder.isNewFolder) {
                $timeout(function () {
                    $scope.folder.isNewFolder = false;
                }, 2000);
            }

            //default values:
            $scope.folder.dragStatus = 0; //0=default; 1=uploading, 2=dropSuccedd, 3=movedSucced
            $scope.folderTemplate = folderLayout.replace("@", $scope.folder.Type);
            $scope.folderUrl = $sce.trustAsResourceUrl(DRAGDIS.config.domain + '#/folder/' + $scope.folder.ID);

            this.folder = $scope.folder;

            $scope.openFolder = function () {
                alert('click on folder!')
                //
                //new TrackEvent("Sidebar", "Folder opened", DRAGDIS.serviceEnum($scope.folder.ServiceTypeId)).send();
                //
                //var url = DRAGDIS.config.domain + '#/folder/' + $scope.folder.ID;
                //DRAGDIS.sidebarController.hide(true);
                //$window.open(url);
            };


            $scope.toggleActionsBlock = function () {

                //console.log("toggleActionsBlock");

                //set folderID who have active ActionsBlock

                if ($scope.folder.showActionsBlock) {
                    $rootScope.folderIDwithActiveActions = null;
                    $scope.folder.actionPanelTemplate = null;
                } else {
                    $rootScope.folderIDwithActiveActions = $scope.folder.ID;
                }

                $scope.folder.showActionsBlock = !$scope.folder.showActionsBlock;

                $scope.removeDropElement();

                dataService.updateScroll();

                if (!$scope.$$phase) {
                    $scope.$digest();
                }
            };




            //Highlight active folder when it is opened 
            $scope.isActive = function () { };

            //update|reset folder data
            $scope.updateFolderValues = function (folder) {
                $scope.folder.ID = folder.ID;
                $scope.folder.Name = folder.Name;
                $scope.folder.Icon = folder.Icon;
                $scope.folder.Order = folder.Order;

                $scope.folder.isFake = folder.isFake;

                if (!$scope.$$phase) {
                    $scope.$digest();
                }
            };
            this.updateFolderValues = $scope.updateFolderValues;

            $scope.deleteFolder = function () {
                $scope.folder.actionPanelTemplate = null;
                $scope.folder.showActionsBlock = false;

                $timeout(function () {
                    var parent = $scope.folders.getByID($scope.folder.Parent);
                    var index;

                    if (parent !== false) {
                        index = parent.Childs.indexOf($scope.folder);

                        if (index != -1) {
                            parent.Childs.splice(index, 1);
                        }

                        if (parent.Childs.length == 0) {
                            parent.Status = 0;
                        }

                    } else {
                        index = $scope.folders.list.indexOf($scope.folder);

                        if (index != -1) {
                            $scope.folders.list.splice(index, 1);
                        }
                    }

                    dataService.updateScroll();
                }, 300);

                if (!$scope.$$phase) {
                    $scope.$digest();
                }
            };
            this.deleteFolder = $scope.deleteFolder;

            if ($scope.folder.Type === 0) {

                $scope.expandChilds = function () {

                    $scope.folder.showActionsBlock = false;
                    $scope.folder.actionPanelTemplate = null;

                    $scope.folder.Status = !$scope.folder.Status;

                    $scope.$parent.$broadcast("resetAnotherFolders", { folderId: $scope.folder.ID });
                };

                $scope.oldFolderStatus = $scope.folder.Status ? 1 : 0;

                $scope.updateGroupStatus = function (id, newVal) {
                    DRAGDIS.api("GroupChangeStatus", {
                        id: id,
                        type: 0,
                        status: newVal ? 1 : 0
                    }, function () { });
                };

                $scope.$watch('folder.Status', function (newVal, oldVal) {
                    if (newVal !== oldVal) {

                        new TrackEvent("Sidebar", "Group status changed").send();

                        // Push update of group status to server after 1s
                        $timeout.cancel($scope.pushGroupStatus);
                        $scope.pushGroupStatus = $timeout(function () {

                            var currentFolderStatus = $scope.folder.Status ? 1 : 0;

                            if ($scope.oldFolderStatus !== currentFolderStatus) {
                                var id = $scope.folder.ID;
                                $scope.oldFolderStatus = newVal;

                                $scope.updateGroupStatus(id, newVal);
                            }
                        }, 1000);
                    }
                });
            }

            $scope.removeDropElement = function () {
                //remove drop element
                if ($rootScope.activeFolderWithDrop) {
                    var folderWithDropElement = $scope.folders.getByID($rootScope.activeFolderWithDrop);

                    if (folderWithDropElement) {
                        folderWithDropElement.dragStatus = 0;
                        folderWithDropElement.actionPanelTemplate = false;

                        $rootScope.activeFolderWithDrop = null;
                    }
                }
            };

            $scope.init = function () {
                $scope.isActive();
            };

            var factory = new folderFactory($scope);
            angular.extend($scope, factory);

            $scope.$on("resetSidebar", function () {

                //reset folder
                $scope.folder.showActionsBlock = false;
                $scope.folder.actionPanelTemplate = null;
                //$rootScope.folderIDwithActiveActions <-- reseted one time on angular.js

                $scope.folder.dragStatus = 0;
                $scope.folder.lastUploadID = null;
                $scope.folder.lastUploadUrl = null;

                if (($scope.$parent.$last && $scope.$last) || (!$scope.$parent.$last && $scope.$last && $scope.folder.Parent == 0 && !$scope.folder.Childs.length)) {
                    dataService.updateScroll();
                }

                if (!$scope.$$phase) {
                    $scope.$digest();
                }
            });

            $scope.$on("resetAnotherFolders", function (e, data) {

                $rootScope.activeFolderWithDrop = null;

                if (data.folderId && data.folderId !== $scope.folder.ID) {
                    $scope.folder.actionPanelTemplate = null;
                    $scope.folder.showActionsBlock = false;
                }

                $scope.folder.dragStatus = 0;

                $scope.folder.lastUploadID = null;
                $scope.folder.lastUploadUrl = null;

                if (($scope.$parent.$last && $scope.$last) || (!$scope.$parent.$last && $scope.$last && $scope.folder.Parent == 0 && !$scope.folder.Childs.length)) {
                    dataService.updateScroll();
                }
            });

            $scope.init();


        }]
    };
}]);

dragdisSidebarDirectives.directive("actionsBlock", ['actionBlockFactory', 'dialogService', 'dataService', '$rootScope', '$timeout', function (actionBlockFactory, dialogService, dataService, $rootScope, $timeout) {
    return {
        restrict: "E",
        replace: true,
        require: "^folder",
        template: '<div class="actions-block" ng-class="{activePanel: folder.actionPanelTemplate}" ng-include="actionsBlockTemplate()"></div>',
        link: function (scope, element, attrs, folderCtrl) {
            scope.folderCtrl = folderCtrl;
            scope.folder = folderCtrl.folder;
        },
        controller: ['$scope', function ($scope) {

            $scope.closeBlock = function () {
                $scope.toggleActionsBlock();
            };
            this.closeBlock = $scope.closeBlock;

            this.closeActionPanel = function () {
                $scope.folder.actionPanelTemplate = null;
            };

            $scope.actionsBlockTemplate = function () {
                if ($scope.folder.actionPanelTemplate || ($scope.folder.ID === $rootScope.folderIDwithActiveActions && $scope.folder.showActionsBlock)) {
                    return DRAGDIS.config.sidebarTemplatesRoot + "actionsBlock";
                } else {
                    $scope.folder.showActionsBlock = false;
                    $scope.folder.actionPanelTemplate = null;

                    return null;
                }
            };

            $scope.activateNewFolderPanel = function () {
                if (dataService.getReferralSystemValue("TotalAvailableFolders") > 0 || dataService.getReferralSystemValue("SubscriptionActive")) {
                    $scope.folder.newChild = true;
                    $scope.folder.actionPanelTemplate = DRAGDIS.config.sidebarTemplatesRoot + "panel_folder";

                    $timeout(function () {
                        dataService.updateScroll();
                    }, 300);
                } else {
                    dialogService.template = DRAGDIS.config.sidebarTemplatesRoot + "dialog.moreFolders";
                }
            };
            $scope.activateFolderPanel = function () {
                $scope.folder.newChild = false;
                $scope.folder.actionPanelTemplate = DRAGDIS.config.sidebarTemplatesRoot + "panel_folder";

                $timeout(function () {
                    dataService.updateScroll();
                }, 300);
            };
            $scope.activateCollaborationPanel = function () {
                dialogService.folder = $scope.folder;
                dialogService.template = DRAGDIS.config.sidebarTemplatesRoot + "dialog.collaborate";
            };

            $scope.copyShortUrl = function () {
                if ($scope.folder.ID > 0) {
                    //Get folder sharing links
                    DRAGDIS.api("FolderGetSharingLinks", $scope.folder).then(function (response) {

                        //console.log("FolderGetSharingLinks", response);
                        //$scope.folder.shareFolderLink = DRAGDIS.config.shortUrlDomain + response.ShareUrl;
                        //$scope.folder.collaboratorsCount = response.collaboratorsCount;

                        //if (response.CollaborateUrl) {
                        //    $scope.collaborativeFolderLink = DRAGDIS.config.shortUrlDomain + response.CollaborateUrl;
                        //}

                        //$scope.folderAlredyLinked = response.IsLinked;

                        if (!$scope.$$phase) {
                            $scope.$apply();
                        }

                    }, function (error) {
                        console.error(error);
                    });
                }
            };


            ///*Action panel zone*/
            $scope.actionPanelTemplate = function () {
                if ($scope.folder.actionPanelTemplate) {
                    return $scope.folder.actionPanelTemplate;
                }
                return null;
            };

            var factory = new actionBlockFactory($scope);
            angular.extend($scope, factory);
        }]
    };
}]);


dragdisSidebarDirectives.directive("folderEdit", ['folderEditFactory', 'dataService', '$sce', '$timeout', '$rootScope', function (folderEditFactory, dataService, $sce, $timeout, $rootScope) {

    var icons = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24];

    return {
        restrict: "A",
        require: ["^folder", "^actionsBlock"],
        link: function (scope, element, attrs, ctrls) {

            scope.folderCtrl = ctrls[0];
            scope.actionsBlockCtrl = ctrls[1];

            scope.folder = ctrls[0].folder;
        },
        controller: ['$scope', function ($scope) {

            $scope.icons = icons;

            $scope.init = function () {
                $scope.folderTitle = $scope.folder.newChild ? "New folder name" : "Folder name";

                //clone folder data
                if ($scope.folder.newChild) {
                    $scope.currentFolder = {
                        ID: 0,
                        Name: "",
                        Parent: $scope.folder.ID,
                        Icon: 0,
                        Type: 1,

                        newChild: true
                    };
                } else {
                    $scope.folderCopy = angular.copy($scope.folder);
                    $scope.currentFolder = $scope.folder;
                }
            };


            $scope.saveFolderDetails = function () {

                //Prevent rapid clicks
                if ($scope.isSavingInProgress) {
                    return;
                }

                //Prevent empty folder names
                if ($scope.folder.newChild) {
                    if (!$scope.currentFolder.Name.length) {
                        return;
                    }
                } else {
                    if (!$scope.folder.Name.length) {
                        return;
                    }
                }

                $scope.isSavingInProgress = true;

                if ($scope.folder.newChild) {
                    new TrackEvent("Sidebar", "New subfolder saved").send();

                    DRAGDIS.api("FolderCreate", $scope.currentFolder).then(function (response) {
                        $scope.isSavingInProgress = false;

                        //set newID to fake folder and convert to not fake 
                        if (response.ID > 0) {
                            //console.log("save success");

                            $rootScope.folderIDwithActiveActions = response.ID;

                            response.isNewFolder = true;
                            $scope.folder.Childs.push(response);

                            $scope.folder.Status = true;

                            dataService.updateFoldersCounters("plus");

                            $scope.actionsBlockCtrl.closeBlock();

                            $rootScope.$broadcast("updateAllFoldersOrder");

                            dataService.updateScroll();

                        } else {
                            console.log("delete fake error");
                        }

                    }, function () {
                        $scope.isSavingInProgress = false;
                    });

                } else {

                    new TrackEvent("Sidebar", "Folder settings saved").send();

                    if ($scope.folder.ID) {

                        DRAGDIS.api("FolderUpdate", $scope.folder).then(function (response) {
                            $scope.isSavingInProgress = false;

                            if (response.ID) {
                                $scope.actionsBlockCtrl.closeBlock();
                            }

                        }, function () {
                            $scope.isSavingInProgress = false;

                            if (!$scope.$$phase) {
                                $scope.$apply();
                            }
                        });
                    }
                }
            };

            $scope.deleteFolder = function () {

                var confirmModal = confirm("Are you sure you want to delete this item?");

                if (confirmModal == true) {
                    //Prevent rapid clicks
                    if ($scope.isDeletingInProgress) {
                        return;
                    }

                    $scope.isDeletingInProgress = true;

                    DRAGDIS.api("FolderDelete", $scope.folder).then(function (folder) {

                        $scope.isDeletingInProgress = false;

                        if (folder.ID) {
                            $scope.folderCtrl.deleteFolder();

                            dataService.updateFoldersCounters("minus", 1 + $scope.folder.Childs.length);
                        }
                    }, function () {
                        $scope.isDeletingInProgress = false;

                        if (!$scope.$$phase) {
                            $scope.$apply();
                        }
                    });
                }
            };

            $scope.cancelPanel = function () {
                if (!$scope.folder.newChild) {
                    $scope.folderCtrl.updateFolderValues($scope.folderCopy);
                }
                $scope.actionsBlockCtrl.closeActionPanel();

                $timeout(function () {
                    dataService.updateScroll();
                }, 100);
            };

            //detect when click newFolder or folder-edit, when folder-edit panel is active
            $scope.$watch("folder.newChild", function () {
                $scope.init();
            });

            var factory = new folderEditFactory($scope, $rootScope);
            angular.extend($scope, factory);

            $scope.init();
        }]
    };
}]);

dragdisSidebarDirectives.directive("newFolder", ['newFolderFactory', 'dataService', '$sce', '$timeout', '$rootScope', function (newFolderFactory, dataService, $sce, $timeout, $rootScope) {

    var icons = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24];

    var newFolderHeight = 234;
    var newFolderHeightWithIcons = newFolderHeight + 159;
    var defaultFoldersTopSpace = 60;

    return {
        restrict: "A",
        link: function (scope, element, attrs) {

            var dragdisFoldersBlock = $rootScope.foldersBlockElement;

            function realFolderListHeight() {
                var lastGroup = dragdisFoldersBlock.children("ul").children("li").last();

                if (lastGroup.length) {
                    var height = lastGroup.height() + lastGroup.position().top;
                } else {
                    height = 0;
                }
                return height;
            }

            scope.newFolder.movefoldersBlock = function () {
                var foldersBlockHeight = dragdisFoldersBlock.height() + dragdisFoldersBlock.position().top - defaultFoldersTopSpace;
                var foldersListHeight = realFolderListHeight();

                var space = foldersBlockHeight - foldersListHeight + defaultFoldersTopSpace;
                var needToMove = 0;

                space = space < 0 ? defaultFoldersTopSpace : space;

                if (this.active) {
                    if (this.showMoreIcons) {
                        if (space < newFolderHeightWithIcons) {
                            needToMove = newFolderHeightWithIcons - space;
                        } else if (space < newFolderHeight) {
                            needToMove = newFolderHeight - space;
                        }
                    } else if (space < newFolderHeight) {
                        needToMove = newFolderHeight - space;//newFolderHeight - space;
                    }

                    scope.$emit("block-move-up", { level: needToMove }); //move up #DRAGDIS_folders
                } else {
                    scope.$emit("block-move-up", { level: 0 }); //move down #DRAGDIS_folders
                }
            };

            element.mouseenter(function () {
                scope.left = "folders left";

                if (!scope.$$phase) {
                    scope.$digest();
                }
            }).mouseleave(function () {
                scope.left = "left";

                if (!scope.$$phase) {
                    scope.$digest();
                }
            });
        },
        controller: ['$scope', function ($scope) {

            $scope.newFolder = {
                icons: icons,

                Name: "",
                Icon: 0
            };
            var newFolder = $scope.newFolder;

            $scope.left = "left";

            newFolder.openNewFolderPanel = function () {
                this.active = true;

                this.movefoldersBlock();
            };

            newFolder.toogleIcons = function () {
                this.showMoreIcons = !this.showMoreIcons;

                this.movefoldersBlock();
            };


            newFolder.actionPanelTemplate = function () {
                if (this.active) {
                    return DRAGDIS.config.sidebarTemplatesRoot + "panel_newFolder";
                }
                return null;
            };

            //Folder Edit
            newFolder.saveNewFolder = function () {

                var $this = this;
                //Prevent rapid clicks
                if ($this.isSavingInProgress) {
                    return;
                }

                //Prevent empty folder names
                if (!$this.Name.length) {
                    return;
                }


                $this.isSavingInProgress = true;

                new TrackEvent("Sidebar", "New folder saved").send();

                //get order of last folder on list
                var latestFolder = DRAGDIS.sidebarController.folders.list.slice(-1).pop();
                var maxOrder = 1;
                if (latestFolder && latestFolder.Order) {
                    maxOrder = latestFolder.Order + 1;
                }

                DRAGDIS.api("FolderCreate", { Name: $this.Name, Icon: $this.Icon, Order: maxOrder }).then(function (response) {
                    $this.isSavingInProgress = false;

                    //set newID to fake folder and convert to not fake 
                    if (response.ID > 0) {
                        //console.log("new folder saved successfully");

                        $rootScope.folderIDwithActiveActions = response.ID;

                        response.Childs = [];
                        response.isNewFolder = true;

                        $scope.folders.list.push(response);

                        dataService.updateFoldersCounters("plus");

                        $this.cancelPanel();

                        $rootScope.$broadcast("updateAllFoldersOrder");

                        dataService.updateScroll();

                    } else {
                        console.log("save error");
                    }

                }, function () {
                    $this.cancelPanel();
                });
            };

            newFolder.cancelPanel = function () {
                this.showMoreIcons = false;
                var $this = this;
                $timeout(function () {
                    $this.Name = "";
                    $this.Icon = 0;

                    $this.active = false;

                    $this.movefoldersBlock();
                }, 10);

            };

            var factory = new newFolderFactory($scope, $rootScope);
            angular.extend(newFolder, factory);

            $scope.$on("resetSidebar", function () {
                newFolder.cancelPanel();
            });
        }]
    };
}]);

dragdisSidebarDirectives.directive("moreFolders", ['dialogService', '$state', function (dialogService, $state) {
    return {
        restrict: "A",
        controller: ['$scope', function ($scope) {
            $scope.getMoreFoldersPanel = function () {
                if (DRAGDIS.config.isExtension) {
                    dialogService.template = DRAGDIS.config.sidebarTemplatesRoot + "dialog.moreFolders";
                } else {
                    $state.go("get-more-folders");
                }
            };
        }]
    };
}]);



dragdisSidebarDirectives.directive("chatWindow", ['$sce', '$timeout', '$rootScope', function ($sce, $timeout, $rootScope) {
    var defaultFoldersTopSpace = 60;
    var chatWindowHeight = 500;

    return {
        restrict: "A",
        link: function (scope, element, attrs) {

            var dragdisFoldersBlock = $rootScope.foldersBlockElement;

            function realFolderListHeight() {
                var lastGroup = dragdisFoldersBlock.children("ul").children("li").last();

                if (lastGroup.length) {
                    var height = lastGroup.height() + lastGroup.position().top;
                } else {
                    height = 0;
                }
                return height;
            }

            scope.chatWindow.movefoldersBlock = function () {
                var foldersBlockHeight = dragdisFoldersBlock.height() + dragdisFoldersBlock.position().top - defaultFoldersTopSpace;
                var foldersListHeight = realFolderListHeight();

                var space = foldersBlockHeight - foldersListHeight + defaultFoldersTopSpace;
                var needToMove = 0;

                space = space < 0 ? defaultFoldersTopSpace : space;

                if (this.active) {
                    if (space < chatWindowHeight) {
                        needToMove = chatWindowHeight - space;//chatWindowHeight - space;
                    }
                    scope.$emit("block-move-up", { level: needToMove }); //move up #DRAGDIS_folders
                } else {
                    scope.$emit("block-move-up", { level: 0 }); //move down #DRAGDIS_folders
                }
            };

            element.mouseenter(function () {
                scope.left = "folders left";

                if (!scope.$$phase) {
                    scope.$digest();
                }
            }).mouseleave(function () {
                scope.left = "left";

                if (!scope.$$phase) {
                    scope.$digest();
                }
            });
        },
        controller: ['$scope', function ($scope) {

            $scope.chatWindow = {
                Text: "",
            };
            var chatWindow = $scope.chatWindow;

            $scope.left = "left";

            chatWindow.open = function () {
                this.active = true;
                this.movefoldersBlock();
            };

            chatWindow.actionPanelTemplate = function () {
                if (this.active) {
                    return DRAGDIS.config.sidebarTemplatesRoot + "panel_chatWindow";
                }
                return null;
            };

            //Folder Edit
            chatWindow.send = function () {
                alert('sending msg ' + $scope.chatWindow.Text);
                $scope.chatWindow.Text = '';
            };

            chatWindow.cancel = function () {
                var $this = this;
                $timeout(function () {
                    $this.Name = "";
                    $this.Icon = 0;
                    $this.active = false;
                    $this.movefoldersBlock();
                }, 10);

            };

            $scope.$on("resetSidebar", function () {
                chatWindow.cancel();
            });
        }]
    };
}]);
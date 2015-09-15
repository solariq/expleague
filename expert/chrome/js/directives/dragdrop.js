dragdisSidebarDirectives.directive('dragoverOnSidebar', ['$timeout', function ($timeout) {
    return {
        restrict: 'A',
        link: function (scope, element) {
            if (DRAGDIS.config.isExtension) {

                //**********************
                //** EXTENSION SIDE
                //**********************

                // NATIVE DROPING
                var dropzone = document.getElementById("dragdis-sidebar");
                new Dragster(dropzone);

                document.addEventListener("dragster:enter", function (e) {
                    DRAGDIS.scrollPreventer.position = $(window).scrollTop();

                    element.addClass('on-drag');
                    DRAGDIS.Drag.IsOnSidebar = true;
                    scope.scroll.showScrollHandles();

                }, false);

                document.addEventListener("dragster:leave", function (e) {
                    DRAGDIS.scrollPreventer.position = null;

                    element.removeClass('on-drag');
                    DRAGDIS.Drag.IsOnSidebar = false;
                    scope.scroll.showScrollHandles();
                }, false);

            } else {
                //**********************
                //** DRAGDIS.COM SIDE
                //**********************

                var detectDragOnSidebarTimeout = 0;

                scope.detectDragOnSidebar = function (e) {
                    $timeout.cancel(detectDragOnSidebarTimeout);
                    detectDragOnSidebarTimeout = $timeout(function () {
                        var screenHeight = $(window).height();
                        var screenWidth = $(window).width();

                        if (e.pageY > 60 && e.pageY < screenHeight - 120 && e.pageX > screenWidth - 260 && e.pageX <= screenWidth) {
                            DRAGDIS.Drag.IsOnSidebar = true;
                            element.addClass("on-drag");
                            scope.scroll.showScrollHandles();
                        } else {
                            DRAGDIS.Drag.IsOnSidebar = false;
                            element.removeClass("on-drag");
                            scope.scroll.hideScrollHandles();
                        }
                    }, 50);
                };

                scope.detectDragOnSidebarTimeout = 0;
                // jQuery UI DROPING
                element.droppable({
                    hoverClass: "drag-over",
                    tolerance: "pointer",
                    accept: ".folder, .thumbnail, .text-container, .item",
                    over: function (e) {
                        scope.detectDragOnSidebar(e);
                    },
                    out: function (e) {
                        scope.detectDragOnSidebar(e);
                    },
                    drop: function () {
                        element.removeClass("on-drag");
                    }
                });
            }

            scope.$on('dragEnd', function () {
                DRAGDIS.Drag.IsOnSidebar = false;
                element.removeClass('on-drag');
                scope.scroll.hideScrollHandles();
            });
        }
    };
}]);

dragdisSidebarDirectives.directive('folderDroppable', ['$timeout', '$window', '$rootScope', 'dragdisApi', '$injector', function ($timeout, $window, $rootScope, $dragdisApi, $injector) {
    return {
        restrict: 'A',
        link: function ($scope, element, attr) {
            //console.log("FOLDERDROPPABLE");

            var folderId = attr.folderDroppable;

            if (DRAGDIS.config.isExtension) {

                //**********************
                //** EXTENSION SIDE
                //**********************

                element
                    .on("drop", function (e) {

                        e.preventDefault(); //It's important line!! this kill redirect action
                        e.stopPropagation();

                        DRAGDIS_SIDEBAR.dragActive = false;

                        if ($scope.folder.Disabled) {
                            new TrackEvent("Sidebar", "Item dropped on disabled folder", DRAGDIS.Drag.Data.Type).send();
                            element.removeClass("hover");
                            return false;
                        }

                        $scope.folder.dragStatus = 1;

                        //File Uploading
                        var types = e.originalEvent.dataTransfer.types;
                        var files = e.originalEvent.dataTransfer.files;

                        if (files.length && $.inArray("Files", types) !== -1) {
                            var reader = new FileReader();
                            reader.readAsDataURL(files[0]),
                                reader.onloadend = function (ev) {
                                    if (ev.target.readyState == 2) {

                                        DRAGDIS.Drag.Data.Files = {}; //for MVP only one file
                                        DRAGDIS.Drag.Data.Files[0] = {
                                            type: files[0].type,
                                            binary: ev.target.result
                                        };
                                    }
                                };
                        }

                        $timeout(function () {

                            new TrackEvent("Sidebar", "Item dropped on folder", DRAGDIS.Drag.Data.Type).send();

                            // Close sidebar on mouseleave only for this time
                            $("#" + DRAGDIS_SIDEBAR_NAME).one("mouseleave", function () {
                                if (!DRAGDIS_SIDEBAR.openedByIcon) {
                                    DRAGDIS.sidebarController.hide();
                                }
                            });

                            // Notify onboarding that user dropped item
                            document.dispatchEvent(new CustomEvent('dragComplete'));

                            DRAGDIS_SIDEBAR.dragActive = false;
                            DRAGDIS.sidebarController.dragEnd();
                            DRAGDIS.sidebarController.upload(folderId, function (response) {

                                //if somebody not killed status, then continue to show success message
                                if ($scope.folder.dragStatus > 0) {

                                    if (response.status == 200) {

                                        $scope.folder.dragStatus = 2;
                                        $rootScope.activeFolderWithDrop = folderId;

                                        $scope.$emit("itemDropped", { dragStatus: 2 });

                                        // Notify onboarding that user dropped item
                                        document.dispatchEvent(new CustomEvent('uploadComplete'));

                                        $scope.folder.lastUploadID = response.data.Id;

                                        //TODO: replace url with from response
                                        $scope.folder.lastUploadUrl = "DEMO"; //response.data.ShortUrl;

                                        DRAGDIS.api("setUserSettings", {
                                            params: {
                                                lastDragTime: new Date().getTime()
                                            }
                                        });

                                        if (!$scope.$$phase) {
                                            $scope.$apply();
                                        }

                                    } else {
                                        if (response.status == 400) {
                                            //TODO: show Error about wrong drag
                                        }

                                        $scope.folder.dragStatus = 0;
                                        $rootScope.activeFolderWithDrop = null;

                                        if (!$scope.$$phase) {
                                            $scope.$apply();
                                        }
                                    }
                                }
                            });
                        }, 100);

                        element.removeClass("hover");

                        return false;
                    })
                    .on("dragenter", function (e) {
                        if (!$scope.folder.Disabled) {
                            element.addClass("hover");
                            document.dispatchEvent(new CustomEvent('dragEnterFolder'));
                        }
                    })
                    .on("dragover", function (e) {
                        e.originalEvent.preventDefault(); //this must be here, it's allow to DROP !!

                        if (!$scope.folder.Disabled) {
                            e.originalEvent.dataTransfer.dropEffect = "copy";
                            element.addClass("hover");

                            document.dispatchEvent(new CustomEvent('dragEnterFolder'));
                        } else {
                            e.originalEvent.dataTransfer.dropEffect = "none";
                        }
                    })
                    .on("dragleave", function () {

                        element.removeClass("hover");
                        if (!$scope.folder.Disabled) {
                            document.dispatchEvent(new CustomEvent('dragLeaveFolder'));
                        }
                    });

            } else {
                //**********************
                //** DRAGDIS.COM SIDE
                //**********************

                var $state = $injector.get('$state');

                element.droppable({
                    hoverClass: "hover",
                    tolerance: "pointer",
                    accept: ".thumbnail, .text-container",
                    greedy: true,
                    drop: function (event, ui) {

                        if ($scope.folder.Disabled) {
                            new TrackEvent("Sidebar", "Item dropped on disabled " + ($scope.folder.Type == 0 ? "group" : "folder")).send();
                            return;
                        }

                        new TrackEvent("Sidebar", "Item dropped on " + ($scope.folder.Type == 0 ? "group" : "folder")).send();

                        // Return if item is dropped to same opened folder 
                        if ($state.params.folderId == $scope.folder.ID) return;

                        $scope.folder.dragStatus = 1;

                        $(ui.helper, ui.draggable).hide();

                        //Get dragged item attributes
                        var item = ui.draggable.scope().item;
                        var itemIndex = ui.draggable.scope().$index;
                        var dropFolderId = $scope.folder.ID;

                        if (dropFolderId > 0 && item.FolderId !== dropFolderId) {

                            $scope.changeItemFolder(item.ID, dropFolderId, function (resp) {

                                $scope.folder.dragStatus = 3;
                                $scope.$emit("itemDropped", { dragStatus: 3 });

                                var gridController = angular.element(".draglist").scope();

                                if (itemIndex >= 0 && ($state.current.name !== "index" && $state.current.name !== "search")) {
                                    gridController.Items.List.splice(itemIndex, 1);
                                }
                            });

                        } else {
                            console.log("fire fail");
                            $(ui.helper, ui.draggable).show();

                            $scope.folder.dragStatus = 0;
                        }
                        if (!$scope.$$phase) {
                            $scope.$digest();
                        }
                    }
                });

            }


            //*************************
            //*** DRAG UPLOAD ZONE
            //*************************

            var mouseTimeoutReseter;
            $scope.$on("itemDropped", function (e, values) {

                if (values.dragStatus == 2 && !$scope.folder.actionPanelTemplate) {

                    $scope.lastUpload = {};

                    element
                        .mouseenter(function () {
                            $timeout.cancel(mouseTimeoutReseter);
                        })
                        .mouseleave(function () {
                            if (!$scope.folder.actionPanelTemplate) {
                                mouseTimeoutReseter = $timeout(function () {
                                    $scope.folder.dragStatus = 0;
                                    element.off("mouseleave mouseenter");
                                }, 1500);
                            }
                        });
                } else if (values.dragStatus == 3) {
                    $timeout(function () {
                        $scope.folder.dragStatus = 0;
                    }, 1500);
                }
            });
        },
        controller: ['$scope', function ($scope) {
            if (!DRAGDIS.config.isExtension) {
                $scope.changeItemFolder = function (itemId, folderId, callback) {
                    $dragdisApi.Item_Move(itemId, folderId).then(function (resp) {
                        callback(resp.data);
                    }, function (error) {
                        console.error(error);
                        new TrackException("Failed to move item").send();
                    });
                };
            }

            //*************************
            //*** DRAG UPLOAD ZONE
            //*************************

            //Jei leidziam atidaryti vel editinti, tai sita salinti
            $scope.lastUpload = {
                //tags: this.tags || []
            };

            $scope.actionPanelTemplate = function () {
                if ($scope.folder.actionPanelTemplate) {
                    return $scope.folder.actionPanelTemplate;
                }
                return null;
            };

            $scope.activateItemPanel = function () {
                $scope.folder.showActionsBlock = true;
                $scope.folder.actionPanelTemplate = "views/panel_item";
            };

            $scope.openLastItem = function () {
                if ($scope.folder.lastUploadID) {
                    new TrackEvent("Sidebar", "Open last item", DRAGDIS.Drag.Data.Type).send();

                    var url = DRAGDIS.config.domain + '#/item/' + $scope.folder.lastUploadID;

                    DRAGDIS.mouseIsOnSidebar = false;
                    DRAGDIS.sidebarController.hide(true);

                    $window.open(url);
                }
            };

            $scope.saveItemDetails = function () {
                //Prevent rapid clicks
                if ($scope.isSavingInProgress) {
                    return;
                }
                $scope.isSavingInProgress = true;

                $scope.lastUpload.ID = $scope.folder.lastUploadID;

                var tagsToString = $.map($scope.lastUpload.tempTags, function (item) {
                    return item.tag;
                }).join();

                $scope.lastUpload.tags = tagsToString;

                if (($scope.lastUpload.notes && $scope.lastUpload.notes.length) || tagsToString.length) {
                    DRAGDIS.api("UpdateLastUpload", $scope.lastUpload).then(function (response) {
                        $scope.isSavingInProgress = false;

                        if (response.ID > 0) {
                            //success
                        }

                        $scope.closePanel();

                        if (!$scope.$$phase) {
                            $scope.$digest();
                        }
                    }, function () {
                        $scope.isSavingInProgress = false;

                        if (!$scope.$$phase) {
                            $scope.$digest();
                        }
                    });
                } else {
                    //close without save
                    $scope.isSavingInProgress = false;

                    $scope.closePanel();

                    if (!$scope.$$phase) {
                        $scope.$digest();
                    }
                }
            };

            $scope.deleteItem = function () {
                var confirmModal = confirm("Are you sure you want to delete last item?");

                if (confirmModal == true) {
                    //Prevent rapid clicks
                    if ($scope.isDeletingInProgress) {
                        return;
                    }

                    $scope.isDeletingInProgress = true;
                    $scope.lastUpload.ID = $scope.folder.lastUploadID;

                    DRAGDIS.api("DeleteLastUpload", $scope.lastUpload).then(function (response) {
                        $scope.isDeletingInProgress = false;

                        if (response.ID > 0) {
                            $scope.closePanel();

                            $timeout(function () {
                                $scope.folder.dragStatus = 0;
                                $scope.folder.showActionsBlock = false;
                            }, 300);
                        }

                        if (!$scope.$$phase) {
                            $scope.$digest();
                        }
                    }, function () {
                        $scope.isDeletingInProgress = false;
                    });
                }
            };

            $scope.closePanel = function () {
                $scope.folder.actionPanelTemplate = null;

                if (!$scope.$$phase) {
                    $scope.$digest();
                }
            };
        }]
    };
}]);





//expand/collapse childs when drag on arrow
dragdisSidebarDirectives.directive('dragOpenChilds', ['$timeout', function ($timeout) {
    var hoverTime = 1000;

    return {
        restrict: 'A',
        link: function ($scope, element) {
            var expandTimeout, collapseTimeout;

            function toggleStatus() {
                if ($scope.folder.Status) {
                    collapseTimeout = $timeout(function () {
                        $scope.folder.Status = false;
                        console.log("over");
                    }, hoverTime);
                } else {
                    expandTimeout = $timeout(function () {
                        $scope.folder.Status = true;
                        console.log("over");
                    }, hoverTime);
                }
            }

            if (DRAGDIS.config.isExtension) {
                //**********************
                //** EXTENSION SIDE
                //**********************

                element
                    .on("dragenter", function () {
                        element.addClass("hover");
                        toggleStatus();
                    })
                    .on("dragleave", function () {
                        element.removeClass("hover");
                        $timeout.cancel(expandTimeout);
                        $timeout.cancel(collapseTimeout);
                    })
                    .on("drop", function () {
                        element.removeClass("hover");
                    });


                //icon-expand 
                element.droppable({
                    //hoverClass: "hover",
                    tolerance: "pointer",
                    accept: ".folder",
                    over: function () {
                        toggleStatus();
                        console.log("over");
                    },
                    out: function () {
                        $timeout.cancel(expandTimeout);
                        $timeout.cancel(collapseTimeout);
                    }
                });

            } else {
                //**********************
                //** DRAGDIS.COM SIDE
                //**********************

                element.droppable({
                    //hoverClass: "hover",
                    tolerance: "pointer",
                    accept: ".thumbnail, .text-container, .folder",
                    over: function () {
                        toggleStatus();
                        console.log("over");
                    },
                    out: function () {
                        $timeout.cancel(expandTimeout);
                        $timeout.cancel(collapseTimeout);
                    }
                });
            }

            $scope.$watch("folder.dragStatus", function (newVal, oldVal) {
                if (newVal !== oldVal) {
                    $timeout.cancel(expandTimeout);
                    $timeout.cancel(collapseTimeout);
                }
            });
        }
    };
}]);
knuggetSidebarDirectives.directive('dragoverOnSidebar', ['$timeout', function ($timeout) {
    return {
        restrict: 'A',
        link: function (scope, element) {
            if (KNUGGET.config.isExtension) {

                //**********************
                //** EXTENSION SIDE
                //**********************

                // NATIVE DROPING
                var dropzone = document.getElementById("knugget-sidebar");
                new Dragster(dropzone);

                document.addEventListener("dragster:enter", function (e) {
                    KNUGGET.scrollPreventer.position = $(window).scrollTop();

                    element.addClass('on-drag');
                    KNUGGET.Drag.IsOnSidebar = true;
                    scope.scroll.showScrollHandles();

                }, false);

                document.addEventListener("dragster:leave", function (e) {
                    KNUGGET.scrollPreventer.position = null;

                    element.removeClass('on-drag');
                    KNUGGET.Drag.IsOnSidebar = false;
                    scope.scroll.showScrollHandles();
                }, false);
            }

            scope.$on('dragEnd', function () {
                KNUGGET.Drag.IsOnSidebar = false;
                element.removeClass('on-drag');
                scope.scroll.hideScrollHandles();
            });
        }
    };
}]);

knuggetSidebarDirectives.directive('folderDroppable', ['$timeout', '$window', '$rootScope', 'knuggetApi', '$injector', function ($timeout, $window, $rootScope, $knuggetApi, $injector) {
    return {
        restrict: 'A',
        link: function ($scope, element, attr) {
            //console.log("FOLDERDROPPABLE");

            var folderId = attr.folderDroppable;
                element
                    .on("drop", function (e) {
                        e.preventDefault(); //It's important line!! this kill redirect action
                        e.stopPropagation();

                    KNUGGET_SIDEBAR.dragActive = false;

                    var types = e.originalEvent.dataTransfer.types;
                        console.log("types");
                        console.log(types);
                    var files = e.originalEvent.dataTransfer.files;

                    console.log(KNUGGET.Drag.Data);
                    if (files.length && $.inArray("Files", types) !== -1) {
                        console.log(files.length);
                        var reader = new FileReader();
                        reader.readAsDataURL(files[0]),
                            reader.onloadend = function (ev) {
                                if (ev.target.readyState == 2) {
                                    KNUGGET.Drag.Data.Files = {}; //for MVP only one file
                                    KNUGGET.Drag.Data.Files[0] = {
                                        type: files[0].type,
                                        binary: ev.target.result
                                    };
                                }
                            };
                    }

                    $timeout(function () {

                        // Close sidebar on mouseleave only for this time
                        $("#" + KNUGGET_SIDEBAR_NAME).one("mouseleave", function () {
                            if (!KNUGGET_SIDEBAR.openedByIcon) {
                                KNUGGET.sidebarController.hide();
                            }
                        });


                        KNUGGET_SIDEBAR.dragActive = false;
                        KNUGGET.sidebarController.dragEnd();
                        KNUGGET.sidebarController.addAnswer(function () {
                            $scope.$emit("itemDropped", {});
                            if (!$scope.$$phase) {
                                $scope.$apply();
                            }
                        });
                    }, 100);

                    element.removeClass("hover");

                    return false;
                })
                .on("dragenter", function (e) {
                    element.addClass("hover");
                    document.dispatchEvent(new CustomEvent('dragEnterFolder'));
                })
                .on("dragover", function (e) {
                    e.originalEvent.preventDefault(); //this must be here, it's allow to DROP !!


                    e.originalEvent.dataTransfer.dropEffect = "copy";
                    element.addClass("hover");

                    document.dispatchEvent(new CustomEvent('dragEnterFolder'));
                })
                .on("dragleave", function () {
                    element.removeClass("hover");
                    document.dispatchEvent(new CustomEvent('dragLeaveFolder'));
                });


            //*************************
            //*** DRAG UPLOAD ZONE
            //*************************

            var mouseTimeoutReseter;
            $scope.$on("itemDropped", function (e, values) {

                $scope.lastUpload = {};

                element
                    .mouseenter(function () {
                        $timeout.cancel(mouseTimeoutReseter);
                    })
                    .mouseleave(function () {
                        if (!$scope.folder.actionPanelTemplate) {
                            mouseTimeoutReseter = $timeout(function () {
                                element.off("mouseleave mouseenter");
                            }, 1500);
                        }
                    });
            });
        },
        controller: ['$scope', function ($scope) {
            //*************************
            //*** DRAG UPLOAD ZONE
            //*************************

            //Jei leidziam atidaryti vel editinti, tai sita salinti
            $scope.lastUpload = {
                //tags: this.tags || []
            };



        }]
    };
}]);

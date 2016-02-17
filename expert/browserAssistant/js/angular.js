﻿﻿knuggetSidebar.controller('KNUGGET_SIDEBAR_CTRL', ['$window', '$scope', '$interval', '$controller', '$timeout', '$rootScope', 'dataService', '$q', function ($window, $scope, $interval, $controller, $timeout, $rootScope, dataService, dialogService, $q) {

    //var mapOptions = {
    //    zoom: 4,
    //    center: new google.maps.LatLng(40.0000, -98.0000),
    //    mapTypeId: google.maps.MapTypeId.TERRAIN
    //};
    //
    //$scope.map = new google.maps.Map(document.getElementById('map'), mapOptions);


    $scope.domain = KNUGGET.config.domain;
    $scope.renderComplete = false;

    $scope.menu = {
        active: false
    };
    $scope.active = false;
    $scope.connectionFail = false;
    $scope.appMode = 0;
    $scope.timeoutShow = 0;
    $scope.timeoutHide = 0;
    $scope.uploadError = 0;
    $scope.expandedView = 0;


    if (KNUGGET.config.isExtension) {
        $scope.$on('$locationChangeStart', function (ev) {
            ev.preventDefault();
        });
    }


    $scope.readableTime = function(time) {
        if (time > 60) {
            return Math.floor(time / 60) + ' мин. ' + (time - 60 * Math.floor(time / 60)) + ' с.'
        }
        return time + ' с.';
    };

    $scope.clear = function() {
        $scope.board.clear();
    };

    $scope.truncateTitle = function(title) {
        if (title.indexOf('Результат поиска Google') === 0) {
            return 'Результат поиска Google';
        }
        return title;
    };


    $scope.getImgPrefix = function(base64Image) {
        return base64Image.split(',', 1)[0];//.slice(1).join(',')
    };

    $scope.getImgData = function(base64Image) {
        return base64Image.split(',').slice(1).join(',');
    };

    $scope.removeFromBorder = function(index) {
        KNUGGET.api('Remove', {index : index}, function (response) {});
        if (!$scope.$$phase) {
            $scope.$apply();
        }
    };

    $scope.printableAnswer = function(answer) {
        if (answer.Base64Image) {
            return answer.Base64Image;
        }
        text = jQuery('<div>' + answer.Text + '</div>').text();;
        maxLetters = 200;
        if (text.length > maxLetters) {
            text = text.substring(0, maxLetters) + '...';
        }
        return jQuery('<div>' + text + '</div>').text();
    };

    $scope.getBubbleClass = function(owner) {
        return 'triangle-isosceles' + (owner == 'self' ? '' : '-alt');
    };

    $scope.addAnswer = function(callback) {
        this.getCollection(function () {
            KNUGGET.Drag.Data.Status = 1;
            answer = JSON.stringify(KNUGGET.Drag.Data);
            KNUGGET.api('addToBoard', {answer : answer}, function (response) {});
            if (typeof (callback) == "function") {
                callback();
            }
        });
    };

    $scope.sidebarContent = function () {
        return KNUGGET.config.sidebarTemplatesRoot + 'dragArea';

        //if ($scope.activeRequest.value && $scope.allowToShow.value)
        //
        ////
        //if ($scope.requests.list.length)
        //    return templatesRoot + 'requestList';
        //
        ////alert('waiting');
        //return templatesRoot + ($scope.user.available ? 'waiting' : 'unavailable');
    };

    $scope.sidebarTemplate = function () {
        return KNUGGET.config.sidebarTemplatesRoot + "sidebar";
    };

    // STORAGE LISTENERS
    if (window.chrome && window.chrome.storage) {
        window.chrome.storage.onChanged.addListener(function (data) {

            console.log("storage updated", data);
            if(data.Board) {
                $scope.board.update();
                //$scope.moveChatScroll(1, 1500);
                if (!$scope.$$phase) {
                    //alert('apply');
                    $scope.$apply();
                }
            } else if (data.AddTrigger) {
                if (data.AddTrigger.newValue.shouldAdd) {
                    KNUGGET.storage.set('AddTrigger', {shouldAdd: false});
                    if (KNUGGET.Drag.Clicked) {
                        KNUGGET.Drag.Clicked.knuggetCollection(function (data) {
                            var answer = JSON.stringify(data);
                            KNUGGET.api('addToBoard', {answer: answer}, function (response) {
                            });
                        });
                        KNUGGET.Drag.Clicked = null;
                    }
                }

            }
            $timeout(function () {
                if (!$scope.$$phase) {
                    //alert('apply');
                    $scope.$apply();
                }
            }, 100);
        });
    }


    $scope.board = {
        list: [],

        update: function() {
            KNUGGET.storage.get('Board', function(value) {
                $scope.board.list = $scope.board.getBoard(value);
                //alert('newlwn: ' + $scope.board.list.length);
            });
        },

        clear: function() {
            $scope.board.list = [];
            KNUGGET.storage.set('Board',JSON.stringify([]));
        },

        getBoard: function(data) {
            var result = [];
            (data ? JSON.parse(data) : []).forEach(function (el) {
                result.push(JSON.parse(el));
            });
            return result;
        }

    };



    $scope.user = {
        username: "",
        avatar: "",
        active: 0,
        available: true,
    };


    $scope.closeSidebar = function () {
        KNUGGET_SIDEBAR.openedByIcon = false;
        $scope.hide(true, true);
    };

    $scope.show = function (needReset, isOpenedManually) {

        console.log("SHOW", needReset + " " + isOpenedManually);

        $timeout.cancel($scope.timeoutHide);
        $timeout.cancel($scope.timeoutShow);


        $scope.timeoutShow = $timeout(function () {

            //var timeStart = new Date().getTime();

            $timeout.cancel($scope.timeoutHide);

            if (KNUGGET.config.firstTimeLoad) {
                $timeout(function () {

                    //var lastTime = new Date().getTime();
                    //console.log("LAIKAS" + (lastTime - timeStart));

                    $scope.active = true;

                    delete KNUGGET.config.firstTimeLoad;

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

            if (KNUGGET_SIDEBAR.dragActive) {
                $scope.dragStart();
            }

            if (!$scope.$$phase) {
                $scope.$apply();
            }

        }, isOpenedManually ? 0 : KNUGGET.config.timing.dragDelay);

        //$scope.moveChatScroll(1, 0);
    };

    $scope.hide = function (closeFast, isClosedManually) {

        $timeout.cancel($scope.timeoutShow);
        $timeout.cancel($scope.timeoutHide);

        if (KNUGGET_SIDEBAR.openedByIcon || $scope.appMode) {
            return false;
        }

        //If user expanded sidebar panel, while dragging, ignore Hide event 
        if (!KNUGGET_SIDEBAR.openedByIcon && $scope.expandedView) {
            return false;
        }

        $scope.timeoutHide = $timeout(function () {
            if ($scope.appMode) {
                return;
            }

            // Check if mouse is on sidebar. If true, register eve;nt to close sidebar after mouse leave
            if (!closeFast && KNUGGET.mouseIsOnSidebar) {
                $("#" + KNUGGET_SIDEBAR_NAME).one("mouseleave", function () {
                    $scope.hide();
                });
                return;
            }

            $timeout.cancel($scope.timeoutShow);

            KNUGGET_SIDEBAR.dragActive = false;
            $scope.active = false;
            $scope.expandedView = false;

            $scope.resetSidebar();

            if (!$scope.$$phase) {
                $scope.$apply();
            }

        }, closeFast ? 0 : KNUGGET.config.timing.sidebarClose);
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

        var dragElement = KNUGGET.Drag.Target;
        dragElement.knuggetCollection(function (data) {
            KNUGGET.Drag.Data = data;
            callback();
        });

    };

    $scope.init = function () {
        KNUGGET.sidebarController = $scope;
        $scope.board.update();

        //Instantly show sidebar if it is not set to initialize in hidden mode
        if (!KNUGGET.config.initHidden) {
            if (KNUGGET.config.firstTimeLoad) {
                $scope.show(null, true);
            } else {
                $scope.show(null, KNUGGET.config.isInitializedManually);
            }
        }
    };

    $scope.init();
}]);

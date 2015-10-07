﻿﻿knuggetSidebar.controller('DRAGDIS_SIDEBAR_CTRL', ['$window', '$scope', '$controller', '$timeout', '$rootScope', 'dataService', 'dialogService', function ($window, $scope, $controller, $timeout, $rootScope, dataService, dialogService) {
    $scope.domain = KNUGGET.config.domain;
    $scope.dialogIsActive = false;
    $scope.renderComplete = false;

    //default auth form
    $scope.isLoginForm = true;

    $scope.menu = {
        active: false,
        openMoreFoldersDialog: function () {
            $scope.hide(true);
            dialogService.template = KNUGGET.config.sidebarTemplatesRoot + "dialog.moreFolders";
        }
    };
    $scope.active = false;
    $scope.connectionFail = false;
    $scope.appMode = 0;
    $scope.timeoutShow = 0;
    $scope.timeoutHide = 0;
    $scope.uploadError = 0;
    $scope.expandedView = 0;

    //$scope.activeRequest = null;

    $scope.msg = {
        default: "",
        loading: "Loading folders...",
        noFolders: "You don't have folders. Create one by clicking 'Add +'",
        timeout: "Cannot connect to server. Please try again later."
    };

    if (KNUGGET.config.isExtension) {
        $scope.$on('$locationChangeStart', function (ev) {
            ev.preventDefault();
        });
    }


    $scope.send = function() {
        KNUGGET.api('SendResponse', {request: $scope.activeRequest.value}, function (response) {
            if (response.status == 200) {
                //$scope.activeRequest = request;
                KNUGGET.api('Finish', {request: $scope.activeRequest.value}, function(responce){});
                $scope.clear();
            } else {
                alert('Не удалось отправить ответ, повторите попытку');
            }
        });
    };

    $scope.clear = function() {
        $scope.board.clear();
        $scope.requests.clear();
        $scope.activeRequest.set(null);
    };


    $scope.findRequest = function(allRequests, req) {
        var index = -1;
        allRequests.forEach(function(el, i) {
            if (req.id == el.id)
                index = i;
        });
        return index;
    }

    $scope.activateRequest = function(request) {
        $scope.board.clear();
        KNUGGET.api('Activate', {request: request}, function (response) {
            if (response.status == 200) {
                $scope.activeRequest.set(request);
            } else {
                alert('Ваша заявка отклонена сервисом');
            }
        });
        $timeout(function () {
            if (!$scope.$$phase) {
                //alert('apply');
                $scope.$apply();
            }
        }, 100);
    };

    $scope.rejectRequest = function(request) {
        KNUGGET.api('Reject', {request: request}, function (response) {});
        if ($scope.activeRequest.value == request) {
            $scope.activeRequest.set(null);
        }
        if (!$scope.$$phase) {
            $scope.$apply();
        }
    };

    $scope.truncateTitle = function(title) {
        if (title.indexOf('Результат поиска Google') === 0) {
            return 'Результат поиска Google';
        }
        return title;
    }


    $scope.getImgPrefix = function(base64Image) {
        return base64Image.split(',', 1)[0];//.slice(1).join(',')
    }

    $scope.getImgData = function(base64Image) {
        return base64Image.split(',').slice(1).join(',');
    }

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

    $scope.moveChatScroll = function(direction, speed) {
        div = $('#chatboard')[0];
        if (direction > 0) {
            direction = div.scrollHeight
        } else {
            direction = 0;
        }
        if (speed > 0) {
            $('#chatboard').animate({scrollTop: direction}, speed)
        } else {
            $('#chatboard').scrollTop = direction;
        }
    }


    $scope.sidebarContent = function () {
        var templatesRoot = KNUGGET.config.sidebarTemplatesRoot;

        console.log('ar' + $scope.activeRequest.value);
        console.log('al' + $scope.allowToShow.value);
        if ($scope.activeRequest.value && $scope.allowToShow.value)
            return templatesRoot + 'dragArea';
        //
        if ($scope.requests.list.length)
            return templatesRoot + 'requestList';

        //alert('waiting');
        return templatesRoot + 'waiting';
    }

    $scope.sidebarTemplate = function () {

        var templatesRoot = KNUGGET.config.sidebarTemplatesRoot;

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

        //return 'views/sidebar';
        return template;
    };

    // STORAGE LISTENERS
    if (window.chrome && window.chrome.storage) {
        window.chrome.storage.onChanged.addListener(function (data) {

            console.log("storage updated", data);

            if (data.UserActive) {
                console.log("UserActive", data.UserActive.newValue);
                console.log("storage UserActive");
                $scope.user.update();
            } else if (data.ConnectionFail) {
                console.log("storage ConnectionFail");
                $scope.user.update();
            } else if (data.IsConnected) {
                $scope.isConnected = data.IsConnected.newValue;
                if ($scope.isConnected) {
                    console.log("storage isConnected");
                    $scope.user.update();
                } else {
                    if (!$scope.$$phase) {
                        $scope.$digest();
                    }
                }
            } else if(data.Board) {
                $scope.board.update();
                //$scope.moveChatScroll(1, 1500);
                if (!$scope.$$phase) {
                    //alert('apply');
                    $scope.$apply();
                }
            } else if (data.AllowToShow) {
                $scope.allowToShow.set(data.AllowToShow)
            } else if(data.Requests) {
                $scope.requests.update();
                if (data.Requests.oldValue) {
                    oldVal = JSON.parse(data.Requests.oldValue);
                    newVal = JSON.parse(data.Requests.newValue);
                    newVal.forEach(function(request) {
                        index = $scope.findRequest(oldVal, request);
                        if (index < 0) {
                           KNUGGET.api("Notify", {owner: request.owner, tag: "new-request",body : request.question}, function (resp){
                               if (resp.status == 200) {
                                   $scope.activateRequest(request);
                               } else if (resp.status == 501) {
                                   alert('reject');
                                   $scope.rejectRequest(request);
                               }
                           });
                       }
                    });

                }
                //$scope.moveChatScroll(1, 1500);
                if (!$scope.$$phase) {
                    //alert('apply');
                    $scope.$apply();
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


    $scope.requests = {
        list: [],

        update: function() {
            KNUGGET.storage.get('Requests', function(value) {
                $scope.requests.list = $scope.requests.getRequests(value);
            });
        },

        getRequests: function(data) {
            var result = [];
            (data ? JSON.parse(data) : []).forEach(function (el) {
                result.push(el);
            });
            return result;
        },

        removeRequest: function(request) {
            result = [];
            $scope.requests.list.forEach(function(el) {
               if (request.id != el.id) {
                   result.push(el);
               }
            });
            $scope.requests.list = result;
            KNUGGET.storage.set('Requests', JSON.stringify(result));
        },

        clear: function() {
            $scope.requests.list = [];
            KNUGGET.storage.set('Requests', JSON.stringify([]));
        }
    };

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


    $scope.allowToShow = {
        value: null,

        get: function(callback) {
            KNUGGET.storage.get('allowToShow', function(value) {
                $scope.allowToShow.value = value;
                callback(value);
            });
        },
        set: function(activeValue) {
            $scope.allowToShow.value = activeValue;
            KNUGGET.storage.set('allowToShow', activeValue);
        }
    };

    $scope.activeRequest = {
        value: null,

        get: function(callback) {
            KNUGGET.storage.get('ActiveRequest', function(value) {
                value = value ? value : null;
                $scope.activeRequest.value = value;
                callback(value);
            });
        },
        set: function(activeRequest) {
            $scope.activeRequest.value = activeRequest;
            KNUGGET.storage.set('ActiveRequest', activeRequest);
        }
    };
    $scope.user = {
        username: "",
        avatar: "",
        active: 0,
        apps: {},
        update: function () {

            var currentUser = this;
            //var laikas = new Date().getTime();

            KNUGGET.storage.get(["UserActive", "IsConnected", "ConnectionFail"], function (values) {

                //console.log("storage", new Date().getTime() - laikas);

                if (values.hasOwnProperty("UserActive")) {
                    currentUser.active = values.UserActive.Active;
                    currentUser.username = values.UserActive.Username;

                    if (values.UserActive.Avatar) {
                        currentUser.avatar = values.UserActive.Avatar;
                    } else {
                        currentUser.avatar = KNUGGET.extensionFileUrl("images/default_avatar.gif");
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

        console.log("SHOW", needReset + " " + isOpenedManually);

        $timeout.cancel($scope.timeoutHide);
        $timeout.cancel($scope.timeoutShow);

        KNUGGET.storage.get("IsConnected", function (value) {
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

            if (DRAGDIS_SIDEBAR.dragActive) {
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
            if (!closeFast && KNUGGET.mouseIsOnSidebar) {
                $("#" + DRAGDIS_SIDEBAR_NAME).one("mouseleave", function () {
                    $scope.hide();
                });
                return;
            }

            $timeout.cancel($scope.timeoutShow);

            KNUGGET.sendMessage({
                Type: "SendTrackData"
            });

            DRAGDIS_SIDEBAR.dragActive = false;
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


    $scope.hideUploadError = function () {

        //Hide sidebar if it was not force opened
        if (!DRAGDIS_SIDEBAR.openedByIcon) {
            $scope.active = false;
            $scope.resetSidebar();
        };

        $scope.uploadError = false;
    };


    $scope.finishRequest = function() {
        $scope.activeRequest.get(function(request) {
            KNUGGET.api("Finish", {request: request}, function () { });
            $scope.board.clear();
            $scope.requests.removeRequest(request);
            $scope.activeRequest.set(null);
        });
    };

    $scope.logout = function ($event) {

        $event.preventDefault();
        $scope.resetSidebar();

        KNUGGET.api("Logout", {}, function () { });
    };

    $scope.init = function () {
        KNUGGET.sidebarController = $scope;

        $scope.board.update();
        $scope.allowToShow.get(function(){});
        $scope.activeRequest.get(function(){});

        KNUGGET.storage.get("IsConnected", function (userConnected) {
            if (userConnected) {
                $scope.user.update();
            }

            $scope.isConnected = userConnected ? true : false;
        });

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

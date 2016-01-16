﻿﻿knuggetSidebar.controller('KNUGGET_SIDEBAR_CTRL', ['$window', '$scope', '$interval', '$controller', '$timeout', '$rootScope', 'dataService', 'dialogService', function ($window, $scope, $interval, $controller, $timeout, $rootScope, dataService, dialogService) {

    //var mapOptions = {
    //    zoom: 4,
    //    center: new google.maps.LatLng(40.0000, -98.0000),
    //    mapTypeId: google.maps.MapTypeId.TERRAIN
    //};
    //
    //$scope.map = new google.maps.Map(document.getElementById('map'), mapOptions);


    $scope.domain = KNUGGET.config.domain;
    $scope.dialogIsActive = false;
    $scope.renderComplete = false;

    //default auth form
    $scope.isLoginForm = true;
    $scope.isRegisterForm = false;

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

    $scope.afterMapInit = function() {
        alert('after');
    };

    $scope.confirmTimer = $interval(function() {
        var now = Date.now();
        if ($scope.activeRequest.value) {
            //has active request
            if (now >= $scope.activeRequest.value.resolveExpireTime) {
                $scope.forceCloseDialog();
                $scope.rejectRequest($scope.activeRequest.value);
                //alert('Время на исполнение заказа истекло');
            }
            $scope.activeRequest.value.timeleft = Math.floor(($scope.activeRequest.value.resolveExpireTime - now) / 1000);
        } else {
            $scope.requests.list.forEach(function(el) {
               if (now >= el.confirmExpireTime) {
                   $scope.forceCloseDialog();
                   $scope.rejectRequest(el);
                   //alert('Время на подтверждение заказа истекло');
               }
                el.timeleft = Math.floor((el.confirmExpireTime - now) / 1000);
            });
        }
    }, 1000);

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


    $scope.isDialogActive = function() {
        return dialogService.dialogIsActive;
    };

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

    $scope.readableTime = function(time) {
        if (time > 60) {
            return Math.floor(time / 60) + ' мин. ' + (time - 60 * Math.floor(time / 60)) + ' с.'
        }
        return time + ' с.';
    };

    $scope.clear = function() {
        $scope.board.clear();
        $scope.requests.clear();
        $scope.activeRequest.set(null);
        KNUGGET.storage.set("ChatLog", JSON.stringify([]));
        KNUGGET.storage.set("AddTrigger", {shouldAdd: false});
    };


    $scope.findRequest = function(allRequests, req) {
        var index = -1;
        allRequests.forEach(function(el, i) {
            if (req.id == el.id)
                index = i;
        });
        return index;
    };

    $scope.activateRequest = function(request) {
        console.log("activate request");
        console.log(request);

        $scope.board.clear();
        KNUGGET.storage.set("ChatLog", JSON.stringify([]));
        KNUGGET.storage.set("AddTrigger", {shouldAdd: false});
        request.isUnconfirmed = false;
        //$interval.cancel($scope.requests.confirmTimer);
        KNUGGET.api('Activate', {request: request}, function (response) {
            if (response.status == 200) {
                //request.timer = $interval(function() {
                //    request.timeleft -= 1;
                //    if (request.timeleft <= 0) {
                //        $scope.rejectRequest(request);
                //        alert('Время на выполнение задания истекло!');
                //    }
                //}, 1000);
                $scope.activeRequest.set(request);
                $scope.requests.removeRequest(request);
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


    $scope.showQuestion = function(request) {
        request.isUnconfirmed = true;
        dialogService.request = request;
        dialogService.template = KNUGGET.config.sidebarTemplatesRoot + "question";
        dialogService.dialogIsActive = true;
    };

    $scope.forceCloseDialog = function() {
        dialogService.template = null;
        dialogService.dialogIsActive = false;
    };

    $scope.rejectRequest = function(request) {
        KNUGGET.api('Reject', {request: request}, function (response) {});
        //if (request.timer) {
        //    $interval.cancel(request.timer);
        //}
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
    };


    $scope.getImgPrefix = function(base64Image) {
        return base64Image.split(',', 1)[0];//.slice(1).join(',')
    };

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

    $scope.getTitileToEdit = function() {
        return dialogService.originReaponse.Title;
    };

    $scope.isText = function() {
        return dialogService.isText || dialogService.isNew;
    };

    $scope.getTextToEdit = function() {
        return dialogService.originReaponse.Text;
    };


    $scope.sidebarContent = function () {
        var templatesRoot = KNUGGET.config.sidebarTemplatesRoot;
        if ($scope.activeRequest.value && $scope.allowToShow.value)
            return templatesRoot + 'dragArea';
        //
        if ($scope.requests.list.length)
            return templatesRoot + 'requestList';

        //alert('waiting');
        return templatesRoot + ($scope.user.available ? 'waiting' : 'unavailable');
    };

    $scope.isWorkingOnAnswer = function() {
        return $scope.activeRequest.value && $scope.allowToShow.value;
    };


    $scope.sidebarTemplate = function () {

        var templatesRoot = KNUGGET.config.sidebarTemplatesRoot;

        //If uppload error is flagged show error-box instead of sidebar
        if ($scope.uploadError)
            return templatesRoot + "uploadError";

        //If connection error is flagged, display Oops... message
        if ($scope.connectionFail)
            return templatesRoot + "error";

        //If user is connected, return sidebar template, else load login template
        var template;
        if ($scope.isConnected === true) {
            template = templatesRoot + "sidebar";
        } else {
            if ($scope.isRegisterForm) {
                template = templatesRoot + "registration";
            } else if ($scope.isLoginForm) {
                template = templatesRoot + "login";
            } else {
                console.log("ERROR: unexpected state!");
                template = templatesRoot + "login";
            }
        }

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
            } else if (data.UserAvailable) {
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
                $scope.forceCloseDialog(); //only one request is allowed, so close all dialong on request list changed
                $scope.requests.update();
                if (data.Requests.oldValue) {
                    oldVal = JSON.parse(data.Requests.oldValue);
                    newVal = JSON.parse(data.Requests.newValue);
                    newVal.forEach(function(request) {
                        index = $scope.findRequest(oldVal, request);
                        if (index < 0) {
                            console.log("send notification: " + request.question);
                           KNUGGET.api("Notify", {id: request.id, owner: request.owner, tag: "new-request",body : request.question}, function (resp){
                               req = $scope.requests.getSame(request);
                               if (resp.status == 200) {
                                   $scope.showQuestion(req);
                               } else if (resp.status == 501) {
                                   //$interval.cancel($scope.requests.confirmTimer);
                                   $scope.rejectRequest(req);
                               }
                               $scope.$apply();
                           });
                       }
                    });
                }

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

            } else if (data.ActiveRequest) {
                $scope.forceCloseDialog(); //close question dialog
                $scope.activeRequest.value = data.ActiveRequest.newValue;
            } else if (data.ChatLog) {
                $scope.chatLog.update(data.ChatLog.newValue);
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
        //confirmTimer: $interval(function() {
        //    $scope.requests.list.forEach(function(req) {
        //        req.timeleft -= 1;
        //        if (req.timeleft <= 0) {
        //            $scope.rejectRequest(req);
        //        }
        //    });
        //}, 1000),

        getSame: function(req) {
            var same = [req];
            $scope.requests.list.forEach(function(el) {
                if (req.id == el.id) {
                    same.push(el);
                }
            });
            return same[[same.length-1]];
        },

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

    $scope.chatLog = {
        value: null,
        unread: 0,

        update: function(newVal) {
        var actual = JSON.parse(newVal);
          $scope.chatLog.value = actual.history;
          $scope.chatLog.unread = actual.unread;
        },

        get: function(callback) {
            KNUGGET.storage.get('ChatLog', function(value) {
                $scope.chatLog.update(value);
                callback(JSON.parse(value));
            });
        },

        readall: function() {
            //don't need to clean if all messages were read
            if ($scope.chatLog.unread > 0) {
                KNUGGET.api("ReadAllChat", {}, function () {});
            }
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
        },

        banOwner: function(callback) {
            $scope.finishRequest();
            KNUGGET.api("BanUser", $scope.activeRequest.value, function () {});
        },

        hasImage: function() {
            return $scope.activeRequest.value.img != undefined && $scope.activeRequest.value.img != null;
        },

        getImage: function() {
            return $scope.activeRequest.value.img;
        },

        reject: function() {
            $scope.finishRequest();
            KNUGGET.api("Reject", $scope.activeRequest.value, function () {});
        }


    };
    $scope.user = {
        username: "",
        avatar: "",
        active: 0,
        available: true,

        makeUnavailable: function() {
            $scope.finishRequest();
            KNUGGET.api("Available", {isAvailable: false}, function () { });
        },
        makeAvailable: function() {
            KNUGGET.api("Available", {isAvailable: true}, function () { });
        },
        update: function () {

            var currentUser = this;
            //var laikas = new Date().getTime();

            KNUGGET.storage.get(["UserAvailable", "UserActive", "IsConnected", "ConnectionFail"], function (values) {

                //console.log("storage", new Date().getTime() - laikas);

                if (values.hasOwnProperty("UserAvailable")) {
                    console.log("UserAvailable: " + values.UserAvailable)
                    currentUser.available = values.UserAvailable;
                }

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
        KNUGGET_SIDEBAR.openedByIcon = false;
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


    $scope.hideUploadError = function () {

        //Hide sidebar if it was not force opened
        if (!KNUGGET_SIDEBAR.openedByIcon) {
            $scope.active = false;
            $scope.resetSidebar();
        };

        $scope.uploadError = false;
    };


    $scope.finishRequest = function() {
        $scope.activeRequest.get(function(request) {
            KNUGGET.api("Finish", {request: request}, function () { });
            $scope.board.clear();
            $scope.requests.clear();
            $scope.activeRequest.set(null);
            KNUGGET.storage.set("ChatLog", JSON.stringify([]));
            KNUGGET.storage.set("AddTrigger", {shouldAdd: false});
        });
    };

    $scope.logout = function ($event) {

        $event.preventDefault();
        $scope.resetSidebar();

        $scope.finishRequest();

        KNUGGET.api("Logout", {}, function () { });
    };


    $scope.ac = function() {
        alert('ping');
    };

    $scope.init = function () {
        KNUGGET.sidebarController = $scope;
        $scope.board.update();
        $scope.allowToShow.get(function(){});
        $scope.activeRequest.get(function(){});
        $scope.requests.update();

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

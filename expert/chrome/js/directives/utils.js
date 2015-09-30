dragdisSidebarDirectives.directive('avatar', ['$q', '$timeout', '$rootScope', function ($q, $timeout, $rootScope) {
    function isImage(src) {

        var deferred = $q.defer();

        var image = new Image();
        image.onerror = function () {
            deferred.resolve(false);
        };
        image.onload = function () {
            deferred.resolve(true);
        };
        image.src = src;

        return deferred.promise;
    }

    return {
        restrict: 'A',
        scope: {
            avatar: "&image"
        },
        link: function (scope, element) {
            $timeout(function () {
                isImage(scope.avatar()).then(function (exist) {
                    if (!exist) {
                        element.attr("src", DRAGDIS.extensionFileUrl("images/default_avatar.gif"));
                        if (DRAGDIS.config.isExtension) {
                            alert('inside UserActive');
                            DRAGDIS.storage.set("UserActive", {
                                Active: $rootScope.$$childHead.user.active,
                                Username: $rootScope.$$childHead.user.username,
                                Avatar: DRAGDIS.extensionFileUrl("images/default_avatar.gif")
                            });
                        }
                    }
                });
            });
        }
    };
}]);
dragdisSidebarDirectives.directive('stopPropagation', function () {
    return {
        restrict: 'A',
        link: function (scope, element) {
            element[0].addEventListener("click", function (e) {
                e.stopPropagation();
            });
        }
    };
});
dragdisSidebarDirectives.directive('preventClick', function () {
    return {
        restrict: 'A',
        link: function (scope, element) {
            element[0].addEventListener("click", function (e) {
                e.preventDefault();
            });
        }
    };
});
dragdisSidebarDirectives.directive('autoFocus', function () {
    return {
        restrict: 'A',
        link: function (scope, element) {
            element.focus();
        }
    };
});
dragdisSidebarDirectives.directive('selectOnClick', function () {
    return {
        restrict: 'A',
        link: function (scope, element) {
            element.bind('click', function () {
                this.select();
            });
        }
    };
});
dragdisSidebarDirectives.directive('toggleMenu', function () {
    return {
        restrict: 'A',
        link: function (scope, element) {
            element.mouseenter(function () {
                element.click(function () {
                    var status = JSON.parse(JSON.stringify(scope.menu.active));

                    scope.resetSidebar();

                    scope.menu.active = !status;

                    if (!scope.$$phase) {
                        scope.$apply();
                    }
                });

                $(document).click(function (e) {
                    if (scope.menu.active && !$(e.target).closest(".user").length) {

                        scope.menu.active = false;
                        if (!scope.$$phase) {
                            scope.$apply();
                        }
                    }
                });

                $(this).unbind("mouseenter");
            });
        }
    };
});
dragdisSidebarDirectives.directive('copyingToClipboard', ['$timeout', '$compile', 'dataService', 'copyingToClipboardFactory', function ($timeout, $compile, dataService, copyingToClipboardFactory) {
    var clipboardSuccessBlock = "<div class='clipboard-success'>Link copied</div>";
    var clipboardWithoutExtensionBlock = "<div class='folder-share variant'><h3>Share this folder?</h3><div class='form-controls'><input type='text' value='" + DRAGDIS.config.domain + "' readonly='readonly' select-on-click /><a href='#' class='cancel' ng-click='removeShareBlock($event)' stop-propagation>Cancel</a></div><scroll-helper type='shareBlock'></scroll-helper></div>";

    return {
        restrict: 'A',
        link: function (scope, element, attr) {



            var shareScope;

            scope.renderShareBlock = function (shortUrl) {
                //when user not have extension

                //positions block by icons
                var blockPosition = null;
                if (scope.folder.Type == 0 || (scope.folder.Type == 1 && scope.folder.IsLink)) {
                    blockPosition = "variant2";
                } else if (scope.folder.Type == 0 && scope.folder.IsLink) {
                    blockPosition = "variant3";
                }

                shareScope = scope.$new();
                var compiledDirective = $compile(clipboardWithoutExtensionBlock.replace(DRAGDIS.config.domain, shortUrl).replace("variant", blockPosition));
                var directiveElement = compiledDirective(shareScope);

                element.append(directiveElement);

                dataService.updateScroll();

                if (!scope.$$phase) {
                    scope.$apply();
                }

            };

            scope.continueCopyProcess = function (shortUrl, isItem) {

                if (attr.clipboardCollaboration && attr.clipboardCollaboration === "true" ||
                attr.clipboardMorefolders && attr.clipboardMorefolders === "true") {
                    element.addClass("copy-success");

                    $timeout(function () {
                        element.removeClass("copy-success");
                        scope.clipboardIsBusy = false;
                    }, 1200);

                    return;
                }

                var successBlock = $(clipboardSuccessBlock);

                if (isItem) {
                    successBlock.addClass("position-right");
                }

                var tooltip = element.find(".tooltip");
                tooltip.addClass("hidden");

                element.hover(function (event) {
                    $(this).off(event);
                    tooltip.removeClass("hidden");
                });

                element.append(successBlock);

                $timeout(function () {
                    successBlock.addClass("fadeOut");

                    $timeout(function () {
                        successBlock.remove();
                    }, 500);
                }, 1200);

                scope.clipboardIsBusy = false;
            };


            scope.copyUrl = function (shortUrl, isItem) {
                new TrackEvent("Sidebar", "Copy URL to clipboard", attr.copyingToClipboard).send();

                if (DRAGDIS.config.isExtension) {
                    DRAGDIS.sendMessage({
                        Type: "CLIPBOARD_COPY",
                        Value: shortUrl,
                    });

                    scope.continueCopyProcess(shortUrl, isItem);
                } else {
                    //send url to extension clipboard
                    if (scope.extensionInstalled) {
                        var copyCompleteCheck = false;
                        var copyComplete = function () {
                            copyCompleteCheck = true;
                            document.removeEventListener("copyComplete", copyComplete);
                        };

                        document.addEventListener('copyComplete', copyComplete, false);

                        document.dispatchEvent(new CustomEvent('copyToClipboard', {
                            detail: {
                                textToCopy: shortUrl
                            }
                        }));

                        $timeout(function () {
                            if (!copyCompleteCheck) {
                                scope.renderShareBlock(shortUrl);
                            } else {
                                scope.continueCopyProcess(shortUrl, isItem);
                            }
                        }, 100);

                    } else {
                        scope.renderShareBlock(shortUrl);
                    }
                }
            };

            element.bind('click', function () {
                if (scope.clipboardIsBusy) {
                    return;
                }
                scope.clipboardIsBusy = true;

                if (attr.clipboardFolder && attr.clipboardFolder === "true") {
                    if (!scope.elementShortUrl) {
                        scope.folder_GetSharingLinks(scope.folder);
                    } else {
                        scope.copyUrl(scope.elementShortUrl, false);

                    }
                } else if (attr.clipboardCollaboration && attr.clipboardCollaboration === "true") {
                    scope.copyUrl(scope.collaborativeFolderLink, true);
                } else if (attr.clipboardMorefolders && attr.clipboardMorefolders === "true") {
                    scope.copyUrl(attr.copyingToClipboard, false);
                } else {
                    if (!scope.elementShortUrl) {
                        scope.getLastItemUrl(scope.folder);
                    } else {
                        scope.copyUrl(scope.elementShortUrl, true);
                    }
                }
            });

            //remove share block popup
            scope.removeShareBlock = function ($event) {
                scope.clipboardIsBusy = false;

                shareScope.$destroy();
                $($event.currentTarget).parents(".folder-share").remove();

                dataService.updateScroll();
            };
        },
        controller: ['$scope', function ($scope) {

            $scope.elementShortUrl = "";

            $scope.clipboardIsBusy = false;

            $scope.folder_GetSharingLinks = function (folder) {
                DRAGDIS.api("FolderGetSharingLinks", folder).then(function (response) {

                    $scope.elementShortUrl = DRAGDIS.config.shortUrlDomain + response.ShareUrl;

                    $scope.copyUrl($scope.elementShortUrl, false);

                }, function (error) {
                    console.error("clipboard error", error);

                    $scope.clipboardIsBusy = false;
                });
            };

            $scope.getLastItemUrl = function (folder) {

                DRAGDIS.api("GetLastItemUrl", folder).then(function (response) {

                    console.log(response);

                    $scope.elementShortUrl = response.ShareUrl;

                    $scope.copyUrl($scope.elementShortUrl, true);

                }, function (error) {
                    console.error("clipboard error", error);

                    $scope.clipboardIsBusy = false;
                });
            };

            var factory = new copyingToClipboardFactory($scope);
            angular.extend($scope, factory);
        }]
    };
}]);
dragdisSidebarDirectives.directive('customScrollbar', ['dataService', '$timeout', '$rootScope', function (dataService, $timeout, $rootScope) {
    return {
        restrict: 'A',
        link: function (scope, element) {

            $rootScope.foldersBlockElement = element; //#DRAGDIS_folders
            $rootScope.foldersListElement = element.children("ul"); //ul.folders-list

            scope.scroll = scope.scroll || {};

            scope.scroll.update = function () {
                scope.$evalAsync(function () {
                    $timeout(function () {
                        scope.scroll.element.refresh();
                    });
                });
            };
            dataService.updateScroll = scope.scroll.update;

            scope.scroll.updatePosition = function () {
                DRAGDIS.storage.get("ScrollPosition", function (position) {
                    if (position >= 0) {
                        scope.$evalAsync(function () {
                            $timeout(function () {
                                scope.scroll.element.inner.scrollTop(position);
                            }, 150);
                        });

                    }
                });
            };
            scope.scroll.element = element.antiscroll({ x: false }).data('antiscroll');

            var scrollPosTimeout;
            scope.scroll.element.inner.on("scroll", function () {
                $timeout.cancel(scrollPosTimeout);
                scrollPosTimeout = $timeout(function () {
                    DRAGDIS.storage.set("ScrollPosition", this.scrollTop);
                }, 500);
            });

            scope.$on("Update_scrollPosition", function () {
                scope.scroll.updatePosition();
            });

            scope.scroll.updatePosition();
        }

    };
}]);
dragdisSidebarDirectives.directive('dragoverScroll', ['$rootScope', function ($rootScope) {
    return {
        restrict: 'A',
        link: function (scope, element) {
            //Define folder list DOM
            scope.scroll = scope.scroll || {};

            scope.scroll.foldersList = scope.scroll.foldersList || $rootScope.foldersListElement;

            if (element.hasClass("top")) {
                scope.scroll.topHandle = scope.scroll.topHandle || element;
            } else if (element.hasClass("bottom")) {
                scope.scroll.bottomHandle = scope.scroll.bottomHandle || element;
            }

            scope.scroll.showScrollHandles = function () {
                this.scrollTop = this.foldersList.scrollTop();

                var droppableSettings = {
                    hoverClass: "hover",
                    tolerance: "pointer",
                    accept: ".draglist li, li, .folder, .thumbnail, .text-container, .title",
                    out: function () {
                        scope.scroll.stopScrollAnimaiton();
                    }
                };

                //Show TOP scroll handler
                if (this.scrollTop === 0) {

                    this.topHandle.height(0);

                    if (this.topHandle.hasClass('ui-droppable')) {
                        this.topHandle.droppable('destroy');
                    }

                } else {

                    this.topHandle.height(105);

                    droppableSettings.over = function () {
                        scope.scroll.animateScroll(true);
                    };

                    this.topHandle.droppable(droppableSettings);

                    this.topHandle.on("dragenter", function () {
                        scope.scroll.animateScroll(true);
                    });

                    this.topHandle.on("dragleave", function () {
                        scope.scroll.stopScrollAnimaiton();
                    });

                }

                //Show BOTTOM scroll handler
                if (this.scrollTop + this.foldersList.innerHeight() >= this.foldersList[0].scrollHeight) {

                    this.bottomHandle.height(0);

                    if (this.bottomHandle.hasClass('ui-droppable')) {
                        this.bottomHandle.droppable('destroy');
                    }

                } else {
                    this.bottomHandle.height(105);

                    droppableSettings.over = function () {
                        scope.scroll.animateScroll(false);
                    };

                    this.bottomHandle.droppable(droppableSettings);

                    this.bottomHandle.on("dragenter", function () {
                        scope.scroll.animateScroll(false);
                    });

                    this.bottomHandle.on("dragleave", function () {
                        scope.scroll.stopScrollAnimaiton();
                    });

                }
            };

            scope.scroll.hideScrollHandles = function () {
                //Destroy droppable UI widgets
                if (this.topHandle.hasClass('ui-droppable')) {
                    this.topHandle.droppable('destroy');
                }

                if (this.bottomHandle.hasClass('ui-droppable')) {
                    this.bottomHandle.droppable('destroy');
                }

                //Hide scroll handles
                this.topHandle.height(0);
                this.bottomHandle.height(0);

                //Remove native dragging elements
                this.topHandle.off('dragenter dragleave');
                this.bottomHandle.off('dragenter dragleave');

                //Enable native scroll
                //scope.Scroll.FoldersList.css('overflow-y', 'scroll');
            };

            scope.scroll.animateScroll = function (direction) {

                //****************************
                //TODO: need prevent throttle on scroll
                //****************************

                //If direction is TRUE then move to TOP, otherwise to BOTTOM
                var folderListDom = this.foldersList[0];
                var moveTo = (direction) ? 0 : folderListDom.scrollHeight - folderListDom.offsetHeight;
                var scroll = this;
                var scrollDistance = (direction) ? folderListDom.scrollTop : folderListDom.scrollHeight - folderListDom.scrollTop - folderListDom.clientHeight;
                var scrollVelocity = Math.round(scrollDistance / folderListDom.clientHeight * 1000);

                this.foldersList.animate({
                    scrollTop: moveTo
                }, {
                    duration: scrollVelocity,
                    easing: "linear",
                    progress: function () {
                        scroll.showScrollHandles();
                    }
                });
            };

            scope.scroll.stopScrollAnimaiton = function () {
                this.foldersList.stop(true);
            };
        }
    };
}]);
dragdisSidebarDirectives.directive('sortableList', ['sortableListFactory', '$timeout', '$rootScope', function (sortableListFactory, $timeout, $rootScope) {

    //#region Options
    var defaultOptions = {
        axis: "y",
        cursor: "move",
        'ui-floating': false,
        opacity: 0.8,
        delay: 120,
        scroll: true,
        scrollSensitivity: 10,
        refreshPositions: true,
        //handle: ".folder-title"
    };
    //#endregion

    return {
        restrict: 'A',
        link: function (scope, element, attrs) {
            //#region groupSortableOptions
            var groupsSortableOptions = {
                start: function (e, ui) {
                    scope.resetSidebar();
                    scope.sortablePreventClick = true;
                },
                update: function () {
                    //this function must be for dropindex initialization, no matter if is empty
                },
                stop: function (e, ui) {

                    var itemSortable = ui.item.sortable;
                    var groupModel = itemSortable.model;
                    var toPos = itemSortable.dropindex;
                    var fromPos = itemSortable.index;

                    DRAGDIS.api("Move", {fromPos: fromPos, toPos: toPos}, function () { });
                    //
                    //if (!$scope.$$phase) {
                    //    $scope.$apply();
                    //}
                    //scope.scroll.hideScrollHandles();

                    $timeout(function () {
                        scope.sortablePreventClick = false;
                    });
                }
            };
            scope.groupsSortableOptions = $.extend({}, defaultOptions, groupsSortableOptions);
            //#endregion

        },
        controller: ['$scope', function ($scope) {
        }]
    };
}]);
dragdisSidebarDirectives.directive('scrollHelper', ['$timeout', '$rootScope', function ($timeout, $rootScope) {

    var parent = $rootScope.foldersListElement;

    var types = {
        actionsBlock: 50,
        item: 270,
        folder: 270,
        shareBlock: 130
    };

    function isElementVisible(el) {
        var eap,
            rect = el.getBoundingClientRect(),
            docEl = document.documentElement,
            vWidth = window.innerWidth || docEl.clientWidth,
            vHeight = window.innerHeight || docEl.clientHeight,
            efp = function (x, y) { return document.elementFromPoint(x, y); },
            contains = "contains" in el ? "contains" : "compareDocumentPosition",
            has = contains == "contains" ? 1 : 0x14;

        // Return false if it's not in the viewport
        if (rect.right < 0 || rect.bottom < 0
                || rect.left > vWidth || rect.top > vHeight)
            return false;

        // Return true if any of its four corners are visible
        return (
              (eap = efp(rect.left, rect.top)) == el || el[contains](eap) == has
          || (eap = efp(rect.right, rect.top)) == el || el[contains](eap) == has
          || (eap = efp(rect.right, rect.bottom)) == el || el[contains](eap) == has
          || (eap = efp(rect.left, rect.bottom)) == el || el[contains](eap) == has
        );
    }

    return {
        restrict: 'E',
        replace: true,
        template: "<span class='scroll-helper'></span>",
        link: function (scope, element, attrs) {
            var type = attrs.type;

            //prevent scroll on bottom newFolder
            if (!scope.folder) {
                element.remove();
                return true;
            }

            //prevent bottom slide after drop success right now
            if (scope.folder.dragStatus >= 2 && type == "actionsBlock") {
                element.remove();
            } else {

                $timeout(function () {
                    if ($(document).find(element).length) {
                        var isVisible = isElementVisible(element[0]);
                        if (!isVisible) {

                            if (types[type]) {
                                parent.scrollTo('+=' + types[type] + 'px', 500, {
                                    onAfter: function () {
                                        element.remove();
                                    }
                                });
                            } else {
                                if (scope.folder.isNewFolder) {
                                    //new folder scroll to bottom
                                    parent.scrollTo("100%", 500);
                                } else {
                                    parent.scrollTo(element, 500, {
                                        onAfter: function () {
                                            element.remove();
                                        }
                                    });
                                }
                            }
                        } else {
                            element.remove();
                        }
                    }
                }, 500);
            }
        }
    };
}]);
dragdisSidebarDirectives.directive('tooltipHover', ['$rootScope', function ($rootScope) {
    var foldersBlockElement;
    return {
        restrict: 'A',
        link: function (scope, element) {
            element.hover(function () {

                if (!foldersBlockElement) {
                    foldersBlockElement = $rootScope.foldersBlockElement;
                }

                var documentOffset = foldersBlockElement.offset().top;
                var hoverPosition = $(this).offset().top - documentOffset;

                if (hoverPosition < 60) {
                    $(this).find(".tooltip").addClass("repositioned");
                } else {
                    $(this).find(".tooltip").removeClass("repositioned");
                }
            });
        }
    };
}]);
dragdisSidebarDirectives.directive('blockMove', ['$timeout', function ($timeout) {
    return {
        restrict: 'A',
        link: function (scope, element) {
            var defaultTop = 60;

            scope.$on("block-move-up", function (e, data) {
                if (data.level > 0) {
                    scope.blockMoveUp = true;
                    $timeout(function () {
                        element.css({ top: defaultTop - data.level });
                    });

                } else {
                    scope.blockMoveUp = false;
                    element.css({ top: defaultTop });
                }
            });
        }
    };
}]);
//prevent outside scroll
dragdisSidebarDirectives.directive('scrollPreventer', ['$timeout', function ($timeout) {
    return {
        restrict: 'A',
        link: function (scope, element) {
            //prevent window scroll
            DRAGDIS.scrollPreventer = [];

            $timeout(function () {
                element.bind('mousewheel', function (e, direction) {
                    var target = $(e.target).parents(".folders");

                    if (!target.length) {
                        e.preventDefault();
                    } else if (target) {
                        var lastGroup = target.children("ul").children("li").last();

                        var groupsHeight = lastGroup.height() + lastGroup.position().top;
                        //if ul.folders is smaller then container size
                        if (target.height() > groupsHeight) {
                            e.preventDefault();
                        }
                    }
                });

                $(window).scroll(function () {
                    if (DRAGDIS.Drag.IsOnSidebar && DRAGDIS.scrollPreventer.position) {
                        $(window).scrollTop(DRAGDIS.scrollPreventer.position);
                    }
                });
            }, 2000);
        }
    };
}]);
dragdisSidebarDirectives.directive('keyUp', function () {
    return {
        restrict: "A",
        link: function (scope, element, attr) {
            element.bind('keyup', function (e) {
                switch (e.which) {
                    case 13:
                        if (attr.keyUpEnter) {
                            scope.$apply(function (s) {
                                s.$eval(attr.keyUpEnter);
                            });
                        }
                        break;
                    case 27:
                        if (attr.keyUpEsc) {
                            scope.$apply(function (s) {
                                s.$eval(attr.keyUpEsc);
                            });
                        }
                        break;
                }
            });
        }
    };
});
dragdisSidebarDirectives.directive('noAnimate', ['$animate', function ($animate) {
    return {
        restrict: "A",
        link: function (scope, element, attr) {
            $animate.enabled(false, element);
            scope.$watch(function () {
                $animate.enabled(false, element);
            });
        }
    };
}]);
dragdisSidebarDirectives.directive('renderComplete', ['$timeout', function ($timeout) {
    return {
        restrict: "A",
        link: function (scope, element) {
            var unbindWatch = scope.$watch("active", function (value) {
                if (value) {
                    $timeout(function () {
                        element.removeClass("first-time");
                        scope.renderComplete = true;

                        if (!scope.$$phase) {
                            scope.$apply();
                        }

                        unbindWatch();
                    }, 1000);
                }
            });
        }
    };
}]);

dragdisSidebarDirectives.directive('timeAgo', ['$timeout', function($timeout) {
    return {
        restrinct: "A",
        link: function(scope, element) {
            element = element[0];
            var templates = {
                prefix: "",
                suffix: " назад",
                seconds: "менее минуты",
                minute: "около минуты",
                minutes: "%d минут",
                hour: "около часа",
                hours: "около %d часов",
                day: "день",
                days: "%d дней",
                month: "около месяца",
                months: "%d месяцев",
                year: "около года",
                years: "%d года"
            };
            var template = function (t, n) {
                return templates[t] && templates[t].replace(/%d/i, Math.abs(Math.round(n)));
            };

            var timer = function (time) {
                if (!time) return;
                time = parseInt(time);

                time = new Date(time);

                var now = new Date();
                var seconds = ((now.getTime() - time) * .001) >> 0;
                var minutes = seconds / 60;
                var hours = minutes / 60;
                var days = hours / 24;
                var years = days / 365;

                return templates.prefix + (
                    seconds < 45 && template('seconds', seconds) || seconds < 90 && template('minute', 1) || minutes < 45 && template('minutes', minutes) || minutes < 90 && template('hour', 1) || hours < 24 && template('hours', hours) || hours < 42 && template('day', 1) || days < 30 && template('days', days) || days < 45 && template('month', 1) || days < 365 && template('months', days / 30) || years < 1.5 && template('year', 1) || template('years', years)) + templates.suffix;
            };

            //element.innerHTML = timer(element.getAttribute('title') || element.getAttribute('datetime'));

            $timeout(function() {
                element.innerHTML = timer(element.getAttribute('title') || element.getAttribute('datetime'));
            }, 300);


        }
    }
}]);

'use strict';

KNUGGET.Drag = {
    IsOnSidebar: false,
    Context: null,
    Data: {},
    Clicked: null
};

var KNUGGET_EVENTS = {
    selectors: {
        D: "img,a,p,span,h1,h2,h3,h4,h5,h6,sub,sup,ul,li,div,dd,dt,td,table,article,aside",
        Y: "object[data*='//www.youtube.com/v/'], iframe[src*='//www.youtube.com/embed/'], embed[src*='//s.ytimg.com/yts/swfbin/watch'],embed[src*='//s.ytimg.com/yts/swfbin/player'], video[data-youtube-id], embed[src*='//www.youtube.com/v/'],.html5-video-container, iframe[src*='https://attachment.fbsbx.com/external_iframe.php']",
        V: "iframe[src*='//player.vimeo.com/video/'], object[data*='vimeocdn.com/p/flash'], video[src*='//player.vimeo.com/play_redirect'], video[src*='//av.vimeo.com/'], div.vimeo_holder, div.player[data-fallback-url*='//player.vimeo.com/v2'], iframe[src*='https://attachment.fbsbx.com/external_iframe.php'], .player.with-fullscreen[data-fallback-url*='//player.vimeo.com/']",
        VideoBlock: "KNUGGET_VIDEO",
        VideoIcon: "KNUGGET_VIDEO_ICON",
        FoldersList: ".KNUGGET_FOLDERS_LIST"
    },

    //blacklist of disabled elements when drag never open sidebar
    selectorIsDisabled: function (target) {
        if (($(target).hasClass("ytp-volume-slider") || $(target).hasClass("html5-progress-bar")) && (target).parents(".html5-video-player").length) {
            return true;
        }

        return false;
    },
    //on website
    dragStart: function (event, iframeEvent) {

        if (iframeEvent) {
            event = iframeEvent;
        }

        event.stopPropagation();
        event.originalEvent.dataTransfer.effectAllowed = "all";

        if (KNUGGET_EVENTS.selectorIsDisabled(event.target)) return false;

        if (KNUGGET_SIDEBAR.dragActive) {
            return true;
        }

        KNUGGET_SIDEBAR.dragActive = true;
        document.dispatchEvent(new CustomEvent('knuggetDragStart'));

        KNUGGET_SIDEBAR.show();

        KNUGGET.Drag = {
            Target: $(event.target),
            Context: event.view,
            Data: {}
        };
        

        /*=================================================
        =            FACEBOOK GHOST EXPERIMENT            =
        =================================================*/  
        var whitelist = [
            'facebook',
            'pinterest',
            'dribbble'
        ]

        if (new RegExp(whitelist.join("|")).test(window.location.host)) {

            if (KNUGGET.Drag.Target.find("img").length) {
                event.originalEvent.dataTransfer.setDragImage(KNUGGET.Drag.Target[0], 0, 0);
            } 

            if (KNUGGET.Drag.Target[0].classList.contains('dribbble-over')) {
                event.originalEvent.dataTransfer.setDragImage(KNUGGET.Drag.Target.parent().find("img")[0], 0, 0);
            }
        }

        return true;
    },


    dragEnter: function (event, iframeEvent) {

        // Return if dragging already started, otherwise set DragActive flag
        if (KNUGGET_SIDEBAR.dragActive) {
            return true;
        } else {
            KNUGGET_SIDEBAR.dragActive = true;
        }

        //If event originated in iframe, pass it to higher tier window context (bubble through multiple iframes to the top window)
        if (iframeEvent) {
            event = iframeEvent;
        }

        //Dispatch event for demo pages
        document.dispatchEvent(new CustomEvent('knuggetDragStart'));

        if (KNUGGET.sidebarController != null) {
            KNUGGET.sidebarController.resetSidebar();
        }

        var effectAllowed = 0;
        var enableFileUpload = false;
        var dataTransferTypes = event.originalEvent.dataTransfer.types;
        var isFileAttached = $.inArray("Files", dataTransferTypes) !== -1;


        if (isFileAttached && enableFileUpload) {
            effectAllowed = 1;
        } else if ($.inArray("text/uri-list", dataTransferTypes) !== -1) {
            effectAllowed = 1;
        }

        if (effectAllowed) {

            event.originalEvent.preventDefault();
            event.originalEvent.stopPropagation();

            //If content is dragged
            if (!isFileAttached) {

                KNUGGET.screenSnapshot();

                KNUGGET.Drag = {
                    Target: $("<div></div>"),
                    Folder: 0,
                    Data: {}
                };

                KNUGGET.Drag.Data.Href = window.location.href;
                KNUGGET.Drag.Data.Title = document.title;

                var lastString = window.location.href.substring(window.location.href.length - Math.min(4, window.location.href.length));
                if (lastString === ".jpg" || lastString === ".gif" || lastString === ".png") {
                    KNUGGET.Drag.Data.Type = "picture";
                    KNUGGET.Drag.Target = $("<img src='" + window.location.href + "'/>");
                } else {
                    KNUGGET.Drag.Data.Type = "address_bar";
                }

                //If file is dragged
            } else {
                KNUGGET.Drag = {
                    Target: $("<img/>"),
                    Folder: 0,
                    Data: {
                        Type: "picture",
                    }
                };
            }

            KNUGGET_SIDEBAR.show();
        }
        return true;
    },

    dragEnd: function (event, iframeEvent) {

        if (KNUGGET_SIDEBAR.isSidebarElm($(event.target)) || KNUGGET_SIDEBAR.dragActive === false || (KNUGGET.Drag && KNUGGET.Drag.IsOnSidebar)) {
            return true;
        }

        KNUGGET_SIDEBAR.dragActive = false;
        document.dispatchEvent(new CustomEvent('knuggetDragEnd'));

        event.stopPropagation();

        KNUGGET.sidebarController.dragEnd();
        KNUGGET.sidebarController.hide();

        //VideoIcon hide after dragend
        $("#" + KNUGGET_EVENTS.selectors.VideoIcon).remove();

        return true;
    },

    dragLeave: function (event, iframeEvent) {

        if (event.target.tagName === "HTML") {

            //event.originalEvent.dataTransfer.setData("knugget", "aaaaa");

            if (KNUGGET.Drag.Target) {
                KNUGGET.Drag.Target.knuggetCollection(function (data) {
                    //console.log(event);
                });
            }
        };


        if (iframeEvent) {
            event = iframeEvent;
        }

        var dragType = KNUGGET.Drag && KNUGGET.Drag.Data && KNUGGET.Drag.Data.Type ? KNUGGET.Drag.Data.Type : "";

        if (dragType == "address_bar" && event.originalEvent.clientX === 0 && event.originalEvent.clientY === 0) {

            if (KNUGGET_SIDEBAR.dragActive === false) {
                return true;
            }

            KNUGGET_SIDEBAR.dragActive = false;
            document.dispatchEvent(new CustomEvent('knuggetDragEnd'));

            event.preventDefault();
            event.stopPropagation();

            KNUGGET.sidebarController.dragEnd();
            KNUGGET.sidebarController.hide();
        }
        return true;
    },

    //on sidebar
    mouseEnter: function () {
        KNUGGET.mouseIsOnSidebar = true;
    },

    mouseLeave: function () {
        KNUGGET.mouseIsOnSidebar = false;
    },

    //on video players
    videoMouseEnter: function (event) {
        if ($(event.relatedTarget).is("#" + KNUGGET_EVENTS.selectors.VideoIcon)) {
            return true;
        }

        if (KNUGGET.FullScreenMode) {
            $("#" + KNUGGET_EVENTS.selectors.VideoIcon).remove();
            return false;
        }

        var $knuggetVideoPlayer = $(event.relatedTarget).parents(".knugget_video_player");

        if ($(event.relatedTarget).attr("src") && $(event.target).attr("src") && $(event.target).attr("src").indexOf("https://attachment.fbsbx.com/external_iframe.php") > -1 && !$knuggetVideoPlayer) {
            return false;
        }

        //FB: prevent both youtube and vimeo running with same iframe.php selector
        if (window.location.href.indexOf("://www.facebook.com") > -1 && $(event.target).parents(".knugget_video_player").attr("data-knuggetVideoProvider") !== event.data.provider) {
            return false;
        }

        $(this).videoDragSquare(event);
    },

    videoMouseLeave: function (event) {
        if ($(event.relatedTarget).is("#" + KNUGGET_EVENTS.selectors.VideoIcon) ||
            ($(event.relatedTarget).is(".html5-video-container") && window.location.href.indexOf("//www.youtube.com/") !== -1)) { //this line prevent hide icon when video is smaller then html5 container
            return true;
        }


        $("#" + KNUGGET_EVENTS.selectors.VideoIcon).remove();
    },

    videoIconMouseEnter: function () {
        $(this).show();
    },

    mouseLeaveWindow: function (event) {
        if (event.toElement === null && event.relatedTarget === null && $("#" + KNUGGET_SIDEBAR_NAME).length) {
            KNUGGET.sidebarController.hide();
        }
    },

    //Drag from google images iframes
    iframeEvents: function () {

        if ($(this).attr("src").indexOf("https://attachment.fbsbx.com/external_iframe.php") > -1) {
            return false;
        }

        $(this).addClass("KNUGGET_iframe").contents().find(KNUGGET_EVENTS.selectors.D).not(".KNUGGET_iframe")
            .bind("dragstart", KNUGGET_EVENTS.dragStart)
            .bind("dragend", KNUGGET_EVENTS.dragEnd);
    }
};

var KNUGGET_CLASS = "knugget";

$(document)
    // Put function execute into onload area
    .ready(function () {
        $(document).on('contextmenu', function(ev) {
            KNUGGET.Drag.Clicked = $(ev.target);
            //ev.preventDefault();
            return true;
        });

        //KNUGGET_SIDEBAR.injectSidebebar();
        //KNUGGET.sidebarController.hide(true, true);

        //force injection of sidebar


        var injectTimer = setInterval(function() {
            try {
                console.log("injecting");
                KNUGGET_SIDEBAR.injectSidebebar();
                console.log("no exception!");
                console.log(KNUGGET.sidebarController);
                if (KNUGGET.sidebarController && KNUGGET.sidebarController.hide) {
                    KNUGGET.sidebarController.hide(true, true);
                    clearInterval(injectTimer);
                }
            } catch (e) {
                console.log(new Date().getTime());
                console.log(e);
            }

        }, 500);

        //var injectTimer = setInterval(function() {
        //    try {
        //
        //        console.log(new Date().getTime());
        //        console.log('injecting');
        //        KNUGGET_SIDEBAR.injectSidebebar();
        //        if (KNUGGET.sidebarController && KNUGGET.sidebarController.hide) {
        //            KNUGGET.sidebarController.hide(true, true);
        //        } else {
        //            console.log(new Date().getTime());
        //            console.log("reinject");
        //            var sidebarTimer = setInterval(function () {
        //                console.log(new Date().getTime());
        //                console.log("reinjecting");
        //                if (KNUGGET.sidebarController) {
        //                    KNUGGET.sidebarController.hide(true, true);
        //                    clearInterval(sidebarTimer);
        //                }
        //            }, 1000);
        //        }
        //        clearInterval(injectTimer);
        //    } catch (e) {
        //        console.log(new Date().getTime());
        //        console.log(e);
        //    }
        //}, 1550);


        if (window.location.href.indexOf("//www.youtube.com/") === -1 && window.location.href.indexOf("&list=") === -1) {
            if ($.fn.fixFlash !== undefined) {
                $(this).fixFlash();
            }
        }

        ////FB drag video
        //if (window.location.href.indexOf("//www.facebook.com/") > -1) {
        //    $(document).on("mouseenter", "a[href*='//www.youtube.com/watch?v='], a[href*='//youtu.be/'],a[href*='//vimeo.com/']", function () {
        //        if ($(this).find("._5pbd,._1y4,._6ku,._1ui2,._6o1").length) {
        //            $(this).parents().eq(2).addClass("knugget_video_player").attr("data-knuggetVideoHref", $(this).attr("href")).attr("data-knuggetVideoProvider", "youtube");
        //        }
        //    });
        //    $(document).on("mouseenter", "a[href*='//vimeo.com/']", function () {
        //        if ($(this).find("._5pbd,._1y4,._6ku,._1ui2,._6o1").length) {
        //            $(this).parents().eq(2).addClass("knugget_video_player").attr("data-knuggetVideoHref", $(this).attr("href")).attr("data-knuggetVideoProvider", "vimeo");
        //        }
        //    });
        //}
    })
    .on("dragstart", KNUGGET_EVENTS.dragStart)
    .on("dragend", KNUGGET_EVENTS.dragEnd)

    .on("dragenter", KNUGGET_EVENTS.dragEnter)
    .on("dragleave", KNUGGET_EVENTS.dragLeave)
    .on("mouseenter", "#" + KNUGGET_SIDEBAR_NAME, KNUGGET_EVENTS.mouseEnter)
    .on("mouseleave", "#" + KNUGGET_SIDEBAR_NAME, KNUGGET_EVENTS.mouseLeave);

    ////:>> YOUTUBE VIDEO ICON EMBED HOVER
    //.on("mouseenter", KNUGGET_EVENTS.selectors.Y, { provider: 'youtube' }, KNUGGET_EVENTS.videoMouseEnter)
    ////VIMEO VIDEO EMBED HOVER
    //.on("mouseenter", KNUGGET_EVENTS.selectors.V, { provider: 'vimeo' }, KNUGGET_EVENTS.videoMouseEnter)
    //.on("mouseleave", KNUGGET_EVENTS.selectors.Y + "," + KNUGGET_EVENTS.selectors.V, KNUGGET_EVENTS.videoMouseLeave)
    ////Hide .KNUGGET_video_block
    //.on("mouseenter", KNUGGET_EVENTS.selectors.VideoIcon, KNUGGET_EVENTS.videoIconMouseEnter);


if (window.location.host === "www.youtube.com") {
    $(document).on('DOMNodeInserted', function (e) {
        if (e.target.id == 'progress') {
            $("." + KNUGGET_EVENTS.selectors.VideoBlock).removeClass(KNUGGET_EVENTS.selectors.VideoBlock);
        }
    });
}

window.addEventListener("focus", function () {
    KNUGGET.storage.get("IsConnected", function (userConnected) {
        if (userConnected) {
            setTimeout(function () {
                //todo place code here
            }, 1000);
        }
    });
}, false);

//IFRAME DRAG&DROP
$(document).on("mouseenter", "iframe:not('.KNUGGET_iframe')", KNUGGET_EVENTS.iframeEvents);


//HTML5 fullscreen  video_icons display: none
KNUGGET.FullScreenMode = false;
$(document).on('webkitfullscreenchange mozfullscreenchange fullscreenchange msfullscreenchange', function (e) {

    var fullscreenState = document.fullScreen || document.mozFullScreen || document.webkitIsFullScreen;

    KNUGGET.FullScreenMode = fullscreenState ? true : false;

    $("#" + KNUGGET_EVENTS.selectors.VideoIcon).remove();
});

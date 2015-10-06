'use strict';

DRAGDIS.Drag = {
    IsOnSidebar: false,
    Context: null,
    Data: {}
};

var DRAGDIS_EVENTS = {
    selectors: {
        D: "img,a,p,span,h1,h2,h3,h4,h5,h6,sub,sup,ul,li,div,dd,dt,td,table,article,aside",
        Y: "object[data*='//www.youtube.com/v/'], iframe[src*='//www.youtube.com/embed/'], embed[src*='//s.ytimg.com/yts/swfbin/watch'],embed[src*='//s.ytimg.com/yts/swfbin/player'], video[data-youtube-id], embed[src*='//www.youtube.com/v/'],.html5-video-container, iframe[src*='https://attachment.fbsbx.com/external_iframe.php']",
        V: "iframe[src*='//player.vimeo.com/video/'], object[data*='vimeocdn.com/p/flash'], video[src*='//player.vimeo.com/play_redirect'], video[src*='//av.vimeo.com/'], div.vimeo_holder, div.player[data-fallback-url*='//player.vimeo.com/v2'], iframe[src*='https://attachment.fbsbx.com/external_iframe.php'], .player.with-fullscreen[data-fallback-url*='//player.vimeo.com/']",
        VideoBlock: "DRAGDIS_VIDEO",
        VideoIcon: "DRAGDIS_VIDEO_ICON",
        FoldersList: ".DRAGDIS_FOLDERS_LIST"
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

        if (DRAGDIS_EVENTS.selectorIsDisabled(event.target)) return false;

        if (DRAGDIS_SIDEBAR.dragActive) {
            return true;
        }

        DRAGDIS_SIDEBAR.dragActive = true;
        document.dispatchEvent(new CustomEvent('dragdisDragStart'));

        new TrackEvent("Sidebar", "Dragging started").send();

        DRAGDIS_SIDEBAR.show();

        DRAGDIS.Drag = {
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

            if (DRAGDIS.Drag.Target.find("img").length) {
                event.originalEvent.dataTransfer.setDragImage(DRAGDIS.Drag.Target[0], 0, 0);
            } 

            if (DRAGDIS.Drag.Target[0].classList.contains('dribbble-over')) {
                event.originalEvent.dataTransfer.setDragImage(DRAGDIS.Drag.Target.parent().find("img")[0], 0, 0);
            }
        }

        return true;
    },


    dragEnter: function (event, iframeEvent) {

        // Return if dragging already started, otherwise set DragActive flag
        if (DRAGDIS_SIDEBAR.dragActive) {
            return true;
        } else {
            DRAGDIS_SIDEBAR.dragActive = true;
        }

        //If event originated in iframe, pass it to higher tier window context (bubble through multiple iframes to the top window)
        if (iframeEvent) {
            event = iframeEvent;
        }

        //Dispatch event for demo pages
        document.dispatchEvent(new CustomEvent('dragdisDragStart'));

        if (DRAGDIS.sidebarController != null) {
            DRAGDIS.sidebarController.resetSidebar();
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

            new TrackEvent("Sidebar", "Dragging started").send();

            //If content is dragged
            if (!isFileAttached) {

                DRAGDIS.screenSnapshot();

                DRAGDIS.Drag = {
                    Target: $("<div></div>"),
                    Folder: 0,
                    Data: {}
                };

                DRAGDIS.Drag.Data.Href = window.location.href;
                DRAGDIS.Drag.Data.Title = document.title;

                var lastString = window.location.href.substring(window.location.href.length - Math.min(4, window.location.href.length));
                if (lastString === ".jpg" || lastString === ".gif" || lastString === ".png") {
                    DRAGDIS.Drag.Data.Type = "picture";
                    DRAGDIS.Drag.Target = $("<img src='" + window.location.href + "'/>");
                } else {
                    DRAGDIS.Drag.Data.Type = "address_bar";
                }

                //If file is dragged
            } else {
                DRAGDIS.Drag = {
                    Target: $("<img/>"),
                    Folder: 0,
                    Data: {
                        Type: "picture",
                    }
                };
            }

            DRAGDIS_SIDEBAR.show();
        }
        return true;
    },

    dragEnd: function (event, iframeEvent) {

        if (DRAGDIS_SIDEBAR.isSidebarElm($(event.target)) || DRAGDIS_SIDEBAR.dragActive === false || (DRAGDIS.Drag && DRAGDIS.Drag.IsOnSidebar)) {
            return true;
        }

        new TrackEvent("Sidebar", "Dragging ended").send();

        DRAGDIS_SIDEBAR.dragActive = false;
        document.dispatchEvent(new CustomEvent('dragdisDragEnd'));

        event.stopPropagation();

        DRAGDIS.sidebarController.dragEnd();
        DRAGDIS.sidebarController.hide();

        //VideoIcon hide after dragend
        $("#" + DRAGDIS_EVENTS.selectors.VideoIcon).remove();

        return true;
    },

    dragLeave: function (event, iframeEvent) {

        if (event.target.tagName === "HTML") {

            event.originalEvent.dataTransfer.setData("dragdis", "aaaaa");

            if (DRAGDIS.Drag.Target) {
                DRAGDIS.Drag.Target.dragdisCollection(function (data) {
                    //console.log(event);
                });
            }
        };


        if (iframeEvent) {
            event = iframeEvent;
        }

        var dragType = DRAGDIS.Drag && DRAGDIS.Drag.Data && DRAGDIS.Drag.Data.Type ? DRAGDIS.Drag.Data.Type : "";

        if (dragType == "address_bar" && event.originalEvent.clientX === 0 && event.originalEvent.clientY === 0) {

            if (DRAGDIS_SIDEBAR.dragActive === false) {
                return true;
            }

            DRAGDIS_SIDEBAR.dragActive = false;
            document.dispatchEvent(new CustomEvent('dragdisDragEnd'));

            event.preventDefault();
            event.stopPropagation();

            DRAGDIS.sidebarController.dragEnd();
            DRAGDIS.sidebarController.hide();
        }
        return true;
    },

    //on sidebar
    mouseEnter: function () {
        DRAGDIS.mouseIsOnSidebar = true;
    },

    mouseLeave: function () {
        DRAGDIS.mouseIsOnSidebar = false;
    },

    //on video players
    videoMouseEnter: function (event) {
        if ($(event.relatedTarget).is("#" + DRAGDIS_EVENTS.selectors.VideoIcon)) {
            return true;
        }

        if (DRAGDIS.FullScreenMode) {
            $("#" + DRAGDIS_EVENTS.selectors.VideoIcon).remove();
            return false;
        }

        var $dragdisVideoPlayer = $(event.relatedTarget).parents(".dragdis_video_player");

        if ($(event.relatedTarget).attr("src") && $(event.target).attr("src") && $(event.target).attr("src").indexOf("https://attachment.fbsbx.com/external_iframe.php") > -1 && !$dragdisVideoPlayer) {
            return false;
        }

        //FB: prevent both youtube and vimeo running with same iframe.php selector
        if (window.location.href.indexOf("://www.facebook.com") > -1 && $(event.target).parents(".dragdis_video_player").attr("data-dragdisVideoProvider") !== event.data.provider) {
            return false;
        }

        $(this).videoDragSquare(event);
    },

    videoMouseLeave: function (event) {
        if ($(event.relatedTarget).is("#" + DRAGDIS_EVENTS.selectors.VideoIcon) ||
            ($(event.relatedTarget).is(".html5-video-container") && window.location.href.indexOf("//www.youtube.com/") !== -1)) { //this line prevent hide icon when video is smaller then html5 container
            return true;
        }


        $("#" + DRAGDIS_EVENTS.selectors.VideoIcon).remove();
    },

    videoIconMouseEnter: function () {
        $(this).show();
    },

    mouseLeaveWindow: function (event) {
        if (event.toElement === null && event.relatedTarget === null && $("#" + DRAGDIS_SIDEBAR_NAME).length) {
            DRAGDIS.sidebarController.hide();
        }
    },

    //Drag from google images iframes
    iframeEvents: function () {

        if ($(this).attr("src").indexOf("https://attachment.fbsbx.com/external_iframe.php") > -1) {
            return false;
        }

        $(this).addClass("DRAGDIS_iframe").contents().find(DRAGDIS_EVENTS.selectors.D).not(".DRAGDIS_iframe")
            .bind("dragstart", DRAGDIS_EVENTS.dragStart)
            .bind("dragend", DRAGDIS_EVENTS.dragEnd);
    }
};

var DRAGDIS_CLASS = "dragdis";

$(document)
    // Put function execute into onload area
    .ready(function () {
        if (window.location.href.indexOf("//www.youtube.com/") === -1 && window.location.href.indexOf("&list=") === -1) {
            if ($.fn.fixFlash !== undefined) {
                $(this).fixFlash();
            }
        }

        //FB drag video
        if (window.location.href.indexOf("//www.facebook.com/") > -1) {
            $(document).on("mouseenter", "a[href*='//www.youtube.com/watch?v='], a[href*='//youtu.be/'],a[href*='//vimeo.com/']", function () {
                if ($(this).find("._5pbd,._1y4,._6ku,._1ui2,._6o1").length) {
                    $(this).parents().eq(2).addClass("dragdis_video_player").attr("data-dragdisVideoHref", $(this).attr("href")).attr("data-dragdisVideoProvider", "youtube");
                }
            });
            $(document).on("mouseenter", "a[href*='//vimeo.com/']", function () {
                if ($(this).find("._5pbd,._1y4,._6ku,._1ui2,._6o1").length) {
                    $(this).parents().eq(2).addClass("dragdis_video_player").attr("data-dragdisVideoHref", $(this).attr("href")).attr("data-dragdisVideoProvider", "vimeo");
                }
            });
        }
    })
    .on("dragstart", DRAGDIS_EVENTS.dragStart)
    .on("dragend", DRAGDIS_EVENTS.dragEnd)

    .on("dragenter", DRAGDIS_EVENTS.dragEnter)
    .on("dragleave", DRAGDIS_EVENTS.dragLeave)
    .on("mouseenter", "#" + DRAGDIS_SIDEBAR_NAME, DRAGDIS_EVENTS.mouseEnter)
    .on("mouseleave", "#" + DRAGDIS_SIDEBAR_NAME, DRAGDIS_EVENTS.mouseLeave)

    //:>> YOUTUBE VIDEO ICON EMBED HOVER
    .on("mouseenter", DRAGDIS_EVENTS.selectors.Y, { provider: 'youtube' }, DRAGDIS_EVENTS.videoMouseEnter)
    //VIMEO VIDEO EMBED HOVER
    .on("mouseenter", DRAGDIS_EVENTS.selectors.V, { provider: 'vimeo' }, DRAGDIS_EVENTS.videoMouseEnter)
    .on("mouseleave", DRAGDIS_EVENTS.selectors.Y + "," + DRAGDIS_EVENTS.selectors.V, DRAGDIS_EVENTS.videoMouseLeave)
    //Hide .DRAGDIS_video_block
    .on("mouseenter", DRAGDIS_EVENTS.selectors.VideoIcon, DRAGDIS_EVENTS.videoIconMouseEnter);


if (window.location.host === "www.youtube.com") {
    $(document).on('DOMNodeInserted', function (e) {
        if (e.target.id == 'progress') {
            $("." + DRAGDIS_EVENTS.selectors.VideoBlock).removeClass(DRAGDIS_EVENTS.selectors.VideoBlock);
        }
    });
}

// Refresh SignalR connection on window focus. Required to reconnect, after socket connection was closed from server side, after client sleep/hibernate.
window.addEventListener("focus", function () {
    DRAGDIS.storage.get("IsConnected", function (userConnected) {
        if (userConnected) {
            setTimeout(function () {
                //todo
            }, 1000);
        }
    });
}, false);

//IFRAME DRAG&DROP
$(document).on("mouseenter", "iframe:not('.DRAGDIS_iframe')", DRAGDIS_EVENTS.iframeEvents);


//HTML5 fullscreen  video_icons display: none
DRAGDIS.FullScreenMode = false;
$(document).on('webkitfullscreenchange mozfullscreenchange fullscreenchange msfullscreenchange', function (e) {

    var fullscreenState = document.fullScreen || document.mozFullScreen || document.webkitIsFullScreen;

    DRAGDIS.FullScreenMode = fullscreenState ? true : false;

    $("#" + DRAGDIS_EVENTS.selectors.VideoIcon).remove();
});

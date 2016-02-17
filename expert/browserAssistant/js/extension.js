'use strict';

KNUGGET.CurrentPage = window.location.href;
KNUGGET.browserInstanceId = Math.floor((1 + Math.random()) * 0x10000000000).toString(16).substring(1);


/* ==========================================================================
Global getters / setters
========================================================================== */
KNUGGET.storage = {
    get: function (key, callback) {

        window.chrome.storage.local.get(key, function (outObj) {
            if (callback) {
                if (key === null || key instanceof Array) {
                    callback(outObj);
                } else {
                    callback(outObj[key] || "");
                }
            }
        });
    },
    set: function (key, value) {
        var obj = {};
        obj[key] = value;

        window.chrome.storage.local.set(obj);
    }
};

KNUGGET.bookmarks = {
    set: function (tree) {

        var jsonBookmarks = $(".browser_bookmarks");

        jsonBookmarks.show();
        jsonBookmarks.find("#jsonInput").val(tree);
        jsonBookmarks.find("#read_bookmarks").hide();

    },
    get: function () {
        KNUGGET.sendMessage({
            Type: "GET_BOOKMARKS"
        });
    }
};


/* ==========================================================================
Global functions
========================================================================== */
KNUGGET.backgroundListener = function (request) {

    //REVIEW: find better solution to skip iframes
    if (window.safari && window.top !== window) {
        return;
    }
    if (window.safari && request.name !== "KNUGGET_MESSAGING") {
        return;
    } //kill message if is not from knugget extension

    if (KNUGGET.sidebarController == null) {
        KNUGGET.sidebarController = {};
    }

    switch (request.Type) {
        case "SIDEBAR_SHOW":
            if (KNUGGET.sidebarController.active) {
                KNUGGET_SIDEBAR.openedByIcon = 0;
                KNUGGET.sidebarController.hide(true, true); //closeFast , isCloseManually
            } else {

                //Set flag for manual initialization (required for user stats)
                if (!KNUGGET.sidebarController.folders) {
                    KNUGGET.config.isInitializedManually = true;
                }

                KNUGGET_SIDEBAR.openedByIcon = 1;
                KNUGGET_SIDEBAR.show({ isOpenedManually: true });
            }
            break;
        case "DISPLAY_UPLOAD_ERROR":
            KNUGGET_SIDEBAR.show({
                showUploadError: true
            });
            break;
        case "KNUGGET_UPDATE_ACTIVE":
            if (KNUGGET.sidebarController != null) {
                KNUGGET.sidebarController.user.set(request.Value);
            }
            break;
        case "KNUGGET_UPDATE_FOLDERS":
            if (KNUGGET.sidebarController != null && KNUGGET.sidebarController.folders) {
                KNUGGET.sidebarController.folders.update(request.Value);
            }
            break;
        case "SIDEBAR_UPDATE_SCROLLPOSITION":
            if (KNUGGET.sidebarController != null && typeof KNUGGET.sidebarController.$broadcast == "function") {
                KNUGGET.sidebarController.$broadcast("Update_scrollPosition");
            }
            break;
        case "SET_BOOKMARKS":
            KNUGGET.bookmarks.set(request.Value);

            break;
    }
};

KNUGGET.api = function (method, data, callback) {

    var deferred = $.Deferred();
    var port = window.chrome.runtime.connect({
        name: "knugget"
    });

    data.method = method;
    data.sender = KNUGGET.browserInstanceId;

    port.postMessage(data);

    port.onMessage.addListener(function (response) {

        port.disconnect();

        if (response.status === 200) {
            deferred.resolve(response.data);
        } else {
            deferred.reject(response.data);
        }

        if (callback) {
            callback(response);
        }
    });

    return deferred.promise();
};

KNUGGET.sendMessage = function (data, callback) {
    window.chrome.runtime.sendMessage(data, callback);
};

KNUGGET.screenSnapshot = function () {
    KNUGGET.sendMessage({
        Type: "ScreenSnapshot"
    });
};


/* ==========================================================================
Sync between knugget iframe<-->extension
========================================================================== */
window.addEventListener('message', function (e) {

    var data;

    try {
        data = JSON.parse(e.data);
    } catch (e) {
        data = {};
    }

    if (e.origin.toLowerCase().indexOf(KNUGGET.config.domain.toLowerCase().slice(0, -1)) > -1) {

        if (data.type == 'KNUGGET_ExtensionIframeSync' && data.action == 'FORCE_REFRESH') {

            // Send confirmation to iframe that message has been received 
            e.source.postMessage(JSON.stringify({
                messageReceived: true
            }), '*');

            // Initialize reconnect
            KNUGGET.sendMessage({
                Type: "RECONNECT"
            });
        }
    }
}, false);


/* ==========================================================================
Extension background message listener
========================================================================== */
window.chrome.extension.onMessage.addListener(KNUGGET.backgroundListener);


//TODO: move to extension native page mod
/* ==========================================================================
Static page mod for bookmarks
========================================================================== */
$(document).on("ready", function () {
    //
    //if (window.location.href.toLowerCase().indexOf("knugget.com/account/settings/import") > -1) {
    //    KNUGGET.bookmarks.get();
    //}

});

function stripTags(str, allow) {

    allow = (((allow || "") + "").toLowerCase().match(/<[a-z][a-z0-9]*>/g) || []).join('');
    var tags = /<\/?([a-z][a-z0-9]*)\b[^>]*>/gi;
    var commentsAndPhpTags = /<!--[\s\S]*?-->|<\?(?:php)?[\s\S]*?\?>/gi;
    return str.replace(commentsAndPhpTags, '').replace(tags, function ($0, $1) {
        return allow.indexOf('<' + $1.toLowerCase() + '>') > -1 ? $0 : '';
    });
}

function getSelectionText() {
    var selection = "";
    var sel;
    var container;
    var title;
    var i;
    var text = "";
    //if (window.getSelection) {
    //    text = window.getSelection().toString();
    //} else if (document.selection && document.selection.type != "Control") {
    //    text = document.selection.createRange().text;
    //}
    if (typeof window.getSelection != "undefined") {
        sel = window.getSelection();

        if (sel.rangeCount) {
            container = document.createElement("div");
            var len;
            for (i = 0, len = sel.rangeCount; i < len; i++) {
                var a = sel.getRangeAt(i).cloneContents();
                if (a && a.firstChild && a.firstChild.tagName && /h[0-9]/.test(a.firstChild.tagName.toLowerCase())) {
                    title = a.firstChild.textContent;
                }
                container.appendChild(a);
            }
            selection = container.innerHTML;
        }
    } else if (typeof document.selection != "undefined") {
        if (document.selection.type == "Text") {
            selection = document.selection.createRange().htmlText;
        }
    }

    var htmlSelection = $("<div>").html(selection);
    htmlSelection.find("script,noscript").remove();
    htmlSelection.find("font").each(function () {
        $(this).replaceWith("<div>" + $(this).html() + "</div>");
    });
    htmlSelection.html($.trim(htmlSelection.html())); //rremove multisplaces
    htmlSelection.html(htmlSelection.html().replace(/(<br\s*\/?>){2,}/gi, '<br>')); //rremove multi br
    htmlSelection.find("*").filter(function () {
        var spaces = (this.nodeType == 3 && !/\S/.test(this.nodeValue));
        var emptyTags = $.trim($(this).html()) == ''; //rremove empty tags
        return spaces || emptyTags;
    }).remove();


    title = title ? title : (document.title && document.title != "") ? document.title : document.URL;
    return {
        text: stripTags(htmlSelection.html(), ''),
        title: title
    };

}

$(document).on('keypress', function(e) {
    var part = getSelectionText();
    if (e.which == 2) { //Ctrl + b
        var answer = {
            Referer: window.location.href,
            Title: part.title,
            Text: part.text,
            Type: 'text'
        };
        KNUGGET.api("addToBoard", {answer: answer});
    }
});


KNUGGET.storage.get("templates", function (templates) {
    KNUGGET.templates = JSON.parse(templates);
});







'use strict';

DRAGDIS.CurrentPage = window.location.href;
DRAGDIS.browserInstanceId = Math.floor((1 + Math.random()) * 0x10000000000).toString(16).substring(1);


/* ==========================================================================
Global getters / setters
========================================================================== */
DRAGDIS.storage = {
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

DRAGDIS.bookmarks = {
    set: function (tree) {

        var jsonBookmarks = $(".browser_bookmarks");

        jsonBookmarks.show();
        jsonBookmarks.find("#jsonInput").val(tree);
        jsonBookmarks.find("#read_bookmarks").hide();

    },
    get: function () {
        DRAGDIS.sendMessage({
            Type: "GET_BOOKMARKS"
        });
    }
};


/* ==========================================================================
Global functions
========================================================================== */
DRAGDIS.backgroundListener = function (request) {

    //REVIEW: find better solution to skip iframes
    if (window.safari && window.top !== window) {
        return;
    }
    if (window.safari && request.name !== "DRAGDIS_MESSAGING") {
        return;
    } //kill message if is not from dragdis extension

    if (DRAGDIS.sidebarController == null) {
        DRAGDIS.sidebarController = {};
    }

    switch (request.Type) {
        case "SIDEBAR_SHOW":
            if (DRAGDIS.sidebarController.active) {
                DRAGDIS_SIDEBAR.openedByIcon = 0;
                DRAGDIS.sidebarController.hide(true, true); //closeFast , isCloseManually
            } else {

                //Set flag for manual initialization (required for user stats)
                if (!DRAGDIS.sidebarController.folders) {
                    DRAGDIS.config.isInitializedManually = true;
                }

                DRAGDIS_SIDEBAR.openedByIcon = 1;
                DRAGDIS_SIDEBAR.show({ isOpenedManually: true });
            }
            break;
        case "DISPLAY_UPLOAD_ERROR":
            DRAGDIS_SIDEBAR.show({
                showUploadError: true
            });
            break;
        case "DRAGDIS_UPDATE_ACTIVE":
            if (DRAGDIS.sidebarController != null) {
                DRAGDIS.sidebarController.user.set(request.Value);
            }
            break;
        case "DRAGDIS_UPDATE_FOLDERS":
            if (DRAGDIS.sidebarController != null && DRAGDIS.sidebarController.folders) {
                DRAGDIS.sidebarController.folders.update(request.Value);
            }
            break;
        case "SIDEBAR_UPDATE_SCROLLPOSITION":
            if (DRAGDIS.sidebarController != null && typeof DRAGDIS.sidebarController.$broadcast == "function") {
                DRAGDIS.sidebarController.$broadcast("Update_scrollPosition");
            }
            break;
        case "SET_BOOKMARKS":
            DRAGDIS.bookmarks.set(request.Value);

            break;
    }
};

DRAGDIS.api = function (method, data, callback) {

    var deferred = $.Deferred();
    var port = window.chrome.runtime.connect({
        name: "dragdis"
    });

    data.method = method;
    data.sender = DRAGDIS.browserInstanceId;

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

DRAGDIS.sendMessage = function (data, callback) {
    window.chrome.runtime.sendMessage(data, callback);
};

DRAGDIS.screenSnapshot = function () {
    DRAGDIS.sendMessage({
        Type: "ScreenSnapshot"
    });
};


/* ==========================================================================
Sync between dragdis iframe<-->extension
========================================================================== */
window.addEventListener('message', function (e) {

    var data;

    try {
        data = JSON.parse(e.data);
    } catch (e) {
        data = {};
    }

    if (e.origin.toLowerCase().indexOf(DRAGDIS.config.domain.toLowerCase().slice(0, -1)) > -1) {

        if (data.type == 'DRAGDIS_ExtensionIframeSync' && data.action == 'FORCE_REFRESH') {

            // Send confirmation to iframe that message has been received 
            e.source.postMessage(JSON.stringify({
                messageReceived: true
            }), '*');

            // Initialize reconnect
            DRAGDIS.sendMessage({
                Type: "RECONNECT"
            });
        }
    }
}, false);


/* ==========================================================================
Extension background message listener
========================================================================== */
window.chrome.extension.onMessage.addListener(DRAGDIS.backgroundListener);


//TODO: move to extension native page mod
/* ==========================================================================
Static page mod for bookmarks
========================================================================== */
$(document).on("ready", function () {

    if (window.location.href.toLowerCase().indexOf("dragdis.com/account/settings/import") > -1) {
        DRAGDIS.bookmarks.get();
    }

});

DRAGDIS.storage.get("templates", function (templates) {
    DRAGDIS.templates = JSON.parse(templates);
});







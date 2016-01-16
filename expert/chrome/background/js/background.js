'use strict';


KNUGGET.storage.set("Requests",JSON.stringify([]));
KNUGGET.storage.set("Board",JSON.stringify([]));
KNUGGET.storage.set("ActiveRequest", null);
// REGISTER API COMMAND LISTENER
chrome.runtime.onConnect.addListener(function(port) {

    port.onMessage.addListener(function(request, portInfo) {

        // Globally accessible function to execure API calls
        KNUGGET.executeApiCall = function(request, senderId) {

            var originalRequestMethod = request.method;

            //If API method is retryItemUpload, convert it to upload 
            if (request.method == "retryItemUpload" && KNUGGET.failedItemUpload) {
                request = KNUGGET.failedItemUpload.request;
                senderId = KNUGGET.failedItemUpload.sender;
            }

            //Dinamically call API method
            console.log('call: ' + request.method);
            KNUGGET.api[request.method](request, senderId).then(function(response) {
                port.postMessage(response);

            }, function(error) {
                //If sharing item is forbidden
                if (error.status === 403 && error.data.ShareResult) {

                    //TODO: Remove dummy condition 

                } else if (request.method == "Upload" && originalRequestMethod != "retryItemUpload") {

                    //Save failed item data if user chose to retry an upload
                    KNUGGET.failedItemUpload = {
                        request: request,
                        sender: senderId
                    };

                    //Send message to active tab to display user failed upload notification
                    KNUGGET.sendMessage({
                        Type: "DISPLAY_UPLOAD_ERROR"
                    });
                }

                port.postMessage(error);

            });
        };

        KNUGGET.executeApiCall(request, request.sender);
    });
});

//Reset localstorage
KNUGGET.storage.set("Domain", KNUGGET.config.domain);
KNUGGET.storage.set("ConnectionFail", false);
KNUGGET.storage.set("IsConnected", false);
KNUGGET.storage.set("FoldersList", {});
KNUGGET.storage.set("UserActive", {
    Active: false
});
KNUGGET.storage.set("CurrentSender", 'empty');
KNUGGET.storage.set("AppConfig", 'empty');
KNUGGET.storage.set("ScrollPosition", null);

KNUGGET.screenSnapshotValue = "";

KNUGGET.imageProcessor = {
    makeSnapshot: function(callback) {
        var $this = this;
        var snapshotStart = new Date().getTime();

        var params = KNUGGET.config.browser.version < 34 ? {
            "format": "png"
        } : {
            format: "jpeg",
            quality: 90
        };

        window.chrome.tabs.captureVisibleTab(null, params, function(base64String) {
            if (snapshotStart + KNUGGET.config.timing.snapshotTimeout > new Date().getTime()) {

                if (KNUGGET.config.browser.OS === "Windows") {
                    $this.crop(base64String, function(result) {
                        callback(result);
                    });
                } else {
                    callback(base64String);
                }

                callback(base64String);
            } else {
                callback(0);
            }
        });
    },
    crop: function(base64String, callback) {
        if (KNUGGET.config.canvasEnabled) {
            //crop top from snapshot
            this.getBase64String(base64String, function(croppedBase64String) {
                callback(croppedBase64String);
            }, 20);

        } else {
            callback(base64String);
        }
    },
    getBase64String: function(src, callback, cropWidth) {
        $('<img />', {
            "src": src
        }).load(function(e) {
            var canvas = document.createElement('canvas');

            canvas.width = this.width - cropWidth;
            canvas.height = this.height;

            var context = canvas.getContext('2d');

            var x = 0;
            var y = 0;
            var width = this.width;
            var height = this.height;

            context.drawImage(this, x, y, width, height);
            var base64String = canvas.toDataURL();

            callback(base64String);
        });
    },
    convertBase64toAb: function(base64) {
        if (base64) {
            var binaryString = window.atob(base64);
            var len = binaryString.length;
            var bytes = new Uint8Array(len);
            for (var i = 0; i < len; i++) {
                var ascii = binaryString.charCodeAt(i);
                bytes[i] = ascii;
            }

            return {
                size: len,
                binary: bytes.buffer
            };
        } else {
            return {
                size: 0,
                binary: null
            };
        }
    },
    getRemoteImageStream: function(src, callback) {
        var xhr = new XMLHttpRequest();
        xhr.open('GET', src, true);
        xhr.responseType = 'blob';

        xhr.onload = function(e) {
            if (e.target.status == 200) {
                callback(e.target.response);
            } else {
                callback(null);
            }
        };

        xhr.send();
    },
    getBiggerImage: function(imageUrl, sitepage, callback) {

        switch (sitepage) {
            case "fb":

                var imagesList = [
                    imageUrl.replace("_q.jpg", "_n.jpg"),
                    imageUrl.replace("s480x480/", "").replace("_s.jpg", "_n.jpg")
                ];

                if (imageUrl.indexOf("safe_image.php") >= 0) {

                    var imageParsedUrl = KNUGGET.urlParam("url", imageUrl);
                    
                    if (!imageParsedUrl) {
                        imageUrl.push(imageParsedUrl);
                    }
                }

                callback(this.checkRemoteFiles(imagesList, imageUrl));

                break;
                
            case "ffffound":
                callback(this.checkRemoteFiles([
                        imageUrl.replace("_xs.jpg", "_m.jpg")
                        .replace("_s.jpg", "_m.jpg")
                        .replace("_xs.png", "_m.png")
                        .replace("_s.png", "_m.png")
                        .replace("_xs.gif", "_m.gif")
                        .replace("_s.gif", "_m.gif")
                    ],
                    imageUrl));
                break;
            case "pinterest":
                callback(this.checkRemoteFiles([
                        imageUrl
                            .replace("_b.jpg", "_c.jpg")
                            .replace("_t.jpg", "_c.jpg")
                            .replace("/236x/", "/736x/")
                    ],
                    imageUrl));
                break;
            case "behance":
                callback(this.checkRemoteFiles([
                        imageUrl
                            .replace("/202/", "/404/")
                    ],
                    imageUrl));
                break;
            case "dribbble":
                callback(this.checkRemoteFiles([
                        imageUrl
                            .replace("_teaser", "")
                            .replace("_1x", "")
                    ],
                    imageUrl));
                break;
            case "twitter":
                callback(this.checkRemoteFiles([
                        imageUrl.replace("_normal.jpg", ".jpg")
                    ],
                    imageUrl));
                break;
            case "tumblr":
                callback(this.checkRemoteFiles([
                        imageUrl.replace("_250.jpg", "_1280.jpg")
                        .replace("_500.jpg", "_1280.jpg"),
                        imageUrl.replace("_250.jpg", "_500.jpg")
                    ],
                    imageUrl));
                break;
            default:
                callback(imageUrl);
        }
    },

    checkRemoteFiles: function(files, original) {
        for (var i = 0; i < files.length; i++) {
            if (this.filePing(files[i])) {
                return files[i];
            }
        }
        return original;
    },
    filePing: function(src) {
        var http = new XMLHttpRequest();
        http.open('HEAD', src, false);

        try {
            http.send();

            if (http.readyState == http.DONE && http.status == 200) {
                return true;
            } else {
                return false;
            }
        } catch (ex) {
            console.log("ERRR", ex);
            return false;
        }
    }
};

KNUGGET.getMessage = function(request, sender, sendResponse) {
    request = window.chrome ? request : request.message;

    switch (request.Type) {
        case "GetValue":
            KNUGGET.storage.get(request.Key, function(value) {
                if (window.chrome) {
                    sendResponse(value);
                }
            });
            break;

        case "SetValue":
            this.storage.set(request.Key, request.Value);
            break;

        case "ScreenSnapshot":
            var snapshotEnable = (window.chrome && this.config.browser.version > 20) || (window.safari && this.config.browser.version > 5) ? 1 : 0;
            if (snapshotEnable) {
                this.imageProcessor.makeSnapshot(function(snapshot) {
                    if (snapshot) {
                        KNUGGET.screenSnapshotValue = snapshot;
                    }
                });
            }
            break;

        //case "RECONNECT":
        //    startHubConnection();
        //    break;
        //
        //case "REFRESH_CONNECTION":
        //    refreshHubConnection();
        //    break;

        case "INJECTION":
            KNUGGET.injector.add(sender.tab.id, sender.tab.url);
            break;

        case "CLIPBOARD_COPY":
            var copyFrom = $('<textarea/>').attr("id", "copyFrom");
            copyFrom.text(request.Value);
            $('body').append(copyFrom);
            copyFrom.select();
            document.execCommand('copy', true);
            copyFrom.remove();


            //Send callback
            if (sendResponse) {
                sendResponse({
                    status: "OK"
                });
            }

            break;
        default:

            var senderId = String(sender.tab.windowId) + String(sender.tab.id);

            if (request.Type) {
                console.log("request.Type:: " + request.Type);
                console.log(request);
                KNUGGET.api[request.Type](request, senderId).then(function(response) {
                    sendResponse('response');
                });
            }
    }
};

// Check whether new version is installed
window.chrome.runtime.onInstalled.addListener(function (details) {
    if (details.reason === "install") {
        KNUGGET.injector.reInject();
    }
});

KNUGGET.urlParam = function(param, text) {
    param = param.replace(/[\[]/, "\\\[").replace(/[\]]/, "\\\]");
    var regexS = "[\\?&]" + param + "=([^&#]*)";
    var regex = new RegExp(regexS);
    var results = regex.exec(text);

    if (results == null)
        return "";
    else {
        return decodeURIComponent(results[1]);
    }

};

KNUGGET.sendMessage = function(data) {
    window.chrome.tabs.query({
        active: true,
        lastFocusedWindow: true,
        highlighted: true
    }, function(tabs) {
        $.each(tabs, function(key, tab) {
            window.chrome.tabs.sendMessage(tab.id, data);
        });
    });
};

//:>> MESSAGING
var multiFirePrevent = [];

window.chrome.runtime.onMessage.addListener(function(request, sender, sendResponse) {

    //Injection: prevent double fire when onUpdated loading fired twice.
    if (typeof request.ExtensionIsInjected !== "undefined" && !request.ExtensionIsInjected) {
        chrome.tabs.executeScript(sender.tab.id, {
            code: "window.ExtensionIsInjected = true;"
        });

        if (multiFirePrevent[sender.tab.id]) {
            clearTimeout(multiFirePrevent[sender.tab.id]);
        }

        multiFirePrevent[sender.tab.id] = setTimeout(function(tabId, tabUrl) {
            KNUGGET.injector.add(tabId, tabUrl);
        }, 500, sender.tab.id, sender.tab.url);

        return;
    }

    KNUGGET.getMessage(request, sender, sendResponse);
});

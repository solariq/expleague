﻿/* ==========================================================================
Storage
========================================================================== */
KNUGGET.storage = {
    get: function (key, callback) {
        window.chrome.storage.local.get(key, function (outObj) {
            callback(outObj[key] || "");
        });
    },
    set: function (key, value) {
        var obj = {};
        obj[key] = value;

        window.chrome.storage.local.set(obj);

        if (key == "UserActive" && value.Username) {
            KNUGGET.config.Username = value.Username;
        }
    }
};



/* ==========================================================================
Content script injection
========================================================================== */
KNUGGET.disabledDomains = {
    storageName: "DisabledDomains",
    list: [],
    add: function (domain) {
        var $this = this;
        KNUGGET.storage.get($this.storageName, function (list) {
            var disabledDomains = !list ? [] : JSON.parse(list);
            var domainIndex = list.indexOf(domain);

            if (domainIndex === -1) {
                disabledDomains.push(domain);
            }

            KNUGGET.storage.set($this.storageName, JSON.stringify(disabledDomains));
            $this.list = disabledDomains;

            window.chrome.tabs.query({
                highlighted: true,
                active: true
            }, function (tabs) {
                for (var i = 0; i < tabs.length; i++) {
                    $this.updateTabContextMenu(tabs[i].id);
                }
            });
        });
    },
    remove: function (domain) {
        var $this = this;
        if (this.list.length > 0) {
            var domainIndex = $.inArray(domain, this.list);

            if (domainIndex !== -1) {
                this.list.splice(domainIndex, 1);
            }

            KNUGGET.storage.set(this.storageName, JSON.stringify(this.list));

            window.chrome.tabs.query({
                highlighted: true,
                active: true
            }, function (tabs) {
                for (var i = 0; i < tabs.length; i++) {
                    $this.updateTabContextMenu(tabs[i].id);
                }
            });
        }
    },
    updateTabContextMenu: function (tabId) {
        var $this = this;

        window.chrome.tabs.get(tabId, function (tab) {
            if (!tab.url) {
                return;
            }
            var domain = KNUGGET.getHostName(tab.url);

            KNUGGET.storage.get($this.storageName, function (list) {
                var disabledDomains = !list ? [] : JSON.parse(list);
                $this.list = disabledDomains;

                if ($.inArray(domain, disabledDomains) !== -1) {
                    window.chrome.contextMenus.update(DisabledDomains_Disable, {
                        enabled: false
                    });
                    window.chrome.contextMenus.update(DisabledDomains_Enable, {
                        enabled: true
                    });
                } else {
                    window.chrome.contextMenus.update(DisabledDomains_Disable, {
                        enabled: true
                    });
                    window.chrome.contextMenus.update(DisabledDomains_Enable, {
                        enabled: false
                    });
                }
            });
        });
    }
};

KNUGGET.imageIcon = {
    whiteList: [
        /facebook\.com/,
        /9gag\.com/,
        /frontend\.lt/,
        /dribbble\.com/,
        /stackoverflow\.com/,
        /reddit\.com/,
        /behance\.net/,
        /pinterest\.com/,
        /tumblr\.com/,
        /imgur\.com/,
        /vimeo\.com/,
        /shutterstock\.com/,
        /designspiration\.net/,
        /reddit\.com/,
        /libertaddigital\.com/,
        /sumally\.com/,
        /medium\.com/,
        /themeforest\.net/,
        /ffffound\.com/,
        /ebay\.com/,
        /dribbbleboard\.com/,
        /news\.ycombinator\.com/,
        /wikibooks\.org/,
        /wikipedia\.org/,
        /\.(jpeg|jpg|gif|png)$/,
        /google\..*\/search.*&tbm=isch/,
        /bing.com\/images\/search\?q/,
        /images\.search\.yahoo\.com\/search\/images/
        ///istockphoto\.com/,
    ],
    isViableHost: function(url) {

        var isViable = false;

        //Check domain whitelist
        $.each(KNUGGET.imageIcon.whiteList, function (key, regex) {
            if (regex.test(url)) {
                isViable = true;
                return true;
            };
        })

        return isViable;
    }
}

KNUGGET.injector = {
    matches: [
        "^http*.://.+/*"
    ],
    exclude_matches: [
        "^https://.+(bank).+",
        "^http*.://(maps.google.com|www.google.com/maps).+",
        "^http*.://www.bing.com/maps.+",
        "^http*.://drive.google.com.+"
    ],
    JS: chrome.runtime.getManifest().content_scripts[0].js,
    CSS: chrome.runtime.getManifest().content_scripts[0].css,

    inject: function (tabId) {
        console.log('injectiong on' + tabId);

        chrome.tabs.executeScript(tabId, {
            code: "chrome.runtime.sendMessage({ExtensionIsInjected: window.ExtensionIsInjected || false});"
        }, function () {
            if (chrome.runtime.lastError) {
                return;
            }
        });
    },
    isMatch: function (url) {
        //dissalow places
        for (var d = 0; d < this.exclude_matches.length; d++) {
            var pattForExcludeMatches = new RegExp(this.exclude_matches[d], "i");

            if (pattForExcludeMatches.test(url)) {
                return false;
            }
        }

        //disabled Domains
        for (var dd = 0; dd < KNUGGET.disabledDomains.list.length; dd++) {
            var pattForDisabledDomains = new RegExp("^http*.://(.+)?(" + KNUGGET.disabledDomains.list[dd] + ").+", "i");

            if (pattForDisabledDomains.test(url)) {
                return false;
            }
        }

        //allow places
        for (var a = 0; a < this.matches.length; a++) {
            var pattForMatches = new RegExp(this.matches[a], "i");

            if (pattForMatches.test(url)) {
                return true;
            }
        }

        return false;
    },
    add: function (tabId, tabUrl) {
        console.log('adding on' + tabId);
        var $this = this;

        if ($this.isMatch(tabUrl)) {

            console.info("Inject at " + tabId, tabUrl);


            KNUGGET.storage.get('VisitedPages', function(vp) {
                vp = vp ? JSON.parse(vp) : [];
                vp.push(tabUrl);
                KNUGGET.storage.set("VisitedPages", JSON.stringify(vp));
            });
            //Add image icon
            KNUGGET.storage.get("userSettings", function(userSettings) {

                var currentTime = new Date().getTime();
                var inactivityPeriod = (24*60*60*1000) * 7;
                var isUserInactive;

                if (!userSettings.lastDragTime || userSettings.lastDragTime < currentTime - inactivityPeriod) {
                    isUserInactive = true;
                }

                if (KNUGGET.imageIcon.isViableHost(tabUrl) && isUserInactive) {
                    window.chrome.tabs.executeScript(tabId, {
                        file: chrome.runtime.getManifest().content_scripts[3].js[0],
                        allFrames: false,
                        runAt: "document_start"
                    }, function (result) {
                        if (chrome.runtime.lastError) {
                            return;
                        }
                    });
                }

            });

            //inject js files
            for (var j = 0; j < $this.JS.length; j++) {

                window.chrome.tabs.executeScript(tabId, {

                    file: $this.JS[j],
                    allFrames: false,
                    runAt: "document_start"

                }, function (result) {

                    if (chrome.runtime.lastError) {
                        return;
                    }

                });

            }

            //inject css files
            for (var s = 0; s < $this.CSS.length; s++) {
                window.chrome.tabs.insertCSS(tabId, {
                    file: $this.CSS[s],
                    allFrames: false,
                    runAt: "document_start"
                }, function (result) {
                    if (chrome.runtime.lastError) {
                        return;
                    }
                });
            }
        }
    },
    reInject: function () {
        //Load sidebar in already opened tabs of current Window
        chrome.tabs.getAllInWindow(null, function (tabs) {
            for (var i = 0; i < tabs.length; i++) {
                KNUGGET.injector.add(tabs[i].id, tabs[i].url);
            }
        });
    }
};

chrome.tabs.onUpdated.addListener(function (tabId, tabInfo, tab) {

    setTimeout(KNUGGET.disabledDomains.updateTabContextMenu(tabId));

    if (tab.url.toLowerCase().indexOf("http") !== 0) return;

    if (tabInfo.status === "loading") {
        console.log('loading on' + tabId);
        KNUGGET.injector.inject(tabId);
    }
});

chrome.tabs.onReplaced.addListener(function (tabId) {
    window.chrome.tabs.get(tabId, function (tab) {
        setTimeout(KNUGGET.disabledDomains.updateTabContextMenu(tabId));

        //prevent files injection to same tab with hash changes, below link show the solution
        if (tab.url.toLowerCase().indexOf("http") !== 0) return;
        console.log('listening on' + tabId);
        KNUGGET.injector.inject(tabId);
    });
});




/* ==========================================================================
Context menu
========================================================================== */

window.chrome.contextMenus.create({
    'title': 'Open knugget.com',
    'contexts': ['all'],
    'onclick': function () {
        window.chrome.tabs.create({
            url: KNUGGET.config.domain
        });
    }
});

window.chrome.contextMenus.create({
    type: 'separator',
    'contexts': ['page']
});

var DisabledDomains_Disable = window.chrome.contextMenus.create({
    'title': 'Disable on this page',
    'contexts': ['page'],
    'onclick': function (info) {
        KNUGGET.disabledDomains.add(KNUGGET.getHostName(info.pageUrl));
    }
});

var DisabledDomains_Enable = window.chrome.contextMenus.create({
    'title': 'Enable on this page',
    'contexts': ['page'],
    'onclick': function (info) {
        KNUGGET.disabledDomains.remove(KNUGGET.getHostName(info.pageUrl));
    }
});

window.chrome.contextMenus.create({
    type: 'separator',
    'contexts': ['page']
});




/* ==========================================================================
Toolbar icon
========================================================================== */

window.chrome.browserAction.onClicked.addListener(function (activeTab) {
    if (activeTab.url == "chrome://newtab/") {
        window.chrome.tabs.update(activeTab.id, {
            url: KNUGGET.config.domain
        });
    } else {
        if (activeTab.url.indexOf("chrome://") == -1) {
            //Show Sidebar
            KNUGGET.sendMessage({
                Type: "SIDEBAR_SHOW"
            });
        }
    }
});



/* ==========================================================================
Helper functions
========================================================================== */

//Extension browser details
KNUGGET.browserDetails = function () {

    //var nVer = navigator.appVersion;
    var nAgt = navigator.userAgent;
    var browserName = navigator.appName;
    var fullVersion = '' + parseFloat(navigator.appVersion);
    var majorVersion; //parseInt(navigator.appVersion, 10);
    var nameOffset, verOffset, ix;

    // In Opera, the true version is after "Opera" or after "Version"
    if ((verOffset = nAgt.indexOf("Opera")) !== -1) {
        browserName = "Opera";
        fullVersion = nAgt.substring(verOffset + 6);
        if ((verOffset = nAgt.indexOf("Version")) !== -1) {
            fullVersion = nAgt.substring(verOffset + 8);
        }
    }
        // In MSIE, the true version is after "MSIE" in userAgent
    else if ((verOffset = nAgt.indexOf("MSIE")) !== -1) {
        browserName = "Microsoft Internet Explorer";
        fullVersion = nAgt.substring(verOffset + 5);
    }
        // In Chrome, the true version is after "Chrome"
    else if ((verOffset = nAgt.indexOf("Chrome")) !== -1) {
        browserName = "Chrome";
        fullVersion = nAgt.substring(verOffset + 7);
    }
        // In Safari, the true version is after "Safari" or after "Version"
    else if ((verOffset = nAgt.indexOf("Safari")) !== -1) {
        browserName = "Safari";
        fullVersion = nAgt.substring(verOffset + 7);
        if ((verOffset = nAgt.indexOf("Version")) !== -1) {
            fullVersion = nAgt.substring(verOffset + 8);
        }
    }
        // In Firefox, the true version is after "Firefox"
    else if ((verOffset = nAgt.indexOf("Firefox")) !== -1) {
        browserName = "Firefox";
        fullVersion = nAgt.substring(verOffset + 8);
    }
        // In most other browsers, "name/version" is at the end of userAgent
    else if ((nameOffset = nAgt.lastIndexOf(' ') + 1) <
        (verOffset = nAgt.lastIndexOf('/'))) {
        browserName = nAgt.substring(nameOffset, verOffset);
        fullVersion = nAgt.substring(verOffset + 1);
        if (browserName.toLowerCase() == browserName.toUpperCase()) {
            browserName = navigator.appName;
        }
    }
    // trim the fullVersion string at semicolon/space if present
    if ((ix = fullVersion.indexOf(";")) !== -1) {
        fullVersion = fullVersion.substring(0, ix);
    }
    if ((ix = fullVersion.indexOf(" ")) !== -1) {
        fullVersion = fullVersion.substring(0, ix);
    }

    majorVersion = parseInt('' + fullVersion, 10);
    if (isNaN(majorVersion)) {
        fullVersion = '' + parseFloat(navigator.appVersion);
        majorVersion = parseInt(navigator.appVersion, 10);
    }

    var osName = "Unknown OS";
    if (navigator.appVersion.indexOf("Win") != -1) osName = "Windows";
    if (navigator.appVersion.indexOf("Mac") != -1) osName = "Mac";
    if (navigator.appVersion.indexOf("X11") != -1) osName = "UNIX";
    if (navigator.appVersion.indexOf("Linux") != -1) osName = "Linux";

    KNUGGET.config.browser = {
        OS: osName,
        name: browserName,
        version: majorVersion,
        versionFull: fullVersion
    };
};

KNUGGET.browserDetails();

//Snapshot Canvas support
KNUGGET.isCanvasSupported = function () {
    var elem = document.createElement('canvas');
    return !!(elem.getContext && elem.getContext('2d'));
};

KNUGGET.config.CanvasEnabled = KNUGGET.isCanvasSupported();

KNUGGET.getHostName = function (url) {
    var match = url.match(/:\/\/(www[0-9]?\.)?(.[^/:]+)/i);
    if (match !== null && match.length > 2 &&
        typeof match[2] === 'string' && match[2].length > 0) {
        return match[2];
    } else {
        return null;
    }
};

KNUGGET.bookmarks = {
    get: function (callback) {
        chrome.bookmarks.getTree(function (tree) {
            var json = JSON.stringify(tree);
            callback(json);
        });
    }
};

KNUGGET.templates = {
    list: {},
    init: function () {
        var templates = [
            "views/empty.html",
            "views/error.html",
            "views/requestList.html",
            "views/dragArea.html",
            "views/dialog.moreFolders.html",
            "views/waiting.html",
            "views/unavailable.html",
            "views/login.html",
            "views/registration.html",
            "views/panel_chatWindow.html",
            "views/root.html",
            "views/sidebar.folder_0.html",
            "views/sidebar.folder_1.html",
             "views/sidebar.html",
             "views/uploadError.html",
             "views/image_icon.html",
             "views/tbtsboard.html"
        ];

        var promises = [];
        $.each(templates, function (index, value) {
            var promise = $.get(chrome.extension.getURL(value), function (data) {
                KNUGGET.templates.list[value.replace(".html", "")] = data;
            });

            promises.push(promise);
        });

        $.when.apply($, promises).then(function () {
            KNUGGET.storage.set("templates", JSON.stringify(KNUGGET.templates.list));
        });
    }
};

KNUGGET.templates.init();

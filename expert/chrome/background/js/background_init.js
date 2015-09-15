/* ==========================================================================
Storage
========================================================================== */
DRAGDIS.storage = {
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
            DRAGDIS.config.Username = value.Username;
        }
    }
};



/* ==========================================================================
Content script injection
========================================================================== */
DRAGDIS.disabledDomains = {
    storageName: "DisabledDomains",
    list: [],
    add: function (domain) {
        var $this = this;
        DRAGDIS.storage.get($this.storageName, function (list) {
            var disabledDomains = !list ? [] : JSON.parse(list);
            var domainIndex = list.indexOf(domain);

            if (domainIndex === -1) {
                disabledDomains.push(domain);
            }

            DRAGDIS.storage.set($this.storageName, JSON.stringify(disabledDomains));
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

            DRAGDIS.storage.set(this.storageName, JSON.stringify(this.list));

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
            var domain = DRAGDIS.getHostName(tab.url);

            DRAGDIS.storage.get($this.storageName, function (list) {
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

DRAGDIS.imageIcon = {
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
        $.each(DRAGDIS.imageIcon.whiteList, function (key, regex) {
            if (regex.test(url)) {
                isViable = true;
                return true;
            };
        })

        return isViable;
    }
}

DRAGDIS.injector = {
    matches: [
        "^http*.://.+/*"
    ],
    exclude_matches: [
        "^https://.+(bank).+",
        "^http(s|)://dragdis.com(/#.*|)$",
        "^http(s|)://*.dragdis.com(/#.*|)$",
        "^http(s|)://dev.dragdis.com(/#.*|)$",
        "^http(s|)://local.dragdis.com(/#.*|)$",
        "^http(s|)://dragdis.offline.lt(/#.*|)$",
        "^http*.://(maps.google.com|www.google.com/maps).+",
        "^http*.://www.bing.com/maps.+",
        "^http*.://drive.google.com.+"
    ],
    JS: chrome.runtime.getManifest().content_scripts[0].js,
    CSS: chrome.runtime.getManifest().content_scripts[0].css,

    inject: function (tabId) {

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
        for (var dd = 0; dd < DRAGDIS.disabledDomains.list.length; dd++) {
            var pattForDisabledDomains = new RegExp("^http*.://(.+)?(" + DRAGDIS.disabledDomains.list[dd] + ").+", "i");

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

        var $this = this;

        if ($this.isMatch(tabUrl)) {

            console.info("Inject at " + tabId, tabUrl);

            //Add image icon
            DRAGDIS.storage.get("userSettings", function(userSettings) {

                var currentTime = new Date().getTime();
                var inactivityPeriod = (24*60*60*1000) * 7;
                var isUserInactive;

                if (!userSettings.lastDragTime || userSettings.lastDragTime < currentTime - inactivityPeriod) {
                    isUserInactive = true;
                }

                if (DRAGDIS.imageIcon.isViableHost(tabUrl) && isUserInactive) {
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

            DRAGDIS.tracker.send(tabId);
        }
    },
    reInject: function () {
        //Load sidebar in already opened tabs of current Window
        chrome.tabs.getAllInWindow(null, function (tabs) {
            for (var i = 0; i < tabs.length; i++) {
                DRAGDIS.injector.add(tabs[i].id, tabs[i].url);
            }
        });
    }
};

chrome.tabs.onUpdated.addListener(function (tabId, tabInfo, tab) {

    setTimeout(DRAGDIS.disabledDomains.updateTabContextMenu(tabId));

    if (tab.url.toLowerCase().indexOf("http") !== 0) return;

    if (tabInfo.status === "loading") {
        DRAGDIS.injector.inject(tabId);
    }
});

chrome.tabs.onReplaced.addListener(function (tabId) {
    window.chrome.tabs.get(tabId, function (tab) {
        setTimeout(DRAGDIS.disabledDomains.updateTabContextMenu(tabId));

        //prevent files injection to same tab with hash changes, below link show the solution
        if (tab.url.toLowerCase().indexOf("http") !== 0) return;

        DRAGDIS.injector.inject(tabId);
    });
});



/* ==========================================================================
Tracker
========================================================================== */
DRAGDIS.tracker = {
    tabsUrls: [],
    data: [],
    add: function (object, tabId) {
        var $this = this;

        if (tabId > 0) {
            chrome.tabs.get(tabId, function (tab) {
                $this.tabsUrls[tab.id] = tab.url;
            });
        }

        if (!this.data[tabId]) {
            this.data[tabId] = [];
        }

        this.data[tabId].push(object);
    },
    send: function (tabId) {

        if (this.data[tabId] && this.data[tabId].length > 0) {

            var tabUrl = this.tabsUrls[tabId] || "";
            var data = JSON.parse(JSON.stringify(this.data[tabId]));

            delete this.data[tabId];
            delete this.tabsUrls[tabId];

            DRAGDIS.api["Tracker"]({
                data: data,
                sourceLink: tabUrl
            }).then(function (response) {
                if (response.status !== 200) {
                    //if tracking data not uploaded successfully, do something
                }
            });
        }
    }
};

chrome.tabs.onRemoved.addListener(function (tabId) {
    DRAGDIS.tracker.send(tabId);
});

chrome.windows.onRemoved.addListener(function (windowId) {
    DRAGDIS.tracker.send(windowId);
});



/* ==========================================================================
Context menu
========================================================================== */

window.chrome.contextMenus.create({
    'title': 'Open dragdis.com',
    'contexts': ['all'],
    'onclick': function () {
        window.chrome.tabs.create({
            url: DRAGDIS.config.domain
        });
        new TrackEvent("Sidebar offsite", "Click on context 'open dragdis.com'").send(true);
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
        DRAGDIS.disabledDomains.add(DRAGDIS.getHostName(info.pageUrl));
    }
});

var DisabledDomains_Enable = window.chrome.contextMenus.create({
    'title': 'Enable on this page',
    'contexts': ['page'],
    'onclick': function (info) {
        DRAGDIS.disabledDomains.remove(DRAGDIS.getHostName(info.pageUrl));
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
    new TrackEvent("Sidebar offsite", "Click on toolbar icon").send(true);

    if (activeTab.url == "chrome://newtab/") {
        window.chrome.tabs.update(activeTab.id, {
            url: DRAGDIS.config.domain
        });
    } else {
        if (activeTab.url.indexOf("chrome://") == -1) {
            //Show Sidebar 
            DRAGDIS.sendMessage({
                Type: "SIDEBAR_SHOW"
            });
        }
    }
});



/* ==========================================================================
Helper functions
========================================================================== */

//Extension browser details
DRAGDIS.browserDetails = function () {

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

    DRAGDIS.config.browser = {
        OS: osName,
        name: browserName,
        version: majorVersion,
        versionFull: fullVersion
    };
};

DRAGDIS.browserDetails();

//Snapshot Canvas support
DRAGDIS.isCanvasSupported = function () {
    var elem = document.createElement('canvas');
    return !!(elem.getContext && elem.getContext('2d'));
};

DRAGDIS.config.CanvasEnabled = DRAGDIS.isCanvasSupported();

DRAGDIS.getHostName = function (url) {
    var match = url.match(/:\/\/(www[0-9]?\.)?(.[^/:]+)/i);
    if (match !== null && match.length > 2 &&
        typeof match[2] === 'string' && match[2].length > 0) {
        return match[2];
    } else {
        return null;
    }
};

DRAGDIS.bookmarks = {
    get: function (callback) {
        chrome.bookmarks.getTree(function (tree) {
            var json = JSON.stringify(tree);
            callback(json);
        });
    }
};

DRAGDIS.templates = {
    list: {},
    init: function () {
        var templates = [
            "views/actionsBlock.html",
            "views/connectionLoading.html",
            "views/dialog.collaborate.html",
            "views/dialog.moreFolders.html",
            "views/empty.html",
            "views/error.html",
            "views/login.html",
            "views/onboarding.html",
            "views/panel_folder.html",
            "views/panel_item.html",
            "views/panel_newFolder.html",
            "views/root.html",
            "views/sidebar.folder_0.html",
            "views/sidebar.folder_1.html",
             "views/sidebar.html",
             "views/uploadError.html",
             "views/image_icon.html"
        ];

        var promises = [];
        $.each(templates, function (index, value) {
            var promise = $.get(chrome.extension.getURL(value), function (data) {
                DRAGDIS.templates.list[value.replace(".html", "")] = data;
            });

            promises.push(promise);
        });

        $.when.apply($, promises).then(function () {
            DRAGDIS.storage.set("templates", JSON.stringify(DRAGDIS.templates.list));
        });
    }
};

DRAGDIS.templates.init();

var apiHub = $.connection.apiHub;

var DomainInterval = setInterval(function () {
    if (DRAGDIS.config.domain.indexOf("http") !== -1) {
        clearInterval(DomainInterval);
        signalRInit();
    }
}, 100);

function signalRInit() {

    $.connection.hub.url = DRAGDIS.config.domain + "signalr";
    $.connection.hub.logging = true;

    // EVENT FIRED FROM SERVER
    apiHub.client.onFolderListUpdated = function (result) {

        var foldersList = JSON.parse(result.FoldersList);
        
        if (foldersList) {

            //Set sender if folders were updated from another tab
            if (result.Sender) {
                DRAGDIS.storage.set("CurrentSender", result.Sender);
            }

            DRAGDIS.storage.set("FoldersList", foldersList);
        }
    };

    apiHub.client.OnUserActiveUpdated = function (result) {
        var userActive = JSON.parse(result);
        if (userActive) {

            DRAGDIS.storage.set("IsConnected", userActive.Active);
            DRAGDIS.storage.set("UserActive", userActive);

            //Reload user settings
            DRAGDIS.loadUserSettings();

            // If user has logged out, stop connection and raise Clean Logout flag   
            if (!userActive.Active) {
                $.connection.hub.cleanLogOut = true;
                $.connection.hub.stop();

                //Clear folder list
                DRAGDIS.storage.set("FoldersList", {});
                DRAGDIS.storage.set("userSettings", {});
            };

        }
    };

    apiHub.client.OnUserReset = function (result) {

        // Stop connection
        $.connection.hub.stop();

        // Reestablish connection after one second
        setTimeout(function () {
            startHubConnection();
        }, 1000);
    };

    apiHub.client.OnUserKill = function (result) {

        // Stop connection and raise Clean Logout flag
        $.connection.hub.cleanLogOut = true;
        $.connection.hub.stop();

        DRAGDIS.storage.set("IsConnected", false);
        DRAGDIS.storage.set("ConnectionFail", false);
        DRAGDIS.storage.set("IsReconnecting", false);
        $.connection.hub.connectionRetryDelay = 1000;
    };

    apiHub.client.OnAppLink = function (result) {

        if (typeof result !== 'object') {
            result = JSON.parse(result);
        }

        //Add timestamp to ensure that Storage.onChange is fired
        result.timestamp = new Date().getTime();

        DRAGDIS.storage.set("AppConfig", result);

    };

    //===============================================================
    //EXPERIMENTAL 
    //===============================================================

    $.connection.hub.connectionRetryDelay = 1000;
    $.connection.hub.throwDisconnectError = function (message) {

        if (message) console.error(message);

        if (apiHub.connection.state == 4) {

            //If reconnecting happening more than 10s, display error notification for user
            if ($.connection.hub.connectionRetryDelay > 10000) {
                DRAGDIS.storage.set("ConnectionFail", true);

                //Set reconnecting flag as false, since it stopped trying to rennect
                DRAGDIS.storage.set("IsReconnecting", false);
            }

            // Reestablish connection after delay
            setTimeout(function () {

                // Stop operation if connection already has been established by other triggers
                if (apiHub.connection.state == 1) {
                    return;
                }

                // Increment dalay exponentially until it reaches just over 10 mins.
                if ($.connection.hub.connectionRetryDelay < 600000)
                    $.connection.hub.connectionRetryDelay *= 2;

                // Try connecting
                startHubConnection();

            }, $.connection.hub.connectionRetryDelay);

        };
    };


    // CONNECTION ERROR HANDLING
    $.connection.hub.disconnected(function () {

        // If User connection was stopped intentionaly, do not notify about error
        if ($.connection.hub.cleanLogOut) {
            $.connection.hub.cleanLogOut = false;
            return;
        }

        // Disconnect with error flag if user is connected
        DRAGDIS.storage.get("IsConnected", function (value) {
            if (value) {
                $.connection.hub.throwDisconnectError("hub.disconnected");
            }
        });
    });

    $.connection.hub.connectionSlow(function () {
        DRAGDIS.storage.set("IsReconnecting", true);
    });

    $.connection.hub.reconnecting(function () {
        DRAGDIS.storage.set("IsReconnecting", true);
    });


    // RECONNECTED EVENT
    $.connection.hub.reconnected(function () {
        DRAGDIS.storage.set("ConnectionFail", false);
        DRAGDIS.storage.set("IsReconnecting", false);
        DRAGDIS.storage.set("IsConnected", true);
        $.connection.hub.connectionRetryDelay = 1000;
    });

    // START CONNECTION
    startHubConnection();
}

function startHubConnection() {

    // Disconnect if another connection already extablished 
    if (apiHub.connection.state == 1) {
        $.connection.hub.stop();
    }

    //Set reconnecting flag to true, to indiicate connecting operation
    DRAGDIS.storage.set("IsReconnecting", true);

    // Create new connection
    $.connection.hub.start().done(function () {

        var getFoldersListTSuccess;

        //Set connection flag to indicaate, that connection has been established 
        DRAGDIS.storage.set("IsReconnecting", false);

        apiHub.server.getUserActive().done(function (result) {

            var userActive = JSON.parse(result);
            if (userActive) {
                DRAGDIS.storage.set("UserActive", userActive);
                DRAGDIS.storage.set("IsConnected", userActive.Active);
            }

        }).fail(function () {
            if (apiHub.connection.state != 4) {
                $.connection.hub.throwDisconnectError("getUserActive() failed");
            }
        });


        apiHub.server.getFolderList().done(function (result) {
            
            getFoldersListTSuccess = true;

            var foldersList = JSON.parse(result);

            if (foldersList) {
                DRAGDIS.storage.set("FoldersList", foldersList);
            }

        }).fail(function () {
            if (apiHub.connection.state != 4) {
                $.connection.hub.throwDisconnectError("getFolderList() failed");
            }
        });


        //If folders are not loaded in 1s retry once
        setTimeout(function () {
            if (!getFoldersListTSuccess) {
                DRAGDIS.api["FolderList"]();
            }
        }, 1000);

        DRAGDIS.storage.set("ConnectionFail", false);
        DRAGDIS.storage.set("IsReconnecting", false);
        $.connection.hub.connectionRetryDelay = 1000;

        $(window).one('unload', function () {
            $.connection.hub.stop();
        });

        //Load user settings if connection was established
        DRAGDIS.loadUserSettings();

    }).fail(function (msg) {
        DRAGDIS.storage.get("IsConnected", function (value) {
            if (value && apiHub.connection.state != 4) {
                $.connection.hub.throwDisconnectError("hub.start() failed on init");
            }
        });
    });
}

function refreshHubConnection() {

    if (apiHub.connection.state == 4) {

        $.connection.hub.start().done(function () {

            DRAGDIS.storage.set("ConnectionFail", false);
            DRAGDIS.storage.set("IsReconnecting", false);
            $.connection.hub.connectionRetryDelay = 1000;

        }).fail(function () {

            DRAGDIS.storage.get("IsConnected", function (value) {
                if (value && apiHub.connection.state != 4) {
                    $.connection.hub.throwDisconnectError("hub.start() failed on refreshHubConnection");
                }
            });
        });
    }
}

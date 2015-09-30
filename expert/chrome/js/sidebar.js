'use strict';

var DRAGDIS_SIDEBAR_NAME = "dragdis-root";

var DRAGDIS_SIDEBAR = {

    dragActive: 0,

    loadRootElement: function (callback) {

        var rootTemplate = DRAGDIS.templates["views/root"];
        
        //Create root element for sidebar
        if ($("body").length) {
            $("body").append(rootTemplate);
        } else {
            $("head").after($(rootTemplate).wrap("<body></body>"));
        }

        document.dispatchEvent(new CustomEvent('sidebarLoaded'));
        console.log('callbask')
        callback();
    },

    isSidebarElm: function (el) {
        return el.closest("#" + DRAGDIS_SIDEBAR_NAME).length ? 1 : 0;
    },

    show: function (params) {
        var sidebar = $("#" + DRAGDIS_SIDEBAR_NAME);

        if (!sidebar.length) {
            this.loadRootElement(function () {
                DRAGDIS.config.firstTimeLoad = true;

                angular.bootstrap(document.getElementById(DRAGDIS_SIDEBAR_NAME), ['dragdisSidebar']);

                //Reset hidden initialization and manual initialization flags
                delete DRAGDIS.config.initHidden;
                delete DRAGDIS.config.isInitializedManually;

                //If aditional parameters are set, execute them
                if (params) {

                    if (params.showUploadError) {
                        DRAGDIS.sidebarController.uploadError = true;
                        DRAGDIS.sidebarController.show(true);
                    }

                    if (params.callback) {
                        params.callback();
                    }
                }
            });

        } else {

            //TODO: Refactor params execution to single function
            //If aditional parameters are set, execute them
            if (params) {

                DRAGDIS.sidebarController.show(true, params.isOpenedManually);

                if (params.showUploadError) {
                    DRAGDIS.sidebarController.uploadError = true;
                }

                if (params.callback) {
                    params.callback();
                }

            } else {
                DRAGDIS.sidebarController.show(true);
            }
        }
    }
};

'use strict';

var DRAGDIS_SIDEBAR_NAME = "knugget-root";

var DRAGDIS_SIDEBAR = {

    dragActive: 0,

    loadRootElement: function (callback) {

        var rootTemplate = KNUGGET.templates["views/root"];
        
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
                KNUGGET.config.firstTimeLoad = true;

                angular.bootstrap(document.getElementById(DRAGDIS_SIDEBAR_NAME), ['knuggetSidebar']);

                //Reset hidden initialization and manual initialization flags
                delete KNUGGET.config.initHidden;
                delete KNUGGET.config.isInitializedManually;

                //If aditional parameters are set, execute them
                if (params) {

                    if (params.showUploadError) {
                        KNUGGET.sidebarController.uploadError = true;
                        KNUGGET.sidebarController.show(true);
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

                KNUGGET.sidebarController.show(true, params.isOpenedManually);

                if (params.showUploadError) {
                    KNUGGET.sidebarController.uploadError = true;
                }

                if (params.callback) {
                    params.callback();
                }

            } else {
                KNUGGET.sidebarController.show(true);
            }
        }
    }
};

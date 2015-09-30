'use strict';

var DRAGDIS = DRAGDIS || {};
DRAGDIS.config = DRAGDIS.config || {};

var sidebarConfig = {
    prefix: "",
    domain: "toobusytosearch.net",
    shortUrlDomain: "toobusytosearch.net",
    timing: {
        dragDelay: 300,
        snapshotTimeout: 7000,
        sidebarClose: 700,
        groupOpenDelay: 900,
        groupCloseDelay: 1500,
        successMsgShow: 3000,
        groupExpandTime: 300 //must be the same as group expand transition
    },
    isExtension: true,
    sidebarTemplatesRoot: "views/",
    foldersLimit: 15
};

(function prefixDomainUrl() {

    var config = DRAGDIS.config;

    $.extend(true, config, sidebarConfig);

    config.shortUrlDomain = "http://" + config.shortUrlDomain + "/";

    if (config.prefix) {
        config.prefix += ".";
    }

    config.domain = "http://" + config.prefix + config.domain + "/";

})();

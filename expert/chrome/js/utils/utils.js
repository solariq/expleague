DRAGDIS.serviceEnum = function (serviceId) {
    switch (serviceId) {
        case 2:
            return "Facebook";
        case 3:
            return "Twitter";
        case 4:
            return "Tumblr";
        case 9:
            return "Facebook chat";
        default:
            return "Dragdis";
    }
};

DRAGDIS.extensionFileUrl = function (file) {
    return window.chrome.extension.getURL(file);
};

//:>> Repair url if is without http*
DRAGDIS.urlParam = function (param, text) {
    param = param.replace(/[\[]/, "\\\[").replace(/[\]]/, "\\\]");
    var regexS = "[\\?&]" + param + "=([^&#]*)";
    var regex = new RegExp(regexS);
    var results = regex.exec(text);
    if (results == null)
        return "";
    else
        return decodeURIComponent(results[1]);
};
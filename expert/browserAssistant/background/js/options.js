KNUGGET.Storage = {
    Get: function (key, callback) {
        if (window.chrome) {
            window.chrome.storage.local.get(key, function (outObj) {
                callback(outObj[key] || "");
            });
        } else if (window.safari) {
            callback(window.safari.extension.settings[key] || "");
        }
    },
    Set: function (key, value) {
        if (window.chrome) {
            var obj = {};
            obj[key] = value;

            window.chrome.storage.local.set(obj);
        } else if (window.safari) {
            window.safari.extension.settings[key] = value;
        }
    }
};
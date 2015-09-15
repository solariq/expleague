DRAGDIS.Storage = {
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

// Saves options to localStorage.
function save_options() {
    var select = document.getElementById("mode");
    var url = select.children[select.selectedIndex].value;
    DRAGDIS.storage.set("Domain", url);

    // Update status to let user know options were saved.
    var status = document.getElementById("status");
    status.innerHTML = "Options Saved.";
    setTimeout(function () {
        status.innerHTML = "";
    }, 750);
}

// Restores select box state to saved value from localStorage.
function restore_options() {
    DRAGDIS.storage.get("Domain", function (value) {
        var mode = value;

        if (!mode) {
            return;
        }
        var select = document.getElementById("mode");
        for (var i = 0; i < select.children.length; i++) {
            var child = select.children[i];
            if (child.value == mode) {
                child.selected = "true";
                break;
            }
        }
    });
}

$(document).ready(function () {
    restore_options();

    $("#save").click(function () {
        save_options();
    });
});
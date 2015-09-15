var script = document.createElement('script');
script.textContent = '(window.chromeDragdis = "' + chrome.runtime.getManifest().version + '")';
(document.head || document.documentElement).appendChild(script);
script.parentNode.removeChild(script);

var DRAGDIS = DRAGDIS || {};

DRAGDIS.sendMessage = function (data, callback) {
    window.chrome.runtime.sendMessage(data, callback);
};

$(document).on("copyToClipboard", function (event) {
    DRAGDIS.sendMessage({
        Type: "CLIPBOARD_COPY",
        Value: event.originalEvent.detail.textToCopy,
    }, function (response) {
        if (response.status) {
            document.dispatchEvent(new CustomEvent('copyComplete'));
        }
    });
});

window.addEventListener('message', function (e) {

    var data;

    try {
        data = JSON.parse(e.data);
    } catch (e) {
        data = {};
    }

    if (e.origin.toLowerCase().indexOf(DRAGDIS.config.domain.toLowerCase().slice(0, -1)) > -1) {

        if (data.type == 'DRAGDIS_ExtensionIframeSync' && data.action == 'FORCE_REFRESH') {

            // Send confirmation to iframe that message has been received 
            e.source.postMessage(JSON.stringify({
                messageReceived: true
            }), '*');

            // Initialize reconnect
            DRAGDIS.sendMessage({
                Type: "RECONNECT"
            });
        }
    }
}, false);
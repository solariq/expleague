import QtQuick 2.7

Item {
    id: self
    property var webView: !!self.children[0] ? self.children[0].webView : null
//    property bool options: false
    anchors.fill: parent

    children: [owner.lastRequest.ui()]
}

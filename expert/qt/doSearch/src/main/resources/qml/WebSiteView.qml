import QtQuick 2.7

Item {
    id: self
    property var editor: children[0].editor
    property var webView: children[0].webView
    property var options: children[0].options

    onOptionsChanged: {
        if (self.children[0].options !== options)
            self.children[0].options = options
    }

    anchors.fill: parent
    children: [owner.root.ui()]

    onChildrenChanged: {
        if (children.length === 0) {
            owner.root.ui().parent = self
        }
    }
}

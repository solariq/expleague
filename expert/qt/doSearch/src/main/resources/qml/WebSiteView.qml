import QtQuick 2.7

Item {
    id: self
    property var editor: owner.root.ui.editor
    property var webView: owner.root.ui.webView
    property var options: owner.root.ui.options
    property string pageSearch: ""

    onPageSearchChanged: {
        owner.root.ui.pageSearch = pageSearch
    }

    onOptionsChanged: {
        if (self.children[0].options !== options)
            self.children[0].options = options
    }

    anchors.fill: parent
    children: [owner.root.ui]

    onChildrenChanged: {
        if (children.length === 0) {
            owner.root.ui.parent = self
        }
    }
}

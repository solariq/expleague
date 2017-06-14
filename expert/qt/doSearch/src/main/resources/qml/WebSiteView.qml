import QtQuick 2.7

Item {
    id: self
    //property var editor: owner.root.ui.editor
    property var webView: owner.root.ui.webView
    property var options: owner.root.ui.options
    property string pageSearch: ""

    visible: false

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
        }else{
            owner.root.ui.visible = visible
        }
    }

//    onActiveFocusChanged: {
//        if (!dosearch.main || !visible || activeFocus)
//            return

//        var parent = dosearch.main ? dosearch.main.activeFocusItem : null
//        while (parent) {
//            if (parent === self) {
//                console.log("Enforce focus to self " + self + " from child view")
//                self.forceActiveFocus()
//                return
//            }
//            parent = parent.parent
//        }
//        if (!dosearch.main.activeFocusItem || dosearch.main.activeFocusItem.toString().search("QtWebEngineCore::") !== -1) {
//            console.log("Enforce focus to self " + self + " from web view " + dosearch.main.activeFocusItem)
//            self.forceActiveFocus()
//        }
//        else {
//            if (!focus)
//                console.log("Focus given from " + self + " (" + owner + ") to " + dosearch.main.activeFocusItem)
//        }
//    }

    onVisibleChanged: {
        console.log("set visible of web site on ", visible)
        owner.root.ui.visible = visible
    }
}

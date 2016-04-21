import QtQuick 2.0
import QtQuick.Layouts 1.1
import QtWebEngine 1.2

Item {
    id: root
    property Item myParent
    anchors.fill: parent

    ColumnLayout {
        anchors.fill: parent
//        RowLayout {
//            Layout.fillWidth: true
//            Text {
//                text: "Width: " + (root.parent !== null ? root.parent.width : "No parent")
//            }
//            Text {
//                text: "Height: " + (root.parent !== null ? root.parent.height : "No parent")
//            }
//        }

        WebEngineView {
            id: webView
            objectName: "webView"
            focus: true
            Layout.fillWidth: true
            Layout.fillHeight: true
            onNewViewRequested: {
                if (!request.userInitiated)
                    print("Warning: Blocked a popup window.")
                else request.openIn(this)
    //            else if (request.destination == WebEngineView.NewViewInTab) {
    //                var tab = tabs.createEmptyTab(currentWebView.profile)
    //                tabs.currentIndex = tabs.count - 1
    //                request.openIn(tab.item)
    //            } else if (request.destination == WebEngineView.NewViewInBackgroundTab) {
    //                var tab = tabs.createEmptyTab(currentWebView.profile)
    //                request.openIn(tab.item)
    //            } else if (request.destination == WebEngineView.NewViewInDialog) {
    //                var dialog = applicationRoot.createDialog(currentWebView.profile)
    //                request.openIn(dialog.currentWebView)
    //            } else {
    //                var window = applicationRoot.createWindow(currentWebView.profile)
    //                request.openIn(window.currentWebView)
    //            }
            }

        }
    }
}

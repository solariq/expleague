import QtQuick 2.5
import QtQuick.Layouts 1.1
import QtQuick.Controls 1.4
import QtWebEngine 1.2
import QtQuick.Window 2.0

import ExpLeague 1.0
Item {
    id: root
    objectName: "root"
    property Item myParent
    anchors.fill: parent
    property WebScreen owner

    Action {
        shortcut: StandardKey.Refresh
        onTriggered: {
            if (webView)
                webView.reload()
        }
    }
    Action {
        shortcut: StandardKey.AddTab
        onTriggered: {
            tabs.createEmptyTab(webView.profile)
            tabs.currentIndex = tabs.count - 1
            addressBar.forceActiveFocus();
            addressBar.selectAll();
        }
    }
    Action {
        shortcut: StandardKey.Close
        onTriggered: {
            webView.triggerWebAction(WebEngineView.RequestClose);
        }
    }
//    Action {
//        shortcut: "Escape"
//        onTriggered: {
//            if (currentWebView.state == "FullScreen") {
//                browserWindow.visibility = browserWindow.previousVisibility
//                fullScreenNotification.hide()
//                currentWebView.triggerWebAction(WebEngineView.ExitFullScreen);
//            }
//        }
//    }
    Action {
        shortcut: "Ctrl+0"
        onTriggered: webView.zoomFactor = 1.0;
    }
    Action {
        shortcut: StandardKey.ZoomOut
        onTriggered: webView.zoomFactor -= 0.1;
    }
    Action {
        shortcut: StandardKey.ZoomIn
        onTriggered: webView.zoomFactor += 0.1;
    }

    Action {
        shortcut: StandardKey.Copy
        onTriggered: webView.triggerWebAction(WebEngineView.Copy)
    }
    Action {
        shortcut: StandardKey.Cut
        onTriggered: webView.triggerWebAction(WebEngineView.Cut)
    }
    Action {
        shortcut: StandardKey.Paste
        onTriggered: webView.triggerWebAction(WebEngineView.Paste)
    }
    Action {
        shortcut: "Shift+"+StandardKey.Paste
        onTriggered: webView.triggerWebAction(WebEngineView.PasteAndMatchStyle)
    }
    Action {
        shortcut: StandardKey.SelectAll
        onTriggered: webView.triggerWebAction(WebEngineView.SelectAll)
    }
    Action {
        shortcut: StandardKey.Undo
        onTriggered: webView.triggerWebAction(WebEngineView.Undo)
    }
    Action {
        shortcut: StandardKey.Redo
        onTriggered: webView.triggerWebAction(WebEngineView.Redo)
    }
    Action {
        shortcut: StandardKey.Back
        onTriggered: webView.triggerWebAction(WebEngineView.Back)
    }
    Action {
        shortcut: StandardKey.Forward
        onTriggered: webView.triggerWebAction(WebEngineView.Forward)
    }

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

            settings {
                javascriptCanAccessClipboard: true
                pluginsEnabled: true
            }

            onNewViewRequested: {
                if (!request.userInitiated)
                    print("Warning: Blocked a popup window.")
                else request.openIn(root.owner.landing())
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

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

    ColumnLayout {
        anchors.fill: parent

        WebEngineView {
            id: webView
            property string html

            onHtmlChanged: {
                loadHtml(html)
            }

            function find(text) {
                findText(text)
            }

            objectName: "webView"
            focus: true
            Layout.fillWidth: true
            Layout.fillHeight: true

            settings {
                autoLoadImages: true
                javascriptEnabled: true
                errorPageEnabled: false

                fullScreenSupportEnabled: false

                javascriptCanAccessClipboard: true
                pluginsEnabled: true
            }

            profile.httpUserAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/45.0.2454.101 Safari/537.36"

            onNewViewRequested: {
                if (!request.userInitiated)
                    print("Warning: Blocked a popup window.")
                else request.openIn(root.owner.landing(request.destination !== WebEngineView.NewViewInBackgroundTab))
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
//    Component.onDestruction: {
//        console.log("Trigger RequestClose")
//        webView.triggerWebAction(WebEngineView.RequestClose)
//    }
}

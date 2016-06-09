import QtQuick 2.5
import QtWebEngine 1.4

Item {
    property var webView: webView
    property string title: webView.title
    property var statusText

    anchors.fill: parent

    WebEngineView {
        id: webView
        anchors.fill: parent
        focus: true

        onLinkHovered: {
            if (hoveredUrl == "")
                resetStatusText.start()
            else {
                resetStatusText.stop()
                statusText.text = hoveredUrl
            }
        }

        states: [
            State {
                name: "FullScreen"
                PropertyChanges {
                    target: tabs
                    frameVisible: false
                    tabsVisible: false
                }
                PropertyChanges {
                    target: navigationBar
                    visible: false
                }
            }
        ]

        onCertificateError: {
            error.defer()
            sslDialog.enqueue(error)
        }

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

        onFullScreenRequested: {
            if (request.toggleOn) {
                webEngineView.state = "FullScreen"
                browserWindow.previousVisibility = browserWindow.visibility
                browserWindow.showFullScreen()
                fullScreenNotification.show()
            } else {
                webEngineView.state = ""
                browserWindow.visibility = browserWindow.previousVisibility
                fullScreenNotification.hide()
            }
            request.accept()
        }

        onRenderProcessTerminated: {
            var status = ""
            switch (terminationStatus) {
            case WebEngineView.NormalTerminationStatus:
                status = "(normal exit)"
                break;
            case WebEngineView.AbnormalTerminationStatus:
                status = "(abnormal exit)"
                break;
            case WebEngineView.CrashedTerminationStatus:
                status = "(crashed)"
                break;
            case WebEngineView.KilledTerminationStatus:
                status = "(killed)"
                break;
            }

            print("Render process exited with code " + exitCode + " " + status)
            reloadTimer.running = true
        }

        onWindowCloseRequested: {
            if (tabs.count == 1)
                browserWindow.close()
            else
                tabs.removeTab(tabs.currentIndex)
        }

        Timer {
            id: reloadTimer
            interval: 0
            running: false
            repeat: false
            onTriggered: currentWebView.reload()
        }
    }
}

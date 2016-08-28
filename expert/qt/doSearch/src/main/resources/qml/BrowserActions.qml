pragma Singleton

import QtQuick 2.5
import QtQuick.Controls 1.4

import QtWebEngine 1.2

import ExpLeague 1.0

Item {
    property WebEngineView webView: {
        var screen = root.context.folder.screen
        if (!screen)
            return null
        var name = screen.toString()
        if (name.indexOf("WebScreen") >= 0 || name.indexOf("WebSearch") >= 0) {
            return screen.webView
        }
        return null
    }

//    Action {
//        id: refreshAction
//        text: qsTr("Обновить страницу")
//        shortcut: StandardKey.Refresh
//        enabled: webView
//        onTriggered: {
//            if (webView)
//                webView.reload()
//        }
//    }
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

}

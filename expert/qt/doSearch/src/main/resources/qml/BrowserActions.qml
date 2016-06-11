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

    Action {
        id: refreshAction
        text: qsTr("Обновить страницу")
        shortcut: StandardKey.Refresh
        enabled: webView
        onTriggered: {
            if (webView)
                webView.reload()
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
        id: resetZoomAction
        text: qsTr("Вернуть исходный масштаб")
        shortcut: "Ctrl+0"
        enabled: webView
        onTriggered: webView.zoomFactor = 1.0;
    }

    Action {
        id: zoomInAction
        text: qsTr("Увеличить масштаб")
        shortcut: StandardKey.ZoomOut
        onTriggered: webView.zoomFactor -= 0.1;
    }

    Action {
        id: zoomOutAction
        text: qsTr("Уменьшить масштаб")
        shortcut: StandardKey.ZoomIn
        onTriggered: webView.zoomFactor += 0.1;
    }

    Action {
        shortcut: StandardKey.Back
        onTriggered: webView.triggerWebAction(WebEngineView.Back)
    }
    Action {
        shortcut: StandardKey.Forward
        onTriggered: webView.triggerWebAction(WebEngineView.Forward)
    }

}

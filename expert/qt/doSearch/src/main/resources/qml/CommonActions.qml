import QtQuick 2.5
import QtQuick.Controls 1.4

import QtWebEngine 1.2

import ExpLeague 1.0

Item {
    id: self
    property alias instance: self
    property Action closeTab: closeTabAction
    property Action copy: copyAction
    property Action paste: pasteAction
    property Action cut: cutAction
    property Action selectAll: selectAllAction
    property Action undo: undoAction
    property Action redo: redoAction
    property Action reload: reloadAction
    property Action saveToVault: saveToVaultAction
    property Action searchOnPage: searchOnPageAction
    property Action searchInternet: searchInternetAction
    property Action searchSite: searchSiteAction
    property Action resetZoom: resetZoomAction
    property Action zoomIn: zoomInAction
    property Action zoomOut: zoomOutAction
    property Action showHistory: showHistoryAction
    property Action exitFullScreen: exitFullScreenAction
    property Action showEditor: showEditorAction
    property Action showVault: showVaultAction

    property QtObject screen: {
        return root.navigation.activeScreen
    }

    property QtObject page: {
        return root.navigation.activePage
    }

    property WebEngineView webView: {
        if (!screen || !screen.webView)
            return null
        return screen.webView
    }

    property TextEdit editor: {
        if (screen && screen.editor) {
            return screen.editor
        }
        return null
    }

    property var omnibox: {
        if (root.main)
            return root.main.omnibox
        return null
    }

    function focusWebView() {
        var focus = root.main.activeFocusItem
        while (focus && focus.toString().indexOf("QQuickWebEngineView") < 0) {
            focus = focus.parent
        }
        return focus
    }

    function focusEditor() {
        var focus = root.main.activeFocusItem
        if (focus.toString().indexOf("QQuickTextEdit") < 0)
            return null
        return focus
    }

    Action {
        id: closeTabAction
        text: qsTr("Закрыть таб")
        shortcut: StandardKey.Close
        enabled: page
        onTriggered: {
            if (page && dosearch.navigation.activeGroup) {
                dosearch.navigation.close(dosearch.navigation.activeGroup, page)
            }
        }
    }

    Action {
        id: copyAction
        text: qsTr("Скопировать")
        shortcut: StandardKey.Copy
        enabled: true//(editor && editor.selectionStart != editor.selectionEnd) || webView
        onTriggered: {
            var focusedWeb = focusWebView()
            var focusedEditor = focusEditor()
            if (focusedWeb) {
                focusedWeb.triggerWebAction(WebEngineView.Copy)
            }
            else if (focusedEditor) {
                focusedEditor.copy()
            }
            else if (editor && editor.selectionStart != editor.selectionEnd) {
                editor.copy()
            }
            else if (webView) {
                webView.triggerWebAction(WebEngineView.Copy)
            }
        }
    }

    Action {
        id: cutAction
        text: qsTr("Вырезать")
        shortcut: StandardKey.Cut
        enabled: editor || webView
        onTriggered: {
            if (!!webView && !!webView["transfer"] && webView.transfer(shortcut)) {
                return
            }
            else if (webView) {
                webView.triggerWebAction(WebEngineView.Cut)
            }
            else if (editor) {
                editor.cut()
            }
        }
    }
    Action {
        id: pasteAction
        text: qsTr("Вставить")
        shortcut: StandardKey.Paste

        enabled: true //editor || webView
        onTriggered: {
            if (!!webView && !!webView["transfer"] && webView.transfer(shortcut)) {
                return
            }
            else if (!!webView) {
                webView.triggerWebAction(WebEngineView.Paste)
            }
            else if (!!editor) {
                if (!!editor['pasteMD'])
                    editor.pasteMD()
                else
                    editor.paste()
            }
        }
    }

    Action {
        id: selectAllAction
        text: qsTr("Выбрать все")
        shortcut: StandardKey.SelectAll
        enabled: webView || editor
        onTriggered: {
            if (!!webView && !!webView["transfer"] && webView.transfer(shortcut)) {
                return
            }
            else if (!!webView) {
                webView.triggerWebAction(WebEngineView.SelectAll)
            }
            else if (editor) {
                editor.selectAll()
            }
        }
    }

    Action {
        id: undoAction
        text: qsTr("Отменить операцию")
        shortcut: StandardKey.Undo
        enabled: webView || (editor && editor.canUndo)
        onTriggered: {
            if (!!webView && !!webView["transfer"] && webView.transfer(shortcut)) {
                return
            }
            else if (!!webView) {
                webView.triggerWebAction(WebEngineView.Undo)
            }
            else if (editor && editor.canUndo) {
                editor.undo()
            }
        }
    }

    Action {
        id: redoAction
        text: qsTr("Снова применить операцию")
        shortcut: StandardKey.Redo
        enabled: webView || (editor && editor.canRedo)
        onTriggered: {
            if (!!webView && !!webView["transfer"] && webView.transfer(shortcut)) {
                return
            }
            else if (!!webView) {
                webView.triggerWebAction(WebEngineView.Redo)
            }
            else if (editor && editor.canRedo) {
                editor.redo()
            }
        }
    }

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
        id: reloadAction
        text: qsTr("Перегрузить страницу")
        shortcut: StandardKey.Refresh
        enabled: webView
        onTriggered: {
            if (webView) {
                webView.reloadAndBypassCache()
            }
        }
    }

    Action {
        id: searchOnPageAction
        shortcut: "Ctrl+F"
        text: qsTr("Поиск на странице")
        onTriggered: {
            if (!omnibox.visible) {
                omnibox.select("page")
                dosearch.main.showDialog(omnibox)
            }
            else {
                omnibox.visible = false
            }
        }
    }

    Action {
        id: searchInternetAction
        shortcut: "Ctrl+N"
        text: qsTr("Поиск в интернете")
        iconSource: "qrc:/tools/search.png"
        onTriggered: {
            if (!omnibox.visible) {
                omnibox.select("internet")
                dosearch.main.showDialog(omnibox)
            }
            else {
                omnibox.visible = false
            }
        }
    }

    Action {
        id: searchSiteAction
        shortcut: "Ctrl+Shift+N"
        text: qsTr("Поиск на текущем сайте")
        onTriggered: {
            if (!omnibox.visible) {
                omnibox.select("site")
                dosearch.main.showDialog(omnibox)
            }
            else {
                omnibox.visible = false
            }
        }
    }

    Action {
        id: saveToVaultAction
        shortcut: StandardKey.Save
        enabled: !!webView
        text: qsTr("Сохранить в хранилище")
        onTriggered: {
            webView.triggerWebAction(WebEngineView.Copy)
            dosearch.navigation.context.vault.clearClipboard();
            dosearch.main.delay(500, function() {
                if (!dosearch.navigation.context.vault.paste(dosearch.navigation.activePage)) {
                    dosearch.navigation.context.vault.appendLink(dosearch.navigation.activePage.url)
                }
            })
        }
    }

    Action {
        id: showHistoryAction
        shortcut: "Ctrl+E"
        text: qsTr("Показать последние страницы")
        onTriggered: {
            dosearch.main.showHistory()
        }
    }

    Action {
        id: showEditorAction
        shortcut: "Ctrl+O"
        text: qsTr("Открыть последний редактор")
        iconSource: "qrc:/tools/editor.png"
        enabled: !!dosearch.navigation.context.task
        onTriggered: {
            dosearch.navigation.open(dosearch.navigation.context.task.answer)
        }
    }

    Action {
        id: showVaultAction
        shortcut: "Ctrl+D"
        text: qsTr("Открыть хранилище")
        iconSource: "qrc:/tools/vault.png"
        onTriggered: {
            if (dosearch.main.sidebarRef.state != "vault")
                dosearch.main.sidebarRef.state = "vault"
            else
                dosearch.main.sidebarRef.state = ""
        }
    }

    Action {
        id: exitFullScreenAction
        shortcut: "Escape"
        enabled: dosearch.main.screenRef.state === "FullScreen"
        text: qsTr("Выйти из полноэкранного режима")
        onTriggered: {
            webView.triggerWebAction(WebEngineView.ExitFullScreen)
        }
    }
}

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
    property Action searchOnPage: searchOnPageAction
    property Action searchInternet: searchInternetAction
    property Action searchSite: searchSiteAction

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
            if (webView) {
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
        enabled: (editor && editor.canPaste) || webView
        onTriggered: {
            if (webView) {
                webView.triggerWebAction(WebEngineView.Paste)
            }
            else if (editor) {
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
            if (editor) {
                editor.selectAll()
            }
            else if (webView) {
                webView.triggerWebAction(WebEngineView.SelectAll)
            }
        }
    }

    Action {
        id: undoAction
        text: qsTr("Отменить операцию")
        shortcut: StandardKey.Undo
        enabled: webView || (editor && editor.canUndo)
        onTriggered: {
            if (editor) {
                editor.undo()
            }
            else if (webView) {
                webView.triggerWebAction(WebEngineView.Undo)
            }
        }
    }

    Action {
        id: redoAction
        text: qsTr("Снова применить операцию")
        shortcut: StandardKey.Redo
        enabled: webView || (editor && editor.canRedo)
        onTriggered: {
            if (editor) {
                editor.redo()
            }
            else if (webView) {
                webView.triggerWebAction(WebEngineView.Redo)
            }
        }
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
            omnibox.visible = true
            omnibox.select("page")
            omnibox.forceActiveFocus()
        }
    }

    Action {
        id: searchInternetAction
        shortcut: "Ctrl+S"
        text: qsTr("Поиск в интернете")
        onTriggered: {
            omnibox.visible = true
            omnibox.select("internet")
            omnibox.forceActiveFocus()
        }
    }

    Action {
        id: searchSiteAction
        shortcut: "Ctrl+Shift+S"
        text: qsTr("Поиск на текущем сайте")
        onTriggered: {
            omnibox.visible = true
            omnibox.select("site")
            omnibox.forceActiveFocus()
        }
    }
}

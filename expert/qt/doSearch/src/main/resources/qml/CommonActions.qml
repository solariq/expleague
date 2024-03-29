import QtQuick 2.5
import QtQuick.Controls 1.4

//import QtWebEngine 1.2

import ExpLeague 1.0

Item {
    id: self
    property alias instance: self
    property Action closeTab: closeTabAction
    property Action copy: copyAction
    property Action copyToEditor: copyToEditorAction
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
    property Action issueUrl: issueUrlAction
    property Action resetZoom: resetZoomAction
    property Action zoomIn: zoomInAction
    property Action zoomOut: zoomOutAction
    property Action showHistory: showHistoryAction
    //property Action exitFullScreen: exitFullScreenAction
    property Action showEditor: showEditorAction
    property Action showVault: showVaultAction
    property Action gotoAdmin: gotoAdminAction

    property QtObject screen: {
        return root.navigation.activeScreen
    }

    property QtObject page: {
        return root.navigation.activePage
    }

    property CefItem webView: {
        if (!screen || !screen.webView)
            return null
        return screen.webView
    }

    property var document: !!dosearch.navigation.context ? dosearch.navigation.context.document : null
    property TextEdit editor: document ? document.ui.editor : null

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
        shortcut: "Ctrl+W"
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
                focusedWeb.copy()
            }
            else if (focusedEditor) {
                focusedEditor.copy()
            }
            else if (editor && editor.selectionStart != editor.selectionEnd) {
                editor.copy()
            }
            else if (webView) {
                webView.copy()
            }
        }
    }

    Action {
        id: copyToEditorAction
        text: qsTr("Скопировать в редактор")
        shortcut: "Ctrl+Shift+C"
        enabled: !!dosearch.navigation.context.document
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
                webView.copy()
            }
            var document
            if (!!dosearch.navigation.context.task)
                document = dosearch.navigation.context.task.answer
            else
                document = dosearch.navigation.context.document
            dosearch.main.delay(100, function () {
                if (document)
                    document.ui.editor.pasteMD()
            })
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
                webView.cut()
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
            /*if (!!webView && !!webView["transfer"] && webView.transfer(shortcut)) {
                return
            }
            else*/
            if (!!webView) {
                webView.paste()
            }
            else
            if (!!editor) {
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
                webView.selectAll()
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
                webView.undo()
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
                webView.redo()
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
        onTriggered: webView.zoomFactor = 0;
    }

    Action {
        id: zoomInAction
        text: qsTr("Увеличить масштаб")
        shortcut: StandardKey.ZoomOut
        onTriggered: webView.zoomFactor -= 0.2;
    }

    Action {
        id: zoomOutAction
        text: qsTr("Уменьшить масштаб")
        shortcut: StandardKey.ZoomIn
        onTriggered: webView.zoomFactor += 0.2;
    }

    Action {
        id: reloadAction
        text: qsTr("Перегрузить страницу")
        shortcut: StandardKey.Refresh
        enabled: webView
        onTriggered: {
            if (webView) {
                webView.reload()
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
        property string highlightedIcon: "qrc:/tools/search_h.png"
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
        id: issueUrlAction
        shortcut: "Ctrl+Alt+N"
        text: qsTr("Переход по URL")
        onTriggered: {
            if (!omnibox.visible) {
                omnibox.select("url")
                dosearch.main.showDialog(omnibox)
            }
            else {
                omnibox.visible = false
            }
        }
    }

    Action {
        id: gotoAdminAction
        shortcut: "Ctrl+Shift+A"
        text: qsTr("Переход к админке")
        enabled: dosearch.league.role == League.ADMIN
        onTriggered: {
            dosearch.navigation.open(dosearch.league.chat)
        }
    }

    Action {
        id: saveToVaultAction
        shortcut: StandardKey.Save
        enabled: !!webView
        text: qsTr("Сохранить в хранилище")
        onTriggered: {
            webView.copy()
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
        property string highlightedIcon: "qrc:/tools/editor_h.png"
        property string disabledIcon: "qrc:/tools/editor_d.png"

        enabled: !!dosearch.navigation.context.document
        onTriggered: {
            if (!!dosearch.navigation.context.task)
                dosearch.navigation.open(dosearch.navigation.context.task.answer)
            else
                dosearch.navigation.open(dosearch.navigation.context.document)
        }
    }

    Action {
        id: showVaultAction
        shortcut: "Ctrl+D"
        text: qsTr("Открыть хранилище")
        iconSource: "qrc:/tools/vault.png"
        property string highlightedIcon: "qrc:/tools/vault_h.png"
        onTriggered: {
            if (dosearch.main.sidebarRef.state != "vault")
                dosearch.main.sidebarRef.state = "vault"
            else
                dosearch.main.sidebarRef.state = ""
        }
    }


//    Action {
//        id: exitFullScreenAction
//        shortcut: "Escape"
//        enabled: dosearch.main.screenRef.state === "FullScreen"
//        text: qsTr("Выйти из полноэкранного режима")
//        onTriggered: {
//            webView.triggerWebAction(WebEngineView.ExitFullScreen)
//        }
//    }
}

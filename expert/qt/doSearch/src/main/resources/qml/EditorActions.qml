pragma Singleton

import QtQuick 2.5
import QtQuick.Controls 1.4

import ExpLeague 1.0

Item {
    id: self
    property alias instance: self
    property TextEdit editor: {
        var screen = root.navigation.activeScreen
        if (screen && screen.editor)
            return screen.editor
        return null
    }

    property alias makeBold: makeBoldAction
    property alias makeItalic: makeItalicAction
    property alias insertHeader3: insertHeader3Action
    property alias insertImage: insertImageAction
    property alias insertLink: insertLinkAction
    property alias insertSplitter: insertSplitterAction
    property alias makeCut: makeCutAction
    property alias insertCitation: insertCitationAction
    property alias makeEnumeration: makeEnumerationAction
    property alias makeList: makeListAction
    property alias insertTable: insertTableAction

    Action {
        id: makeBoldAction
        text: qsTr("Выделить болдом")
        tooltip: qsTr("Сделать фрагмент жирнее")
        iconSource: "qrc:/editor/bold.png"

        shortcut: "Ctrl+B"
        enabled: editor
        onTriggered: {
            editor.forceActiveFocus()
            var start = editor.selectionStart
            var text = editor.text.substring(editor.selectionStart, editor.selectionEnd)
            editor.remove(editor.selectionStart, editor.selectionEnd)
            if (!(text.indexOf("**") === 0 || text.lastIndexOf("**") === text.length - 2))
                editor.insert(start, "**" + text.trim() + "**")
            else
                editor.insert(start, text.substring(2, text.length - 2))

            editor.forceActiveFocus()
        }
    }

    Action {
        id: makeItalicAction
        text: qsTr("Выделить курсивом")
        tooltip: qsTr("Выделить курсивом")
        iconSource: "qrc:/editor/italic.png"

        enabled: editor
        shortcut: "Ctrl+I"
        onTriggered: {
            editor.forceActiveFocus()
            var start = editor.selectionStart
            var text = editor.text.substring(editor.selectionStart, editor.selectionEnd)
            editor.remove(editor.selectionStart, editor.selectionEnd)
            if (!(text.indexOf("*") === 0 || text.lastIndexOf("*") === text.length - 1))
                editor.insert(start, "*" + text.trim() + "*")
            else
                editor.insert(start, text.substring(1, text.length - 1))
            editor.forceActiveFocus()
        }
    }

    Action {
        id: insertHeader3Action
        text: qsTr("Заголовок 3-го уровня")
        tooltip: qsTr("Вставить заголовок 3-го уровня")
        iconSource: "qrc:/editor/header.png"

        enabled: editor
        shortcut: "Ctrl+H"
        onTriggered: {
            editor.forceActiveFocus()
            var start = editor.selectionStart
            editor.insert(start, "### ")
            editor.forceActiveFocus()
        }
    }

    Action {
        id: insertImageAction
        text: qsTr("Вставить картинку")
        tooltip: qsTr("Вставить картинку")
        iconSource: "qrc:/editor/image.png"

        enabled: editor
        shortcut: "Ctrl+P"
        onTriggered: {
            editor.forceActiveFocus()
            var start = editor.selectionStart
            editor.insert(start, "![]()")
            editor.cursorPosition = editor.cursorPosition - 1
            editor.forceActiveFocus()
        }
    }

    Action {
        id: insertLinkAction
        text: qsTr("Вставить ссылку")
        tooltip: qsTr("Вставить ссылку")
        iconSource: "qrc:/editor/link.png"

        enabled: editor
        shortcut: "Ctrl+L"
        onTriggered: {
            editor.forceActiveFocus()
            var start = editor.selectionStart
            var text = editor.text.substring(editor.selectionStart, editor.selectionEnd)
            editor.remove(editor.selectionStart, editor.selectionEnd)
            editor.insert(start, "[" + text.trim() + "]()")
            editor.cursorPosition = editor.cursorPosition - 1
            editor.forceActiveFocus()
        }
    }

    Action {
        id: insertSplitterAction
        text: qsTr("Вставить разделитель")
        tooltip: qsTr("Вставить разделитель")
        iconSource: "qrc:/editor/splitter.png"

        enabled: editor
        shortcut: "Ctrl+-"
        onTriggered: {
            editor.forceActiveFocus()
            var start = editor.selectionStart
            editor.insert(start, "----\n")
            editor.cursorPosition = editor.selectionStart + 5
            editor.forceActiveFocus()
        }
    }

    Action {
        id: makeCutAction
        text: qsTr("Создать cut")
        tooltip: qsTr("Создать кат")
        iconSource: "qrc:/editor/cut.png"

        enabled: editor
        shortcut: "Ctrl+M"
        onTriggered: {
            editor.forceActiveFocus()
            var start = editor.selectionStart
            var text = editor.text.substring(editor.selectionStart, editor.selectionEnd)
            editor.remove(editor.selectionStart, editor.selectionEnd)
            editor.insert(start, "+[" + text.trim() + "]\n\n")
            editor.insert(start + text.length + 4, "-[" + text.trim() + "]")
            editor.cursorPosition = editor.selectionStart - text.trim().length - 4
            editor.forceActiveFocus()
        }
    }

    Action {
        id: insertCitationAction
        text: qsTr("Вставить цитирование")
        tooltip: qsTr("Вставить цитирование")
        iconSource: "qrc:/editor/quote.png"

        enabled: editor
        shortcut: "Ctrl+J"
        onTriggered: {
            editor.forceActiveFocus()
            var start = editor.selectionStart
            editor.insert(start, "> ")
            editor.forceActiveFocus()
        }
    }

    Action {
        id: makeListAction
        text: qsTr("Создать список")
        tooltip: qsTr("Создать список")
        iconSource: "qrc:/editor/bullets.png"

        enabled: editor
        shortcut: "Ctrl+0"
        onTriggered: {
            editor.forceActiveFocus()
            var text = editor.text.substring(editor.selectionStart, editor.selectionEnd)
            var parts = text.split("\n")
            editor.remove(editor.selectionStart, editor.selectionEnd)
            var pos = editor.selectionStart
            for (var i = 0; i < parts.length; i++) {
                editor.insert(pos, "* " + parts[i] + "\n")
                pos += 3 + parts[i].length
            }

            editor.cursorPosition = editor.cursorPosition - 1
            editor.forceActiveFocus()
        }
    }

    Action {
        id: makeEnumerationAction
        text: qsTr("Создать нумерованный список")
        tooltip: qsTr("Создать нумерованный список")
        iconSource: "qrc:/editor/enum.png"

        enabled: editor
        shortcut: "Ctrl+1"
        onTriggered: {
            editor.forceActiveFocus()
            var text = editor.text.substring(editor.selectionStart, editor.selectionEnd)
            var parts = text.split("\n")
            editor.remove(editor.selectionStart, editor.selectionEnd)
            var pos = editor.selectionStart
            for (var i = 0; i < parts.length; i++) {
                editor.insert(pos, "1. " + parts[i] + "\n")
                pos += 4 + parts[i].length
            }

            editor.cursorPosition = pos - 1
            editor.forceActiveFocus()
        }
    }

    Action {
        id: insertTableAction
        text: qsTr("Вставить таблицу")
        tooltip: qsTr("Вставить таблицу")
        iconSource: "qrc:/editor/table.png"

        enabled: editor
        shortcut: "Ctrl+T"
        onTriggered: {
            editor.forceActiveFocus()
            var start = editor.selectionStart
            var pattern = "\n\n|   |   |\n|---|---|\n|   |   |\n\n"
            editor.insert(start, pattern)
            editor.cursorPosition = editor.cursorPosition - pattern.length + 3

            editor.forceActiveFocus()
        }
    }
}

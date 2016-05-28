import QtQuick 2.5
import QtQuick.Controls 1.4
import QtQuick.Layouts 1.1

import ExpLeague 1.0

import "."
Item {
    id: self

    anchors.fill: parent
    anchors.rightMargin: 3
    objectName: "root"

    property MarkdownEditorScreen owner

    function paste() {
        var coded = owner.codeClipboard()
        edit.remove(edit.selectionStart, edit.selectionEnd)

        for(var i = 0; i < coded.length; i++) {
            edit.insert(edit.cursorPosition, coded[i])
        }
    }

    Action {
        id: makeBold
        tooltip: "Сделать фрагмент жирнее"
        iconSource: "qrc:/icons/25x25/bold.png"

        shortcut: "Ctrl+B"
        onTriggered: {
            edit.forceActiveFocus()
            var start = edit.selectionStart
            var text = edit.text.substring(edit.selectionStart, edit.selectionEnd)
            edit.remove(edit.selectionStart, edit.selectionEnd)
            if (!(text.indexOf("**") === 0 || text.lastIndexOf("**") === text.length - 2))
                edit.insert(start, "**" + text + "**")
            else
                edit.insert(start, text.substring(2, text.length - 2))

            edit.forceActiveFocus()
        }
    }

    Action {
        id: makeItalic
        tooltip: "Выделить курсивом"
        iconSource: "qrc:/icons/25x25/italic.png"

        shortcut: "Ctrl+I"
        onTriggered: {
            edit.forceActiveFocus()
            var start = edit.selectionStart
            var text = edit.text.substring(edit.selectionStart, edit.selectionEnd)
            edit.remove(edit.selectionStart, edit.selectionEnd)
            if (!(text.indexOf("*") === 0 || text.lastIndexOf("*") === text.length - 1))
                edit.insert(start, "*" + text + "*")
            else
                edit.insert(start, text.substring(1, text.length - 1))
            edit.forceActiveFocus()
        }
    }

    Action {
        id: insertHeader3
        tooltip: "Вставить заголовок 3-го уровня"
        iconSource: "qrc:/icons/25x25/header.png"

        shortcut: "Ctrl+H"
        onTriggered: {
            edit.forceActiveFocus()
            var start = edit.selectionStart
            edit.insert(start, "### ")
            edit.forceActiveFocus()
        }
    }

    Action {
        id: insertImage
        tooltip: "Вставить картинку"
        iconSource: "qrc:/icons/25x25/image.png"

        shortcut: "Ctrl+P"
        onTriggered: {
            edit.forceActiveFocus()
            var start = edit.selectionStart
            edit.insert(start, "!()[]")
            edit.cursorPosition = edit.cursorPosition - 1
            edit.forceActiveFocus()
        }
    }

    Action {
        id: insertLink
        tooltip: "Вставить ссылку"
        iconSource: "qrc:/icons/25x25/link.png"

        shortcut: "Ctrl+L"
        onTriggered: {
            edit.forceActiveFocus()
            var start = edit.selectionStart
            edit.insert(start, "()[]")
            edit.cursorPosition = edit.cursorPosition - 1
            edit.forceActiveFocus()
        }
    }

    Action {
        id: insertSplitter
        tooltip: "Вставить разделитель"
        iconSource: "qrc:/icons/25x25/splitter.png"

        shortcut: "Ctrl+-"
        onTriggered: {
            edit.forceActiveFocus()
            var start = edit.selectionStart
            edit.insert(start, "----\n")
            edit.cursorPosition = edit.selectionStart + 5
            edit.forceActiveFocus()
        }
    }

    Action {
        id: makeCut
        tooltip: "Создать кат"
        iconSource: "qrc:/icons/25x25/cut.png"

        shortcut: "Ctrl+M"
        onTriggered: {
            edit.forceActiveFocus()
            var start = edit.selectionStart
            var text = edit.text.substring(edit.selectionStart, edit.selectionEnd)
            edit.remove(edit.selectionStart, edit.selectionEnd)
            edit.insert(start, "+[" + text + "]\n\n")
            edit.insert(start + text.length + 4, "-[" + text + "]")
            edit.cursorPosition = edit.selectionStart - text.length - 4
            edit.forceActiveFocus()
        }
    }

    Action {
        id: insertCitation
        tooltip: "Вставить цитирование"
        iconSource: "qrc:/icons/25x25/quote.png"

        shortcut: "Ctrl+J"
        onTriggered: {
            edit.forceActiveFocus()
            var start = edit.selectionStart
            edit.insert(start, "> ")
            edit.forceActiveFocus()
        }
    }

    Action {
        id: makeList
        tooltip: "Создать список"
        iconSource: "qrc:/icons/25x25/bullets.png"

        shortcut: "Ctrl+0"
        onTriggered: {
            edit.forceActiveFocus()
            var text = edit.text.substring(edit.selectionStart, edit.selectionEnd)
            var parts = text.split("\n")
            edit.remove(edit.selectionStart, edit.selectionEnd)
            var pos = edit.selectionStart
            for (var i = 0; i < parts.length; i++) {
                edit.insert(pos, "* " + parts[i] + "\n")
                pos += 3 + parts[i].length
            }

            edit.cursorPosition = edit.cursorPosition - 1
            edit.forceActiveFocus()
        }
    }

    Action {
        id: makeEnumeration
        tooltip: "Создать список"
        iconSource: "qrc:/icons/25x25/enum.png"

        shortcut: "Ctrl+1"
        onTriggered: {
            edit.forceActiveFocus()
            var text = edit.text.substring(edit.selectionStart, edit.selectionEnd)
            var parts = text.split("\n")
            edit.remove(edit.selectionStart, edit.selectionEnd)
            var pos = edit.selectionStart
            for (var i = 0; i < parts.length; i++) {
                edit.insert(pos, "1. " + parts[i] + "\n")
                pos += 4 + parts[i].length
            }

            edit.cursorPosition = pos - 1
            edit.forceActiveFocus()
        }
    }

    Action {
        id: insertTable
        tooltip: "Вставить таблицу"
        iconSource: "qrc:/icons/25x25/table.png"

        shortcut: "Ctrl+T"
        onTriggered: {
            edit.forceActiveFocus()
            var start = edit.selectionStart
            var pattern = "\n\n|   |   |\n|---|---|\n|   |   |\n\n"
            edit.insert(start, pattern)
            edit.cursorPosition = edit.cursorPosition - pattern.length + 3

            edit.forceActiveFocus()
        }
    }


    ColumnLayout {
        anchors.fill: parent
        spacing: 0
        Rectangle {
            Layout.preferredHeight: 33
            Layout.fillWidth: true

            color: Palette.backgroundColor
            RowLayout {
                anchors.fill: parent
                id: buttons
                spacing: 5
//                Item {Layout.preferredWidth: 0}
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: makeBold
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: makeItalic
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: insertHeader3
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: insertImage
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: insertLink
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: insertSplitter
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: makeCut
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: insertCitation
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: makeList
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: makeEnumeration
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: insertTable
                }

                Item {Layout.fillWidth: true}
            }
        }

        Rectangle {
            id: editorBox
            Layout.fillHeight: true
            Layout.fillWidth: true

            color: "white"

            ScrollView {
                id:  scroll
                anchors.fill: parent
                frameVisible: true
                flickableItem.anchors.margins: 4
                function ensureVisible(r) {
                    if (flickableItem.contentX >= r.x)
                        flickableItem.contentX = r.x;
                    else if (flickableItem.contentX+width <= r.x+r.width)
                        flickableItem.contentX = r.x+r.width-width;
                    if (flickableItem.contentY >= r.y)
                        flickableItem.contentY = r.y;
                    else if (flickableItem.contentY+height <= r.y+r.height)
                        flickableItem.contentY = r.y+r.height-height;
                }
                TextEdit {
                    id: edit
                    objectName: "editor"

//                    width: Math.max(implicitWidth, parent.width)
//                    height: Math.max(implicitHeight, parent.height)
                    width: Math.max(scroll.width - 8, implicitWidth)
                    height: Math.max(scroll.height - 8, implicitHeight)
                    anchors.centerIn: parent
                    focus: true
                    wrapMode: TextEdit.Wrap
                    selectByMouse: true
                    onCursorRectangleChanged: scroll.ensureVisible(cursorRectangle)
                    Keys.onPressed: {
                        var control = (event.modifiers & (Qt.ControlModifier | Qt.MetaModifier)) != 0
                        var shift = (event.modifiers & Qt.ShiftModifier) != 0
                        if (event.key === Qt.Key_V && control || event.key === Qt.Key_Insert && shift) {
                            self.paste()
                            event.accepted = true
                        }
                    }
                }
            }
        }
    }
}

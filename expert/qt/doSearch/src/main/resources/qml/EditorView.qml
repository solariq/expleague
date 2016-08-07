import QtQuick 2.7
import QtQuick.Controls 2.0
import QtQuick.Layouts 1.1

import ExpLeague 1.0

import "."
Column {
    id: self
    anchors.fill: parent
    property alias editor: edit
    Item {
        id: buttons
        height: 33
        width: parent.width

        RowLayout {
            anchors.fill: parent
            spacing: 5
            Item {Layout.preferredWidth: 1}
            ToolbarButton {
                Layout.alignment: Qt.AlignVCenter
                action: EditorActions.makeBold
            }
            ToolbarButton {
                Layout.alignment: Qt.AlignVCenter
                action: EditorActions.makeItalic
            }
            ToolbarButton {
                Layout.alignment: Qt.AlignVCenter
                action: EditorActions.insertHeader3
            }
            ToolbarButton {
                Layout.alignment: Qt.AlignVCenter
                action: EditorActions.insertImage
            }
            ToolbarButton {
                Layout.alignment: Qt.AlignVCenter
                action: EditorActions.insertLink
            }
            ToolbarButton {
                Layout.alignment: Qt.AlignVCenter
                action: EditorActions.insertSplitter
            }
            ToolbarButton {
                Layout.alignment: Qt.AlignVCenter
                action: EditorActions.makeCut
            }
            ToolbarButton {
                Layout.alignment: Qt.AlignVCenter
                action: EditorActions.insertCitation
            }
            ToolbarButton {
                Layout.alignment: Qt.AlignVCenter
                action: EditorActions.makeList
            }
            ToolbarButton {
                Layout.alignment: Qt.AlignVCenter
                action: EditorActions.makeEnumeration
            }
            ToolbarButton {
                Layout.alignment: Qt.AlignVCenter
                action: EditorActions.insertTable
            }

            Item {Layout.fillWidth: true}
        }
    }
    Item {height: 3}

    Rectangle {
        id: editorBox
        width: parent.width
        height: parent.height - buttons.height
        color: "white"

        Flickable {
            id:  scroll
            anchors.fill: parent
            anchors.margins: 2
            flickableDirection: Flickable.VerticalFlick
            clip: true
            interactive: true
            contentHeight: edit.height

            function ensureVisible(r) {
                if (contentY >= r.y)
                    contentY = r.y;
                else if (contentY+height <= r.y+r.height)
                    contentY = r.y+r.height-height;
            }

            TextEdit {
                id: edit
                width: scroll.width - 8
                height: Math.max(scroll.height - 8, implicitHeight)
                focus: true
                wrapMode: TextEdit.Wrap
                selectByMouse: true
                onCursorRectangleChanged: scroll.ensureVisible(cursorRectangle)
                renderType: Text.NativeRendering
                font.pointSize: 14

                function paste() {
                    var coded = owner.codeClipboard()
                    edit.remove(edit.selectionStart, edit.selectionEnd)

                    for(var i = 0; i < coded.length; i++) {
                        edit.insert(edit.cursorPosition, coded[i])
                    }
                }
            }
        }
    }
    onVisibleChanged: {
        if (visible)
            editor.forceActiveFocus()
    }
}

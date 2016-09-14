import QtQuick 2.7
import QtQuick.Controls 2.0
import QtQuick.Layouts 1.1

import ExpLeague 1.0

import "."
Rectangle {
    anchors.fill: parent
    id: self
    Column {
        property var editorActions: {
            return dosearch.main.editorActionsRef
        }
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
                    action: editorActions.makeBold
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.makeItalic
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.insertHeader3
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.insertImage
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.insertLink
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.insertSplitter
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.makeCut
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.insertCitation
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.makeList
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.makeEnumeration
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.insertTable
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

                    Keys.onPressed: {
                        if (event.key === Qt.Key_V && (event.modifiers & (Qt.ControlModifier | Qt.MetaModifier)) != 0) {
                            event.accepted = true
                            pasteMD()
                        }
                    }

                    function pasteMD() {
                        var coded = owner.codeClipboard()
                        edit.remove(edit.selectionStart, edit.selectionEnd)

                        for(var i = 0; i < coded.length; i++) {
                            edit.insert(edit.cursorPosition, coded[i])
                        }
                    }
                }
            }
            DropArea {
                anchors.fill: parent
                z: parent.z + 10
                //            keys: ["text/plain"]

                onDropped: {
                    if (drop.hasText) {
                        edit.remove(edit.selectionStart, edit.selectionEnd)
                        edit.insert(editor.cursorPosition, drop.text)
                        drop.accept()
                    }
                }

                onPositionChanged: {
                    editor.cursorPosition = editor.positionAt(scroll.contentX + drag.x, scroll.contentY + drag.y)
                }
            }
        }

        onVisibleChanged: {
            if (visible)
                editor.forceActiveFocus()
        }
    }
}

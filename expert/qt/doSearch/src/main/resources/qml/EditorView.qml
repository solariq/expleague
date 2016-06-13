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
                    r.x += 4
                    r.y += 4
                    if (flickableItem.contentX > r.x)
                        flickableItem.contentX = r.x;
                    else if (flickableItem.contentX+width < r.x+r.width)
                        flickableItem.contentX = r.x + r.width - width;
                    if (flickableItem.contentY > r.y)
                        flickableItem.contentY = r.y;
                    else if (flickableItem.contentY + height < r.y + r.height + 8)
                        flickableItem.contentY = r.y + r.height - height + 8;
                }
                TextEdit {
                    id: edit
                    objectName: "editor"

//                    width: Math.max(implicitWidth, parent.width)
//                    height: Math.max(implicitHeight, parent.height)
                    width: scroll.width - 8
                    height: Math.max(scroll.height - 8, implicitHeight)
                    anchors.centerIn: parent
                    focus: true
                    wrapMode: TextEdit.Wrap
                    selectByMouse: true
                    onCursorRectangleChanged: scroll.ensureVisible(cursorRectangle)
                    renderType: Text.NativeRendering
                    function paste() {
                        console.log("Paste called")
                        var coded = owner.codeClipboard()
                        edit.remove(edit.selectionStart, edit.selectionEnd)

                        for(var i = 0; i < coded.length; i++) {
                            edit.insert(edit.cursorPosition, coded[i])
                        }
                    }

                    Keys.onPressed: {
                        var control = (event.modifiers & (Qt.ControlModifier | Qt.MetaModifier)) != 0
                        var shift = (event.modifiers & Qt.ShiftModifier) != 0
                        if (event.key === Qt.Key_V && control || event.key === Qt.Key_Insert && shift) {
                            edit.paste()
                            event.accepted = true
                        }
                    }
                }
            }
        }
    }
}

import QtQuick 2.5
import QtQuick.Controls 1.4

import ExpLeague 1.0
Rectangle {
    id: self
    anchors.fill: parent
    color: "white"
    objectName: "root"
    property MarkdownEditorScreen owner

    function paste() {
        var coded = owner.codeClipboard()
        edit.remove(edit.selectionStart, edit.selectionEnd)

        for(var i = 0; i < coded.length; i++) {
            edit.insert(edit.cursorPosition, coded[i])
        }
    }
    ScrollView {
        id:  scroll
        anchors.fill: parent
        frameVisible: true
        TextEdit {
            id: edit
            objectName: "editor"

            width: Math.max(implicitWidth, self.width)
            height: Math.max(implicitHeight, self.height)
            focus: true
            wrapMode: TextEdit.Wrap
            selectByMouse: true
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

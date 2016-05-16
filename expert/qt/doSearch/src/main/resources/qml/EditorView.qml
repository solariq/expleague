import QtQuick 2.0

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

    FocusScope {
        anchors.fill: parent
        Flickable {
            id: flick
            anchors.fill: parent
            contentWidth: edit.paintedWidth
            contentHeight: edit.paintedHeight
            clip: true
            interactive: false

            function ensureVisible(r) {
                if (contentX >= r.x)
                    contentX = r.x;
                else if (contentX+width <= r.x+r.width)
                    contentX = r.x+r.width-width;
                if (contentY >= r.y)
                    contentY = r.y;
                else if (contentY+height <= r.y+r.height)
                    contentY = r.y+r.height-height;
            }

            TextEdit {
                id: edit
                objectName: "editor"
                width: flick.width
                height: flick.height
                focus: true
                wrapMode: TextEdit.Wrap
                selectByMouse: true

                onCursorRectangleChanged: flick.ensureVisible(cursorRectangle)
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

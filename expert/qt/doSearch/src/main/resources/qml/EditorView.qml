import QtQuick 2.0

Rectangle {
    anchors.fill: parent
    color: "white"
    objectName: "root"

    FocusScope {
        anchors.fill: parent
        Flickable {
            id: flick
            anchors.fill: parent
            contentWidth: edit.paintedWidth
            contentHeight: edit.paintedHeight
            clip: true

            function ensureVisible(r)
            {
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

                onCursorRectangleChanged: flick.ensureVisible(cursorRectangle)
            }
        }
    }
}

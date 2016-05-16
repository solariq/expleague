import QtQuick 2.0

Rectangle {
    property bool hovered
    property alias text: dialog.text
    property bool active: false

    antialiasing: true
    anchors.verticalCenterOffset: -2

    implicitWidth: dialog.width + 50
    height: parent.height + 4
    clip: false
    color: {
        if (active)
            return Qt.darker(idleColor, 1.1)
        return hovered ? idleColor : backgroundColor
    }
    Text {
        renderType: Text.NativeRendering
        anchors.centerIn: parent
        id: dialog
    }
}

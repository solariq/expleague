import QtQuick 2.0

Rectangle {
    property color textColor: "black"
    property bool hover
    property real size: 8

    anchors.centerIn: parent
    color: "white"

    Text {
        anchors.centerIn: parent
        width: parent.width - 8
        height: parent.height - 8
        text: owner.text
        color: textColor
        font.pointSize: size * 3/4
//        renderType: Text.NativeRendering
        wrapMode: Text.WrapAtWordBoundaryOrAnywhere
        elide: Text.ElideRight
    }
}

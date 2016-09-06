import QtQuick 2.0

Item {
    property color textColor: "black"
    property color color: "white"
    property bool hover
    property real size: 8

    anchors.centerIn: parent

    Text {
        anchors.fill: parent
        text: owner.text
        color: textColor
        font.pointSize: size * 3/4
        renderType: Text.NativeRendering
        wrapMode: Text.WrapAtWordBoundaryOrAnywhere
        elide: Text.ElideRight
    }
}

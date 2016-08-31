import QtQuick 2.0

Item {
    property color textColor: "black"
    property color color: "white"

    anchors.centerIn: parent

    Text {
        anchors.fill: parent
        text: owner.text
        color: textColor
        font.pointSize: 6
        renderType: Text.NativeRendering
        wrapMode: Text.WrapAtWordBoundaryOrAnywhere
        elide: Text.ElideRight
    }
}

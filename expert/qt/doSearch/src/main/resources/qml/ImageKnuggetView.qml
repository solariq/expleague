import QtQuick 2.7

Item {
    property color textColor: "black"
    property color color: "white"
    property bool hover
    property real size

    anchors.centerIn: parent

    Image {
        mipmap: true
        anchors.fill: parent
        source: dosearch.league.normalizeImageUrlForUI(owner.src)
        fillMode: Image.PreserveAspectCrop
    }
}

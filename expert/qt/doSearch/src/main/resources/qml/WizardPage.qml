import QtQuick 2.5
import QtQuick.Layouts 1.1

Item {
    default property alias content: holder.children
    property Item firstFocus: back
    property bool ready: true
    property Item next

    property var go: function () {}

    visible: false
    anchors.fill: parent
    Item {
        anchors.fill: parent
        anchors.leftMargin: 100
        anchors.rightMargin: 20
        anchors.topMargin: 20
        anchors.bottomMargin: 20
        Rectangle {
            anchors.fill: parent
            id: holder
        }
    }
}

import QtQuick 2.5
import QtGraphicalEffects 1.0

Item {
    property int size
    property string src
    property bool showStatus
    Item {
        id: avatar

        anchors.centerIn: parent
        height: size
        width: size

        Image {
            id: img
            visible: false
            mipmap: true

            anchors.centerIn: parent
            height: size - 3
            width: size - 3
            fillMode: Image.PreserveAspectFit
            source: src
        }

        Rectangle {
            id: status
            visible: showStatus
            height: size/4 -1
            width: size/4 -1
            radius: status.width/2

            color: "red"
            x: avatar.width - status.width - 2
            y: avatar.height - status.height - 2
            z: avatar.z + 1
        }

        OpacityMask {
            anchors.fill: img
            source: img
            maskSource: Item {
                anchors.centerIn: parent
                width: size
                height: size
                Rectangle {
                    anchors.centerIn: parent
                    radius: (size - 1)/2
                    width: size-2
                    height: size-2
                }
            }
        }
    }
}

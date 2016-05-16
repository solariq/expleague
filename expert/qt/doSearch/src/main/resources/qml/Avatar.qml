import QtQuick 2.5
import QtGraphicalEffects 1.0

import ExpLeague 1.0

Item {
    property int size
    property string userId
    property Member user: root.league.findMember(userId)
    property string src: user.avatar
    property bool showStatus: true

    implicitHeight: size
    implicitWidth: size

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
            height: Math.max(6, size/4 -1)
            width: height
            radius: status.width/2

            color: user.status == Member.ONLINE ? "green" : "red"
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

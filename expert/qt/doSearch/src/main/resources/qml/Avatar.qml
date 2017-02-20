import QtQuick 2.5
import QtGraphicalEffects 1.0

import QtQuick.Controls 2.1

import ExpLeague 1.0

Item {
    id: self
    property int size
    property string userId
    property Member user: root.league.findMember(userId)
    property string src: user ? user.avatar : ""
    property bool showStatus: true

    implicitHeight: size
    implicitWidth: size + 2

    ToolTip.delay: 1000

    Item {
        id: avatar

        anchors.centerIn: parent
        height: size
        width: size + 2

        Rectangle {
            anchors.centerIn: parent
            radius: size/2
            width: size
            height: size
            color: "#B4B4B4"
        }

        Rectangle {
            id: status
            visible: showStatus
            height: Math.max(6, size/4 -1)
            width: height
            radius: status.width/2

            color: user && user.status == Member.ONLINE ? "green" : "red"
            x: avatar.width - status.width
            y: avatar.height - status.height - 1
            z: avatar.z + 1
        }

        Image {
            id: img
            visible: false
            mipmap: true

            anchors.centerIn: parent
            height: size - 2
            width: size - 2
            fillMode: Image.PreserveAspectFit
            source: src
        }

        OpacityMask {
            anchors.fill: img
            source: img
            maskSource: Item {
                anchors.centerIn: parent
                width: size - 2
                height: size - 2
                Rectangle {
                    anchors.centerIn: parent
                    radius: (size - 2)/2
                    width: size - 2
                    height: size - 2
                }
            }
        }
    }
    MouseArea {
        anchors.fill: parent
        hoverEnabled: true
        onEntered: self.ToolTip.show(user && user.name && user.name !== "" ? user.name : userId)
        onExited: self.ToolTip.hide()
    }
}

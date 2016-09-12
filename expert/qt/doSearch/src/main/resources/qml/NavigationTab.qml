import QtQuick 2.5
import QtQuick.Controls 2.0
import QtQuick.Controls.Styles 1.4
import QtQuick.Layouts 1.1
import QtGraphicalEffects 1.0

import ExpLeague 1.0

import "."

Rectangle {
    id: tabItem
    property color textColor: "black"
    property bool closeEnabled: true
    property alias hover: tabMouseArea.containsMouse
    implicitWidth: tabItemText.implicitWidth + 24

    Image {
        id: crossIcon
        z: parent.z + 1
        anchors {
            leftMargin: 5
            rightMargin: 5
            verticalCenter: tabItemText.verticalCenter
            left: parent.left
            right: tabItemText.left
        }
        source: closeEnabled && tabMouseArea.containsMouse ? "qrc:/cross.png" : this['icon'] !== null ? modelData.icon : ""
        visible: tabMouseArea.containsMouse || this['icon'] !== null
        fillMode: Image.PreserveAspectFit
        height: 14
        width: 14
        mipmap: true
        MouseArea {
            visible: closeEnabled
            anchors.fill: parent
            onClicked: {
                dosearch.navigation.close(group, modelData)
            }
        }
    }
    Text {
        property bool isLong: modelData.title.length >= 30
        id: tabItemText
        visible: text.length > 0
//        renderType: Text.NativeRendering
        anchors {
            leftMargin: 8
            centerIn: parent
            horizontalCenterOffset: 5
        }
        color: textColor
        text: modelData.title.replace("\n", " ")
    }
    states: [
        State {
            name: "no text"
            when: !tabItemText.visible
            PropertyChanges {
                target: tabItem
                implicitWidth: 24
            }
            PropertyChanges {
                target: crossIcon
                width: 20
                height: 20
                anchors {
                    margins: 2
                    right: crossIcon.parent.right
                }
            }
        },
        State {
            name: "wide text"
            when: tabItemText.visible && tabItemText.isLong
            PropertyChanges {
                target: tabItemText
                elide: Text.ElideRight
                width: 200
            }
            PropertyChanges {
                target: tabItem
                implicitWidth: 240
            }
        },
        State {
            name: "not wide text"
            when: tabItemText.visible && !tabItemText.isLong
            PropertyChanges {
                target: tabItemText
                elide: Text.ElideNone
                width: tabItem.implicitWidth - 40
            }
            PropertyChanges {
                target: tabItem
                implicitWidth: tabItemText.paintedWidth + 40
            }
        }
    ]

    MouseArea {
        id: tabMouseArea
        anchors.fill: parent
        hoverEnabled: true
        onClicked: {
            dosearch.navigation.select(group, modelData)
        }
//        onEntered: {
//            tabItem.ListView.view.currentIndex = index
//        }
    }
}

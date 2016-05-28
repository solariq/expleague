import QtQuick 2.5
import QtQuick.Controls 1.4
import QtQuick.Layouts 1.1

import "."

Item {
    property alias model: repeater.model

    property bool position: false

    anchors.leftMargin: 5
    anchors.rightMargin: 5

    Component {
        id: tabButton

        Rectangle {
            id: tabItem
            Layout.minimumHeight: 26
            Layout.maximumWidth: 240
            Layout.minimumWidth: 50
            Layout.bottomMargin: position ? 0 : -7
            Layout.topMargin: position ? -7 : 0
            height: tabItemText.height + 2
            border.color: "lightgray"
            border.width: 1
            radius: 5
            color: active ? Palette.activeColor : Palette.idleColor

            Image {
                id: crossIcon
                z: parent.z + 10
                anchors {
                    leftMargin: 5
                    rightMargin: 5
                    verticalCenter: tabItemText.verticalCenter
                    left: parent.left
                    right: tabItemText.left
                }
                source: tabMouseArea.containsMouse ? "qrc:/cross.png" : this['icon'] !== null ? icon : ""
                visible: tabMouseArea.containsMouse || this['icon'] !== null
                fillMode: Image.PreserveAspectFit
                height: 14
                width: 14
                mipmap: true
                MouseArea {
                    anchors.fill: parent
                    onClicked: {
                        remove()
                    }
                }
            }
            Text {
                property bool isLong: name.length >= 30
                id: tabItemText
                anchors {
                    leftMargin: 8
                    centerIn: parent
                    horizontalCenterOffset: 5
                    verticalCenterOffset: position ? +2 : -2
                }
                color: Palette.textColor
                text: name.replace("\n", " ")
            }
            states: [
                State {
                    name: "wide text"
                    when: tabItemText.isLong
                    PropertyChanges {
                        target: tabItemText
                        elide: Text.ElideMiddle
                        width: 200
                    }
                    PropertyChanges {
                        target: tabItem
                        Layout.preferredWidth: 240
                    }
                },
                State {
                    name: "not wide text"
                    when: !tabItemText.isLong
                    PropertyChanges {
                        target: tabItemText
                        elide: Text.ElideNone
                        width: tabItem.width - 40
                    }
                    PropertyChanges {
                        target: tabItem
                        Layout.preferredWidth: tabItemText.paintedWidth + 40
                    }
                }
            ]

            MouseArea {
                id: tabMouseArea
                anchors.fill: parent
                hoverEnabled: true
                onClicked: {
                    active = true
                }
            }
        }
    }

    RowLayout {
        spacing: 1
        Repeater {
            id: repeater
            Layout.fillWidth: true
            focus: true
            delegate: tabButton
            model: model
        }
    }
}

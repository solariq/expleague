import QtQuick 2.7

import QtQuick.Controls 2.0
import QtQuick.Layouts 1.1

import ExpLeague 1.0
import "."

Item {
    id: self

    property var selectedRoom

    anchors.fill: parent

    Component {
        id: roomCard
        Item {
            id: roomCardSelf
            property bool adminHere: modelData.occupied
            implicitHeight: 75
            implicitWidth: 350

            Rectangle {
                anchors.fill: parent
                color: "white"
                z: -1
            }

            RowLayout {
                anchors.fill: parent
                spacing: 0
                ColumnLayout {
                    Layout.fillHeight: true
                    Layout.fillWidth: true
                    spacing: 0
                    RowLayout {
                        Layout.fillHeight: true
                        Layout.fillWidth: true
                        Item { Layout.preferredWidth: 3 }
                        Item {
                            Layout.alignment: Qt.AlignVCenter
                            Layout.preferredHeight: 45
                            Layout.preferredWidth: 45
                            Avatar {
                                id: clientAva
                                anchors.centerIn: parent
                                size: 33
                                user: client
                            }
                        }
                        Item {
                            Layout.fillHeight: true
                            Layout.fillWidth: true
                            Text {
                                anchors.verticalCenter: parent.verticalCenter
                                anchors.left: parent.left
                                id: topic
                                text: modelData.topic
                            }
                        }
                        Item { Layout.preferredWidth: 3 }
                    }
                    RowLayout {
                        Layout.preferredHeight: 25
                        Layout.fillWidth: true
                        spacing: 0
                        Item { Layout.preferredWidth: 2 }
                        Text {
                            Layout.preferredWidth: implicitWidth
                            Layout.preferredHeight: implicitHeight
                            Layout.alignment: Qt.AlignVCenter
                            text : qsTr("Админы:")
                            font.pixelSize: 12
                        }
                        Item { Layout.preferredWidth: 2 }
                        ListView {
                            Layout.fillWidth: true
                            Layout.fillHeight: true
                            id: adminsList
                            model: modelData.admins
                            delegate: Avatar {
                                size: index != adminsList.model.length - 1 || !roomCardSelf.adminHere ? 20 : 23
                                user: modelData
                            }
                        }
                    }
                }
                Item {
                    Layout.alignment: Qt.AlignVCenter
                    Layout.preferredHeight: 45
                    Layout.preferredWidth: 45
                    Image {
                        anchors.centerIn: parent
                        height: 25
                        width: 25
                        id: status
                        source: {
                            var statuses = ["open", "chat", "response", "confirmation", "offer", "work", "delivery", "feedback", "cloded"]
                            return "qrc:/status/" + statuses[modelData.status] + ".png"
                        }
                    }
                    Text {
                        id: eta
                        anchors.top: status.bottom
                        anchors.horizontalCenter: status.horizontalCenter
                        renderType: Text.NativeRendering
                        wrapMode: Text.WordWrap
                        property color textColor: "black"
                        text: {
                            var offer = modelData.task.offer
                            if (!offer)
                                return ""
                            var d = new Date(Math.abs(offer.timeLeft))
                            return (offer.timeLeft > 0 ? "" : "-") + (d.getUTCHours() + (d.getUTCDate() - 1) * 24) + qsTr(" ч. ") + d.getUTCMinutes() + qsTr(" мин.")
                        }
                        color: {
                            var offer = modelData.task.offer
                            if (!offer)
                                return textColor

                            var urgency = Math.sqrt(Math.max(offer.timeLeft/offer.duration, 0))
                            return Qt.rgba(textColor.r + (1 - textColor.r) * urgency, textColor.g * urgency, textColor.b * urgency, textColor.a + (1 - textColor.a) * urgency)
                        }
                    }
                }
            }
            MouseArea {
                anchors.fill: parent
                onClicked: {
                    modelData.enter()
                    self.selectedRoom = modelData
                }
            }
        }
    }
    Rectangle {
        anchors.fill: parent
        color: "darkgrey"
        RowLayout {
            anchors.fill: parent
            spacing: 1
            Rectangle {
                Layout.fillHeight: true
                Layout.preferredWidth: 350
                color: "darkgrey"
                ListView {
                    id: roomsList
                    anchors.fill: parent
                    anchors.topMargin: 2
                    anchors.bottomMargin: 2
                    spacing: 2
                    clip: true

                    model: owner.rooms

                    delegate: roomCard
                    currentIndex: {
                        for (var i in owner.rooms) {
                            if (owner.rooms[i] === selectedRoom)
                                return i
                        }
                        return -1
                    }
                    highlight: Rectangle {
                        visible: !!roomsList.currentItem
                        width: roomsList.width
                        height: 76
                        color: Qt.rgba(225/256, 237/256, 254/256, 0.5)
                        y: visible ? roomsList.currentItem.y : 0
                        z: 10
                    }
                    footer: Item {
                        height: Math.max(0, roomsList.parent.height - roomsList.model.length * 75)
                    }
                }
            }

            Rectangle {
                Layout.fillHeight: true
                Layout.fillWidth: true
                color: "white"
                Item {
                    visible: !selectedRoom
                    anchors.fill: parent
                    Text {
                        anchors.centerIn: parent
                        text: "Выберите комнату из списка"
                        font.pointSize: 16
                        color: "darkgray"
                    }
                }

                RowLayout {
                    visible: !!selectedRoom
                    anchors.fill: parent
                    spacing: 0
                    LeagueChat {
                        Layout.fillHeight: true
                        Layout.fillWidth: true
                        task: !!selectedRoom ? selectedRoom.task : null
                    }
                    Rectangle {
                        Layout.fillHeight: true
                        Layout.preferredWidth: 300
                        color: Palette.navigationColor
                        OfferView {
                            anchors.fill: parent
                            task: !!selectedRoom ? selectedRoom.task : null
                            editable: true
                        }
                    }
                }
            }
        }
    }
}

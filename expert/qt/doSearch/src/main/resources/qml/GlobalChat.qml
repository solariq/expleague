import QtQuick 2.7

import QtQuick.Controls 2.0
import QtQuick.Layouts 1.1

import ExpLeague 1.0
import "."

Item {
    id: self

    property var selectedRoom

    anchors.fill: parent

    onSelectedRoomChanged: {
        if (!!selectedRoom) {
            selectedRoom.client.requestHistory()
            clientRoomsList.selectedRoom = selectedRoom
        }
    }

    Component {
        id: roomCard
        Item {
            id: roomCardSelf

            property string status: ["open", "chat", "response", "confirmation", "offer", "work", "delivery", "feedback", "cloded"][modelData.status]

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
                            Layout.alignment: Qt.AlignHCenter
                            Layout.preferredHeight: 45
                            Layout.preferredWidth: 50
                            Avatar {
                                id: clientAva
                                anchors.centerIn: parent
                                anchors.verticalCenterOffset: -5
                                size: 33
                                user: client
                                userId: client ? client.id : ""
                            }
                            Text {
                                anchors.top: clientAva.bottom
                                text: !!client ? client.id : ""
                                font.pixelSize: 10
                            }
                        }
                        Item {
                            Layout.fillHeight: true
                            Layout.fillWidth: true
                            Text {
                                anchors.verticalCenter: parent.verticalCenter
                                anchors.verticalCenterOffset: -5
                                anchors.left: parent.left
                                id: topic
                                text: modelData.topic
                                elide: Text.ElideRight
                            }
                        }
                        Item { Layout.preferredWidth: 3 }
                    }
                    RowLayout {
                        Layout.preferredHeight: 25
                        Layout.maximumHeight: 25
                        Layout.fillWidth: true
                        spacing: 0
                        Item { Layout.preferredWidth: 2 }
                        ListView {
                            Layout.fillWidth: true
                            Layout.fillHeight: true
                            id: involvedList
                            model: modelData.involved
                            property var occupied: modelData.occupied
                            visible: involved.length > 0
                            delegate: Avatar {
                                anchors.verticalCenter: parent.verticalCenter
                                size: {
                                    for(var i in involvedList.occupied)
                                        if (modelData === involvedList.occupied[i])
                                            return 22
                                    return 18
                                }
                                userId: modelData.id
                                user: modelData
                            }
                        }
                        Item { Layout.fillWidth: true }
                        Text {
                            id: region
                            text: modelData.task.offer ? modelData.task.offer.region : ""
                            font.pixelSize: 10
                        }
                        Item { Layout.preferredWidth: 2 }
                    }
                }
                Item {
                    Layout.alignment: Qt.AlignVCenter
                    Layout.preferredHeight: 45
                    Layout.preferredWidth: 45
                    Image {
                        id: status
                        anchors.centerIn: parent
                        height: 25
                        width: 25
                        anchors.verticalCenterOffset: eta.visible ? -4 : 0
                        source: "qrc:/status/" + roomCardSelf.status + ".png"
                    }
                    Text {
                        id: eta
                        anchors.top: status.bottom
                        anchors.horizontalCenter: status.horizontalCenter
                        renderType: Text.NativeRendering
                        wrapMode: Text.WordWrap
                        font.pixelSize: 10
                        visible: ["delivery", "feedback", "closed", "response"].indexOf(roomCardSelf.status) < 0
                        property color textColor: "black"
                        text: {
                            var offer = modelData.task.offer
                            if (!offer)
                                return ""
                            var d = new Date(Math.abs(offer.timeLeft))
                            return (offer.timeLeft > 0 ? "" : "-") + (d.getUTCHours() + (d.getUTCDate() - 1) * 24) + qsTr(":") + d.getUTCMinutes()
                        }
                        color: {
                            var offer = modelData.task.offer
                            if (!offer)
                                return "black"

                            var urgency = Math.sqrt(Math.max(offer.timeLeft/offer.duration, 0))
                            return Qt.rgba(textColor.r + (1 - textColor.r) * (1 - urgency), textColor.g * urgency, textColor.b * urgency, textColor.a + (1 - textColor.a) * urgency)
                        }
                    }
                }
            }
            MouseArea {
                anchors.fill: parent
                onClicked: {
                    roomCardSelf.ListView.view.selectedRoom = modelData
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
                    property var selectedRoom

                    onSelectedRoomChanged: {
                        self.selectedRoom = roomsList.selectedRoom
                    }

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
                    visible: !self.selectedRoom
                    anchors.fill: parent
                    Text {
                        anchors.centerIn: parent
                        text: "Выберите комнату из списка"
                        font.pointSize: 16
                        color: "darkgray"
                    }
                }

                ColumnLayout {
                    spacing: 0
                    anchors.fill: parent
                    visible: !!self.selectedRoom
                    Rectangle {
                        Layout.preferredHeight: 79
                        Layout.fillWidth: true
                        color: "darkgray"
                        ListView {
                            id: clientRoomsList

                            property var selectedRoom

                            orientation: ListView.Horizontal
                            anchors.fill: parent
                            anchors.margins: 2

                            spacing: 2
                            clip: true

                            model: !!self.selectedRoom ? self.selectedRoom.client.history : []

                            delegate: roomCard
                            currentIndex: {
                                for (var i in model) {
                                    if (model[i] === clientRoomsList.selectedRoom)
                                        return i
                                }
                                return -1
                            }
                            highlight: Rectangle {
                                visible: !!clientRoomsList.currentItem
                                width: 350
                                height: 77
                                color: Qt.rgba(225/256, 237/256, 254/256, 0.5)
                                x: !!clientRoomsList.currentItem ? clientRoomsList.currentItem.x : 0
                                y: 1
                                z: 10
                            }
                            footer: Item {
                                height: Math.max(0, roomsList.parent.height - roomsList.model.length * 75)
                            }
                        }
                    }

                    RowLayout {
                        id: taskView
                        Layout.fillHeight: true
                        Layout.fillWidth: true
                        property var task: {
                            return !!clientRoomsList.selectedRoom ? clientRoomsList.selectedRoom.task : self.selectedRoom.task
                        }

                        onTaskChanged: {
                            task.enter()
                        }

                        spacing: 0
                        LeagueChat {
                            Layout.fillHeight: true
                            Layout.fillWidth: true
                            task: taskView.task
                        }
                        Rectangle {
                            Layout.fillHeight: true
                            Layout.preferredWidth: 300
                            color: Palette.navigationColor
                            OfferView {
                                anchors.fill: parent
                                task: taskView.task
                                editable: true
                            }
                        }
                    }
                }
            }
        }
    }
}
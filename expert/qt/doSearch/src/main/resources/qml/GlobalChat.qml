import QtQuick 2.7

import QtQuick.Controls 2.0
import QtQuick.Layouts 1.1
import QtQuick.Controls 1.4 as Legacy

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

            property string status: modelData.status >= 0 && modelData.status < 10 ? ["open", "chat", "response", "confirmation", "offer", "work", "delivery", "feedback", "closed", "verify"][modelData.status] : ""

            implicitHeight: 75
            implicitWidth: 346
            anchors.horizontalCenter: parent.horizontalCenter

            Rectangle {
                anchors.fill: parent
                color: "white"
                z: -1
            }

            Rectangle {
                x: parent.width - 20
                y: 4
                radius: 8
                width: 16
                height: 16
                color: "red"
                visible: modelData.unread > 0
                Text {
                    anchors.centerIn: parent
                    color: "white"
                    text: "" + modelData.unread
                }
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
                                anchors.fill: parent
                                horizontalAlignment: Qt.AlignLeft
                                verticalAlignment: Qt.AlignVCenter
                                id: topic
                                text: modelData.topic
                                clip: true
                                wrapMode: Text.WrapAnywhere
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
                            Layout.preferredWidth: 100/*Math.min(100, implicitWidth)*/
                            Layout.fillHeight: true

                            implicitWidth: model.length * 20 + 6

                            id: involvedList
                            model: modelData.involved
                            property var occupied: modelData.occupied
                            visible: involved.length > 0
                            orientation: Qt.Horizontal
                            delegate: Avatar {
                                anchors.verticalCenter: parent.verticalCenter
                                size: {
                                    for(var i in involvedList.occupied)
                                        if (modelData === involvedList.occupied[i])
                                            return 24
                                    return 18
                                }
                                userId: modelData.id
                                user: modelData
                            }
                        }
                        ListView {
                            Layout.preferredWidth: 60/*Math.min(60, implicitWidth)*/
                            Layout.fillHeight: true

                            implicitWidth: 30 * model.size

                            id: ordersList
                            model: modelData.orderStatuses
                            visible: modelData.orderStatuses.length > 0
                            orientation: Qt.Horizontal
                            delegate: Rectangle {
                                height: 24
                                width: 24
//                                border.color: Palette.borderColor("active")
//                                border.width: 2
//                                radius: height / 2
                                Image {
                                    width: 16
                                    height: 16
                                    mipmap: true
                                    anchors.centerIn: parent
                                    source: {
                                        if (modelData == "progress")
                                            return "qrc:/status/play_h.png"
                                        else if (modelData == "open")
                                            return "qrc:/status/new_task_h.png"
                                        else if (modelData == "suspended")
                                            return "qrc:/status/waiting_h.png"
                                        return ""
                                    }
                                }
                            }
                        }

                        Item { Layout.fillWidth: true }
                        Text {
                            Layout.preferredWidth: Math.min(130, implicitWidth)
                            elide: Text.ElideRight
                            id: region
                            text: modelData.offer ? modelData.offer.region : ""
                            font.pixelSize: 9
                        }
                        Item { Layout.preferredWidth: 2 }
                    }
                }
                Item {
                    Layout.alignment: Qt.AlignVCenter
                    Layout.preferredHeight: 45
                    Layout.preferredWidth: 60
                    Image {
                        id: status
                        anchors.centerIn: parent
                        height: 25
                        width: 25
                        mipmap: true

                        anchors.verticalCenterOffset: eta.visible ? -4 : 0
                        source: roomCardSelf.status != "" ? "qrc:/status/" + roomCardSelf.status + ".png" : ""
                    }

                    Item {
                        id: statusDetails
                        anchors.top: status.bottom
                        anchors.horizontalCenter: status.horizontalCenter
                        height: 12
                        implicitHeight: 12
                        implicitWidth: eta.visible ? eta.implicitWidth : (stars.visible ? stars.implicitWidth : (canceled.visible ? canceled.implicitWidth : 0))
                        Text {
                            id: eta
                            renderType: Text.NativeRendering
                            wrapMode: Text.WordWrap
                            font.pixelSize: 10
                            visible: ["delivery", "feedback", "closed", "response"].indexOf(roomCardSelf.status) < 0
                            property color textColor: "black"
                            text: {
                                var offer = modelData.offer
                                if (!offer)
                                    return ""
                                var d = new Date(Math.abs(offer.timeLeft))
                                return (offer.timeLeft > 0 ? "" : "-") + (d.getUTCHours() + (d.getUTCDate() - 1) * 24) + qsTr(":") + d.getUTCMinutes()
                            }
                            color: {
                                var offer = modelData.offer
                                if (!offer)
                                    return "black"

                                var urgency = Math.sqrt(Math.max(offer.timeLeft/offer.duration, 0))
                                return Qt.rgba(textColor.r + (1 - textColor.r) * (1 - urgency), textColor.g * urgency, textColor.b * urgency, textColor.a + (1 - textColor.a) * urgency)
                            }
                        }
                        RowLayout {
                            id: stars
                            visible: roomCardSelf.status == "closed" && modelData.feedback > 0
                            spacing: 0
                            height: implicitHeight
                            implicitHeight: 10

                            Image {
                                Layout.preferredHeight: 10
                                Layout.preferredWidth: 10
                                mipmap: true
                                source: modelData.feedback > 0 ? "qrc:/tools/star-filled.png" : "qrc:/tools/star.png"
                            }
                            Image {
                                Layout.preferredHeight: 10
                                Layout.preferredWidth: 10
                                mipmap: true
                                source: modelData.feedback > 1 ? "qrc:/tools/star-filled.png" : "qrc:/tools/star.png"
                            }
                            Image {
                                Layout.preferredHeight: 10
                                Layout.preferredWidth: 12
                                mipmap: true
                                source: modelData.feedback > 2 ? "qrc:/tools/star-filled.png" : "qrc:/tools/star.png"
                            }
                            Image {
                                Layout.preferredHeight: 10
                                Layout.preferredWidth: 10
                                mipmap: true
                                source: modelData.feedback > 3 ? "qrc:/tools/star-filled.png" : "qrc:/tools/star.png"
                            }
                            Image {
                                Layout.preferredHeight: 10
                                Layout.preferredWidth: 10
                                mipmap: true
                                source: modelData.feedback > 4 ? "qrc:/tools/star-filled.png" : "qrc:/tools/star.png"
                            }
                        }
                        Text {
                            id: canceled
                            visible: roomCardSelf.status == "closed" && modelData.feedback == 0
                            height: implicitHeight
                            width: implicitWidth
                            text: qsTr("отменено")
                            font.pixelSize: 12
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
        color: Palette.toolsBackground
        RowLayout {
            anchors.fill: parent
            spacing: 1
            Item {
                Layout.fillHeight: true
                Layout.preferredWidth: 350
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
                        height: 77
                        color: Qt.rgba(225/256, 237/256, 254/256, 0.5)
                        y: visible ? roomsList.currentItem.y : 0
                        z: 10
                    }
                    footer: Item {
                        height: Math.max(0, roomsList.parent.height - roomsList.model.length * 77 - 2)
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
                    RowLayout {
                        id: taskView
                        Layout.fillHeight: true
                        Layout.fillWidth: true
                        property var task: {
                            return !!clientRoomsList.selectedRoom ? clientRoomsList.selectedRoom.task : (!!self.selectedRoom ? self.selectedRoom.task : null)
                        }

                        onTaskChanged: {
                            if (!!clientRoomsList.selectedRoom)
                                owner.enter(clientRoomsList.selectedRoom)
                        }

                        spacing: 0
                        LeagueChat {
                            Layout.fillHeight: true
                            Layout.fillWidth: true
                            task: taskView.task
                        }

                        Rectangle {
                            Layout.fillHeight: true
                            Layout.preferredWidth: 350
                            color: Palette.toolsBackground
                            Legacy.SplitView {
                                anchors.fill: parent
                                orientation: Qt.Vertical
                                OfferView {
                                    Layout.fillWidth: true
                                    Layout.preferredHeight: implicitHeight
                                    Layout.maximumHeight: maxHeight
                                    Layout.minimumHeight: minHeight
                                    task: taskView.task
                                    editable: true
                                }
                                Item {
                                    Layout.fillWidth: true
                                    Layout.fillHeight: true
                                    ListView {
                                        id: clientRoomsList

                                        property var selectedRoom
                                        anchors.fill: parent
                                        orientation: ListView.Vertical

                                        anchors.topMargin: 2
                                        anchors.bottomMargin: 2
                                        spacing: 2
                                        clip: true

                                        model: !!self.selectedRoom && self.selectedRoom.client ? self.selectedRoom.client.history : []

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
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

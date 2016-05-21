import QtQuick 2.5
import QtQuick.Controls 1.4
import QtQuick.Controls.Styles 1.4
import QtQuick.Layouts 1.1

import QtGraphicalEffects 1.0

import QtLocation 5.3

import ExpLeague 1.0

Item {
    id: self
//    property TagsDialog tagsDialog
    property Offer offer
    property Task task
    implicitHeight: topic.implicitHeight + 4 +
                    (task ? buttons.height : 0) + 4 +
                    (offer ? time.height + 4 : 0) +
                    (offer && offer.hasLocation ? 200 + 4 : 0) +
                    ((offer ? offer.images.length * (200 + 4) : 0)) +
                    (tagsView.visible ? tagsView.height + 4 : 0) +
                    (patternsView.visible ? patternsView.height + 4 : 0) +
                    (callsView.visible ? callsView.height + 4 : 0) +
                    4


    SendDialog {
        id: sendDialog
        visible: false

        task: self.task
    }

    TagsDialog {
        id: tagsDialog
        visible: false

        league: root.league
        task: self.task

        onAppendTag: {
            task.tag(tag)
        }
    }

    PatternsDialog {
        id: patternsDialog
        visible: false

        league: root.league
        onAppendPattern: {
            task.pattern(pattern)
            task.answerReset(task.answer + "\n" + pattern.text)
        }
    }

    CallDialog {
        id: callDialog
        visible: false

        onAppendCall: {
            task.phone(phone)
        }
    }


    ColumnLayout {
        anchors.fill: parent
        Rectangle {
            color: backgroundColor
            visible: task
            Layout.preferredHeight: 33
            Layout.fillWidth: true
            RowLayout {
                anchors.fill: parent
                id: buttons
                spacing: 3
                Item {Layout.preferredWidth: 1}
                Button {
                    Layout.alignment: Qt.AlignVCenter

                    style: ButtonStyle {
                        background: Rectangle {
                            implicitHeight: 29
                            implicitWidth: 29
                            radius: 4
                            color: control.pressed ? backgroundColor : navigationColor
                            Image {
                                height: 25
                                width: 25
                                anchors.centerIn: parent
                                source: "qrc:/tools/send.png"
                                mipmap: true
                            }
                        }
                    }

                    onClicked: {
                        sendDialog.visible = true
                    }
                }
                Button {
                    Layout.alignment: Qt.AlignVCenter

                    style: ButtonStyle {
                        background: Rectangle {
                            implicitHeight: 29
                            implicitWidth: 29
                            radius: 4
                            color: control.pressed ? backgroundColor : navigationColor
                            Image {
                                height: 25
                                width: 25
                                anchors.centerIn: parent
                                source: "qrc:/tools/tags.png"
                                mipmap: true
                            }
                        }
                    }

                    onClicked: {
                        tagsDialog.visible = true
                    }
                }
                Button {
                    Layout.alignment: Qt.AlignVCenter

                    style: ButtonStyle {
                        background: Rectangle {
                            implicitHeight: 29
                            implicitWidth: 29
                            radius: 4
                            color: control.pressed ? backgroundColor : navigationColor
                            Image {
                                height: 25
                                width: 25
                                anchors.centerIn: parent
                                source: "qrc:/tools/patterns.png"
                                mipmap: true
                            }
                        }
                    }

                    onClicked: {
                        patternsDialog.visible = true
                    }
                }
                Button {
                    Layout.alignment: Qt.AlignVCenter

                    style: ButtonStyle {
                        background: Rectangle {
                            implicitHeight: 29
                            implicitWidth: 29
                            radius: 4
                            color: control.pressed ? backgroundColor : navigationColor
                            Image {
                                height: 25
                                width: 25
                                anchors.centerIn: parent
                                source: "qrc:/tools/phone.png"
                                mipmap: true
                            }
                        }
                    }

                    onClicked: {
                        callDialog.visible = true
                    }
                }
                Item { Layout.fillWidth: true }
            }
        }

        Plugin {
            id: mapPlugin
            name: "osm"
        }

        Text {
            Layout.alignment: Qt.AlignHCenter
            id: time
            text: {
                if (!offer)
                    return ""
                var d = new Date(Math.abs(offer.timeLeft))
                return (offer.timeLeft > 0 ? "" : "-") + (d.getUTCHours() + (d.getUTCDate() - 1) * 24) + qsTr(" ч. ") + d.getUTCMinutes() + qsTr(" мин.")
            }
            color: {
                if (!offer)
                    return "black"

                var urgency = Math.sqrt(Math.max(offer.timeLeft/offer.duration, 0))
                return Qt.rgba(1-urgency, 0, 0, 1.0)
            }

        }

        TextEdit {
            id: topic
            Layout.alignment: Qt.AlignHCenter
            Layout.preferredWidth: parent.width - 20
            horizontalAlignment: Qt.AlignHCenter
            renderType: Text.NativeRendering
            text: offer ? offer.topic : ""
            selectByMouse: true
        }

        Flow {
            id: tagsView
            visible: task && task.tags.length > 0
            Layout.alignment: Qt.AlignHCenter
            Layout.preferredWidth: parent.width - 20
            spacing: 3
            Label {
                text: qsTr("Теги: ")
            }

            Repeater {
                model: task ? task.tags : []
                delegate: Component {
                    Text {
                        text: name + (index < task.tags.length - 1 ? "," : "")
                    }
                }
            }
        }
        Flow {
            id: callsView
            visible: task && task.phones.length > 0
            Layout.alignment: Qt.AlignHCenter
            Layout.preferredWidth: parent.width - 20

            spacing: 3
            Label {
                text: qsTr("Звонки: ")
            }

            Repeater {
                model: task ? task.phones : []
                delegate: Component {
                    Text {
                        text: modelData + (index < task.phones.length - 1 ? "," : "")
                    }
                }
            }
        }
        Flow {
            id: patternsView
            visible: task && task.patterns.length > 0
            Layout.alignment: Qt.AlignHCenter
            Layout.preferredWidth: parent.width - 20

            spacing: 3
            Label {
                text: qsTr("Шаблоны: ")
            }

            Repeater {
                model: task ? task.patterns : []
                delegate: Component {
                    Text {
                        text: name + (index < task.patterns.length - 1 ? "," : "")
                    }
                }
            }
        }


        Map {
            id: map
            visible: offer && offer.hasLocation

            property MapCircle circle
            Layout.columnSpan: 2
            Layout.preferredHeight: 200
            Layout.preferredWidth: 300
            Layout.alignment: Qt.AlignHCenter
            zoomLevel: 12
            plugin: mapPlugin
            center {
                longitude: offer ? offer.longitude : 0
                latitude: offer ? offer.latitude : 0
            }

            gesture.enabled: true
            MouseArea {
                anchors.fill: parent
                onClicked: {
                    task.context.handleOmniboxInput("qrc:/html/yandex-map.html?latitude=" + offer.latitude + "&longitude=" + offer.longitude, true)
                }
            }

            Connections {
                target: self
                onOfferChanged: {
                    if (circle)
                        map.removeMapItem(circle)
                    if (offer) {
                        map.circle = Qt.createQmlObject('import QtLocation 5.3; MapCircle {}', map)
                        map.circle.center.longitude = offer.longitude
                        map.circle.center.latitude = offer.latitude
                        map.circle.radius = 50.0
                        map.circle.color = 'green'
                        map.circle.border.width = 3
                        map.addMapItem(circle)
                    }
                }
            }
        }

        Component {
            id: imageView
            Item {
                Layout.preferredHeight: 200
                Layout.alignment: Qt.AlignHCenter
                Layout.fillWidth: true

                Image {
                    id: img
                    autoTransform: true
                    anchors.centerIn: parent
                    fillMode: Image.PreserveAspectFit
                    source: "image://store/" + modelData
                    onStatusChanged: {
                        if (sourceSize.height/sourceSize.width > 2./3.) {
                            img.height = 200
                            img.width = undefined
                        }
                        else {
                            img.height = undefined
                            img.width = 300
                        }
                    }
                }
                MouseArea {
                    anchors.fill: parent
                    onClicked: task.context.handleOmniboxInput(root.league.imageUrl(modelData), true)
                }
            }
        }

        Repeater {
            model: offer ? offer.images : []
            delegate: imageView
        }

        Item {Layout.fillHeight: true}
    }
}

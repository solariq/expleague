import QtQuick 2.5
import QtQuick.Window 2.2
import QtQuick.Controls 1.4
import QtQuick.Layouts 1.1

import QtLocation 5.3

import ExpLeague 1.0

Window {
    id: dialog

    property Offer offer
    property color backgroundColor: "#e8e8e8"
    property int invitationTimeout: 0

    width: 350
    height: 450
    minimumHeight: height
    maximumHeight: height
    minimumWidth: width
    maximumWidth: width
    opacity: 0.9

    modality: Qt.WindowModal
    color: backgroundColor

    signal accepted(Offer offer)
    signal rejected(Offer offer)

//    Connections {
//        target: offer
//        onCancelled: {
//            dialog.hide()
//        }
//    }

    Timer {
        interval: 500; running: true; repeat: true
        onTriggered: {
            if (invitationTimeout > 0) {
                invitationTimeout -= 500
                var time = new Date(invitationTimeout)
                accept.text = qsTr("Принять ") + time.getMinutes() + "м. " + time.getSeconds() + "c."
            }
            else accept.text = qsTr("Принять")
        }
    }

    Action {
        id: accept
        text: qsTr("Принять")
        onTriggered: {
            dialog.accepted(offer)
            dialog.hide()
        }
    }

    Action {
        id: reject
        text: qsTr("Отказаться")
        onTriggered: {
            dialog.rejected(offer)
            dialog.hide()
        }
    }

    Plugin {
        id: mapPlugin
        name: "osm"
          //specify plugin parameters if necessary
          //PluginParameter {...}
          //PluginParameter {...}
          //...
    }
    ColumnLayout {
        anchors.fill: parent
        Item {Layout.preferredHeight: 20}
        Text {
            Layout.alignment: Qt.AlignHCenter
            text: qsTr("Открыто задание")
            font.bold: true
            font.pointSize: 16
        }
        Item {
            Layout.fillHeight: true
            Layout.fillWidth: true
            GridLayout {
                anchors.margins: 15
                anchors.horizontalCenter: parent.horizontalCenter
                anchors.top: parent.top
                anchors.bottom: parent.bottom
                columns: 2
                Label {
                    text: qsTr("На тему: ")
                }

                Text {
                    Layout.alignment: Qt.AlignLeft
                    text: offer ? offer.topic : ""
                    font.bold: true
                }

                Label {
                    text: qsTr("Крайний срок через: ")
                }

                Text {
                    Layout.alignment: Qt.AlignLeft
                    text: {
                        if (!offer)
                            return ""
                        var d = new Date(Math.abs(offer.timeLeft))
                        return (offer.timeLeft > 0 ? "" : "-") + (d.getHours() + (d.getDate() - 1) * 24) + qsTr(" ч. ") + d.getMinutes() + qsTr(" мин.")
                    }
                    color: {
                        if (!offer)
                            return "black"

                        var urgency = Math.sqrt(Math.max(offer.timeLeft/offer.duration, 0))
                        return Qt.rgba(1.0, urgency, urgency, 1.0)
                    }
                }

                Map {
                    id: map
                    visible: offer && offer.hasLocation

                    Layout.columnSpan: 2
                    Layout.preferredHeight: 200
                    Layout.preferredWidth: 300
                    Layout.alignment: Qt.AlignHCenter

                    plugin: mapPlugin
                    center {
                        longitude: offer ? offer.longitude : 0
                        latitude: offer ? offer.latitude : 0
                    }

                    gesture.enabled: true
                }

                Component {
                    id: imageView
                    Item {
                        Layout.columnSpan: 2
                        Layout.preferredHeight: 200
                        Layout.preferredWidth: 300

                        Image {
                            id: img
                            autoTransform: true
                            anchors.centerIn: parent
                            fillMode: Image.PreserveAspectFit
                            source: "image://store/" + modelData
                            onStatusChanged: {
                                if (sourceSize.height/sourceSize.width > 2./3.)
                                    img.height = 200
                                else
                                    img.width = 300
                            }
                        }
                    }
                }

                Repeater {
                    model: offer ? offer.images : []
                    delegate: imageView
                }


                Item {Layout.fillHeight:true; Layout.columnSpan: 2}
                Button {
                    Layout.alignment: Qt.AlignLeft
                    Layout.preferredWidth: 130
                    action: reject
                }

                Button {
                    focus: true
                    Layout.alignment: Qt.AlignRight
                    Layout.preferredWidth: 130
                    action: accept
                }
            }
        }
        Keys.onEscapePressed: {
            dialog.hide()
        }
    }
}

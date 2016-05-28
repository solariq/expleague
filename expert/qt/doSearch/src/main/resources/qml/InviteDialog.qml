import QtQuick 2.5
import QtQuick.Window 2.2
import QtQuick.Controls 1.4
import QtQuick.Layouts 1.1

import QtLocation 5.3

import ExpLeague 1.0

import "."

Window {
    id: dialog

    property Offer offer
    property color backgroundColor: Palette.backgroundColor
    property int invitationTimeout: 0

    width: 500
    height: contents.implicitHeight + caption.implicitHeight + 40
    minimumHeight: height
    maximumHeight: height
    minimumWidth: width
    maximumWidth: width
    opacity: 0.95

    modality: Qt.WindowModal
    color: backgroundColor

    signal accepted(Offer offer)
    signal rejected(Offer offer)

    Connections {
        target: offer
        onCancelled: {
            dialog.visible = false
        }
    }

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
            id: caption
            Layout.alignment: Qt.AlignHCenter
            text: qsTr("Открыто задание")
            font.bold: true
            font.pointSize: 16
        }
        Item {
            Layout.fillHeight: true
            Layout.fillWidth: true
            GridLayout {
                id: contents
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
                    Layout.maximumHeight: 150
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
                        id: imageContainer
                        Layout.fillHeight: true
                        Layout.preferredWidth: implicitWidth
                        implicitHeight: img.height
                        implicitWidth: img.width

                        Image {
                            id: img
                            autoTransform: true
                            anchors.centerIn: parent
                            fillMode: Image.PreserveAspectFit
                            source: "image://store/" + modelData
                            onStatusChanged: {
                                if (sourceSize.height/sourceSize.width > 2./3.) {
                                    img.height = imageContainer.height
                                    img.width = imageContainer.height * sourceSize.width/sourceSize.height
                                }
                                else {
                                    img.height = imageContainer.width * sourceSize.height/sourceSize.width
                                    img.width = imageContainer.width
                                }
                            }
                        }
                    }
                }

                ScrollView {
                    visible: offer && offer.images.length > 0
                    Layout.columnSpan: 2
                    Layout.preferredHeight: 200
                    Layout.maximumWidth: 400
                    Layout.preferredWidth: imagesRow.implicitWidth
                    Layout.alignment: Qt.AlignHCenter
                    RowLayout {
                        id: imagesRow
                        height: 200
                        width: implicitWidth
                        spacing: 5
                        implicitWidth: {
                            var result = 0
                            for (var i = 0; i < children.length; i++) {
                                if (i > 0)
                                    result += 5
                                result += children[i].implicitWidth
                            }
                            return result
                        }

                        Repeater {
                            model: offer ? offer.images : []
                            delegate: imageView
                        }
                    }
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
                Item {Layout.preferredHeight: 5; Layout.columnSpan: 2}
            }
        }
        Keys.onEscapePressed: {
            dialog.hide()
        }
    }
}

import QtQuick 2.5
import QtQuick.Window 2.2
import QtQuick.Controls 1.4
import QtQuick.Layouts 1.1

import QtLocation 5.3
import QtWebEngine 1.3

import ExpLeague 1.0

import "."

Window {
    id: self

    property Offer offer
    property color backgroundColor: Palette.activeColor
    property color textColor: Palette.activeTextColor
    property int invitationTimeout: 0
    property real offerHeight: topic.implicitHeight + 4 + eta.implicitHeight + 4 + local.implicitHeight + 4 + attachmentsCount.implicitHeight + 4 + (map.visible ? 204 : 0) + (images.visible ? 204 : 0) + 60

    width: 500
    height: {
        return Math.min(400, offerHeight + caption.implicitHeight + 40)
    }
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
            self.visible = false
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
            self.accepted(offer)
            self.hide()
        }
    }

    Action {
        id: reject
        text: qsTr("Отказаться")
        onTriggered: {
            self.rejected(offer)
            self.hide()
        }
    }

    ColumnLayout {
        anchors.fill: parent
        Item {Layout.preferredHeight: 20}
        Text {
            id: caption
            Layout.maximumWidth: parent.width - 20
            Layout.alignment: Qt.AlignHCenter
            renderType: Text.NativeRendering
            text: qsTr("Открыто задание")
            color: textColor
            font.bold: true
            font.pointSize: 14
        }
        Flickable {
            id: contents
            Layout.fillHeight: true
            Layout.fillWidth: true

            contentHeight: self.offerHeight
            contentWidth: width
            clip: true
            GridLayout {
                id: contentsGrid
                width: contents.width - 30
                anchors.horizontalCenter: parent.horizontalCenter
                anchors.top: parent.top
                anchors.bottom: parent.bottom
                columns: 2
                Label {
                    text: qsTr("На тему: ")
                    color: textColor
                }

                TextEdit {
                    id: topic
                    Layout.alignment: Qt.AlignLeft
                    Layout.preferredWidth: self.width/1.5 - 10
                    Layout.maximumWidth: self.width/1.5 - 10
                    renderType: Text.NativeRendering
                    readOnly: true
                    wrapMode: Text.WordWrap
                    text: offer ? offer.topic : ""
                    color: textColor
                    font.bold: true
                    clip: true
                }

                Label {
                    text: qsTr("Крайний срок через: ")
                    color: textColor
                }

                Text {
                    id: eta
                    Layout.alignment: Qt.AlignLeft
                    Layout.maximumHeight: 150
                    renderType: Text.NativeRendering
                    wrapMode: Text.WordWrap
                    text: {
                        if (!offer)
                            return ""
                        var d = new Date(Math.abs(offer.timeLeft))
                        return (offer.timeLeft > 0 ? "" : "-") + (d.getUTCHours() + (d.getUTCDate() - 1) * 24) + qsTr(" ч. ") + d.getUTCMinutes() + qsTr(" мин.")
                    }
                    color: {
                        if (!offer)
                            return textColor

                        var urgency = 1 - Math.sqrt(Math.max(offer.timeLeft/offer.duration, 0))
                        return Qt.rgba(textColor.r + (1 - textColor.r) * urgency, textColor.g * urgency, textColor.b * urgency, textColor.a + (1 - textColor.a) * urgency)
                    }
                }

                Label {
                    text: qsTr("Гео-специфичный: ")
                    color: textColor
                }

                Label {
                    id: local
                    text: offer ? (offer.local ? qsTr("Да") : qsTr("Нет")) : ""
                    color: textColor
                }

                Label {
                    text: qsTr("Количество приложений: ")
                    color: textColor
                }

                Label {
                    id: attachmentsCount
                    text: offer ? "" + offer.images.length : ""
                    color: textColor
                }

                WebEngineView {
                    id: map
                    visible: offer && offer.hasLocation

                    Layout.columnSpan: 2
                    Layout.preferredHeight: 200
                    Layout.preferredWidth: 300
                    Layout.alignment: Qt.AlignHCenter

                    url: offer ? "qrc:/html/yandex-map.html?latitude=" + offer.latitude + "&longitude=" + offer.longitude : ""
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
                    id: images
                    visible: offer && offer.images.length > 0
                    Layout.columnSpan: 2
                    Layout.preferredHeight: 200
                    Layout.maximumWidth: 400
                    Layout.preferredWidth: imagesRow.implicitWidth
                    Layout.alignment: Qt.AlignHCenter
                    horizontalScrollBarPolicy: Qt.ScrollBarAlwaysOff
                    verticalScrollBarPolicy: Qt.ScrollBarAlwaysOff

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
            }
        }
        RowLayout {
            Layout.preferredHeight: rejectButton.implicitHeight
            Layout.fillWidth: true
            spacing: 0
            Item { Layout.preferredWidth: 15 }
            Button {
                id: rejectButton
                Layout.alignment: Qt.AlignLeft
                Layout.preferredWidth: 130
                action: reject
            }
            Item {
                Layout.fillWidth: true
            }
            Button {
                focus: true
                Layout.alignment: Qt.AlignRight
                Layout.preferredWidth: 130
                action: accept
            }
            Item { Layout.preferredWidth: 15 }
        }
        Item {
            Layout.preferredHeight: 5
        }

        Keys.onEscapePressed: {
            self.visible = false
        }
    }
}

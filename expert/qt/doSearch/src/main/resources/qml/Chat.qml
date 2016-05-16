import QtQuick 2.5
import QtQuick.Layouts 1.1
import QtQuick.Controls 1.4

import Qt.labs.controls 1.0

import ExpLeague 1.0

Rectangle {
    property Task task
    color: Qt.rgba(230/256.0, 233/256.0, 234/256.0, 1.0)

    Component {
        id: message

        Item {
            implicitHeight: {
                var height = 0;
                if (text.visible) {
                    height += text.implicitHeight
                }
                if (button.visible) {
                    height += button.implicitHeight
                }
                if (image.visible) {
                    height += image.implicitHeight
                }
                return height
            }
            implicitWidth: {
                var width = 0;
                if (text.visible) {
                    width += text.implicitWidth
                }
                if (button.visible) {
                    width += button.implicitWidth
                }
                if (image.visible) {
                    width += image.implicitWidth
                }
                return width
            }

            TextEdit {
                id: text
                anchors.fill: parent
                visible: model.text.length > 0 && !model.action
                text: model.text
                horizontalAlignment: Qt.AlignLeft
                renderType: Text.NativeRendering
                selectByMouse: true
            }

            Button {
                id: button
                anchors.fill: parent
                visible: model.text.length > 0 && model.action
                text: model.text

                onClicked: {
                    fire()
                }
            }

            Image {
                id: image
                anchors.fill: parent
                visible: model.reference.length > 0
                source: model.reference
                height: Math.min(implicitHeight, 200)
                fillMode: Image.PreserveAspectFit
            }
        }
    }

    Component {
        id: bubble

        Item {
            Layout.fillWidth: true
            implicitHeight: content.implicitHeight

            Avatar {
                id: leftAvatar
                visible: incoming

                anchors.bottom: parent.bottom
                anchors.left: parent.left
                anchors.right: incomingTail.left

                userId: from
                size: 25
            }

            Image {
                id: incomingTail
                visible: incoming

                width: 12
                height: 12
                anchors.bottom: parent.bottom
                anchors.bottomMargin: 8
                anchors.left: leftAvatar.right

                source: "qrc:/chat/incoming_tail.png"
                sourceSize: "24x24"
                fillMode: Image.PreserveAspectFit
            }

            Rectangle {
                id: content

                implicitHeight: messagesView.implicitHeight + 8
                implicitWidth: messagesView.implicitWidth + 8

                color: incoming ? "white" : "#A2DCF4"
                radius: 8

                ColumnLayout {
                    anchors.centerIn: parent
                    id: messagesView
                    Repeater {
                        model: messages
                        delegate: message
                    }
                }
            }

            Image {
                id: outgoingTail
                width: 12
                height: 12

                source: "qrc:/chat/outgoing_tail.png"
                sourceSize: "24x24"
                fillMode: Image.PreserveAspectFit
                visible: !incoming

                anchors.bottom: parent.bottom
                anchors.right: rightAvatar.left
                anchors.bottomMargin: 8
            }

            Avatar {
                id: rightAvatar
                visible: !incoming

                anchors.bottom: parent.bottom
                anchors.right: parent.right

                userId: from
                size: 25
            }

            states: [
                State {
                    name: "incoming"
                    when: incoming
                    PropertyChanges {
                        target: content
                        anchors.left: incomingTail.right
                        Layout.alignment: Qt.AlignLeft
                    }
                },
                State {
                    name: "outgoing"
                    when: !incoming
                    PropertyChanges {
                        target: content
                        anchors.right: outgoingTail.left
                        Layout.alignment: Qt.AlignLeft
                    }
                }
            ]
        }
    }

    ColumnLayout {
        anchors.fill: parent
        Item {
            Layout.fillWidth: true
            Layout.fillHeight: true
            ColumnLayout {
                anchors.fill: parent
                spacing: 3
                Item {Layout.preferredHeight: 1}
                Repeater {
                    model: task ? task.chat : []
                    delegate: bubble

                    Component.onCompleted:  {
                        console.log("Chat model: " + model)
                    }
                }
                Item {Layout.fillHeight: true}
            }
        }

        TextArea {
            id: send
            Layout.fillWidth: true
            wrapMode: TextEdit.WrapAnywhere
            placeholderText: qsTr("Напишите сообщение клиенту")
            background: Rectangle {
                color: send.enabled ? "white" : "#353637"
                border.color: send.enabled ? "#bdbebf" : "transparent"
            }
            Keys.onPressed: {
                if ((event.key === Qt.Key_Enter || event.key === Qt.Key_Return) && ((event.modifiers & (Qt.ControlModifier | Qt.MetaModifier)) !== 0)) {
                    task.sendMessage(send.text)
                    send.text = ""
                }
            }
        }
    }
}

import QtQuick 2.7
import QtQuick.Layouts 1.1
import QtQuick.Controls 1.4
import QtQuick.Controls 2.0

import ExpLeague 1.0

import "."

Rectangle {
    id: self
    property Task task
    property var chat: self.task ? self.task.chat : []

    color: Qt.rgba(230/256.0, 233/256.0, 234/256.0, 1.0)
    signal messageClicked(ChatMessage message)

    Component {
        id: message

        Item {
            x: 0
            height: {
                var height = 0;
                if (text.visible) {
                    height += text.implicitHeight
                }
                if (button.visible) {
                    height += button.implicitHeight
                }
                if (imageContainer.visible) {
                    height += imageContainer.height
                }
                return Math.max(20, height)
            }
            width: {
                var width = 0;
                if (text.visible) {
                    width += text.implicitWidth
                }
                if (button.visible) {
                    width += button.implicitWidth
                }
                if (imageContainer.visible) {
                    width += imageContainer.width
                }
                return Math.max(Math.min(width, self.width - 50), 20)
            }

            TextEdit {
                id: text
                anchors.fill: parent
                visible: msg.text.length > 0 && !msg.action
                text: msg.text
                wrapMode: TextEdit.WrapAnywhere

                horizontalAlignment: Qt.AlignLeft
                renderType: Text.NativeRendering
                selectByMouse: true
                readOnly: true
            }

            Button {
                id: button
                anchors.fill: parent
                visible: msg.text.length > 0 && msg.action
                text: msg.text

                onClicked: {
                    msg.fire()
                }
            }

            Item {
                id: imageContainer
                visible: msg.reference.length > 0
                height: image.height
                width: image.width

                Image {
                    id: image
                    anchors.centerIn: parent
                    source: msg.reference
                    fillMode: Image.PreserveAspectFit
                    mipmap: true
                    autoTransform: true
                    onStatusChanged: {
                        var w = (column.width - 30)
                        if (sourceSize.height/sourceSize.width > 2./3.) {
                            image.height = w * 2./3.
                            image.width = image.height * sourceSize.width/sourceSize.height
                        }
                        else {
                            image.width = w
                            image.height = w * sourceSize.height/sourceSize.width
                        }
                    }
                }
                MouseArea {
                    anchors.fill: parent
                    onClicked: {
                        var split = msg.reference.split('/')
                        dosearch.navigation.handleOmnibox(root.league.imageUrl(split[split.length - 1]), 0)
                    }
                }
            }
            TransparentMouseArea {
                anchors.fill: parent
                onClicked: {
                    messageClicked(modelData)
                }
            }
        }
    }

    Component {
        id: bubble

        Item {
            width: chatContainer.width - chatContainer.leftMargin - chatContainer.rightMargin
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

                implicitHeight: messagesView.contentHeight + 8
                implicitWidth: messagesView.contentWidth + 8

                color: incoming ? "white" : "#A2DCF4"
                radius: 8

                ListView {
                    id: messagesView

                    anchors.centerIn: parent
                    width: contentWidth
                    height: contentHeight
                    orientation: ListView.TopToBottom

                    model: modelData
                    interactive: false

                    delegate: message

                    contentWidth: {
                        var result = 0
                        for(var child in contentItem.children) {
                            result = Math.max(result, contentItem.children[child].width)
                        }
                        return result
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
        id: column
        anchors.fill: parent
        spacing: 0
        Flickable {
            Layout.fillHeight: true
            Layout.fillWidth: true

            id: chatContainer
            clip: true
            contentWidth: width - 8
            contentHeight: chat.height
            topMargin: 4
            leftMargin: 4
            rightMargin: 4
            bottomMargin: 4

            Column {
                id: chat
                spacing: 5
                Item {height: 5}
                Repeater {
                    model: self.chat
                    delegate: bubble
                }
                onHeightChanged: {
                    chatContainer.contentY = Math.max(0, height - chatContainer.height)
                }
            }
        }

        TextArea {
            id: send
            visible: !!task
            Layout.fillWidth: true
            Layout.preferredHeight: implicitHeight
            wrapMode: TextEdit.WrapAnywhere
            placeholderText: qsTr("Напишите сообщение клиенту")
            background: Rectangle {
                color: send.enabled ? "white" : Palette.chatBackgroundColor
                border.color: send.enabled ? Palette.focusBorderColor : "transparent"
            }
            Keys.onPressed: {
                if ((event.key === Qt.Key_Enter || event.key === Qt.Key_Return) && ((event.modifiers & (Qt.ControlModifier | Qt.MetaModifier)) !== 0)) {
                    self.task.sendMessage(send.text)
                    send.text = ""
                }
            }
        }
    }
}

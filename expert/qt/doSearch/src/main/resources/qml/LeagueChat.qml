import QtQuick 2.7
import QtQuick.Layouts 1.1
import QtQuick.Controls 2.0

import QtGraphicalEffects 1.0

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

    Item {
        id: column
        anchors.fill: parent
        Flickable {
            id: chatContainer
            anchors.top: parent.top
            anchors.left: parent.left
            anchors.right: parent.right
            height: parent.height - toolsRow.implicitHeight

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

        Component {
            id: chatPattern
            Rectangle {
                implicitHeight: text.implicitHeight + 8
                height: implicitHeight
                width: parent.width - 8
                color: "white"
                radius: Palette.radius
                TextEdit {
                    id: text
                    anchors.centerIn: parent
                    width: parent.width - 8
                    height: parent.height - 8
                    readOnly: true
                    selectByMouse: true
                    textFormat: TextEdit.RichText
                    wrapMode: TextEdit.WrapAnywhere
                    text: modelData.replace(/TODO/g, "<b>TODO</b>")
                }

                MouseArea {
                    anchors.fill: parent
                    onClicked: {
                        send.text = modelData
                        column.activeDialog.visible = false
                        send.forceActiveFocus()
                    }
                }
            }
        }

        Rectangle {
            id: hello
            height: Math.min(400, helloLst.implicitHeight + 8)
            width: 500
            color: Palette.toolsBackground
            x: chatContainer.x + chatContainer.width - width - 4
            y: chatContainer.y + chatContainer.height - height - 4
            ListView {
                id: helloLst
                model: dosearch.league.helloPatterns
                delegate: chatPattern
                spacing: 4
                rightMargin: 4
                leftMargin: 4
                topMargin: 4
                bottomMargin: 4
                anchors.fill: parent
                implicitHeight: contentHeight
            }

            visible: false
        }

        Rectangle {
            id: patterns
            height: Math.min(400, helloLst.implicitHeight + 8)
            width: 500
            color: Palette.toolsBackground
            x: chatContainer.x + chatContainer.width - width - 4
            y: chatContainer.y + chatContainer.height - height - 4
            ListView {
                id: patternsLst
                model: dosearch.league.chatPatterns
                delegate: chatPattern
                spacing: 4
                rightMargin: 4
                leftMargin: 4
                topMargin: 4
                bottomMargin: 4
                anchors.fill: parent
                implicitHeight: contentHeight
            }

            visible: false
        }

        property var activeDialog: hello
        function showDialog(d) {
            if (activeDialog !== d) {
                activeDialog.visible = false
                activeDialog = d
                activeDialog.visible = true
            }
            else activeDialog.visible = !activeDialog.visible
        }

        DropShadow {
            visible: !!column.activeDialog && column.activeDialog.visible
            anchors.fill: column.activeDialog
            cached: true
            radius: 8.0
            samples: 16
            spread: 0.4
            color: "#80000000"
            source: column.activeDialog
        }

        Rectangle {
            id: toolsRow

            anchors.top: chatContainer.bottom
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.bottom: parent.bottom
            height: implicitHeight
            implicitHeight: send.implicitHeight + 16
            color: Palette.toolsBackground

            RowLayout {
                anchors.fill: parent
                spacing: 0
                Item { Layout.preferredWidth: 4 }
                Rectangle {
                    id: sendBg
                    Layout.fillWidth: true
                    Layout.preferredHeight: send.implicitHeight + 8
                    Layout.maximumHeight: send.implicitHeight + 8
                    Layout.minimumHeight: send.implicitHeight + 8
                    border.color: send.enabled ? Palette.focusBorderColor : "transparent"
                    color: send.enabled ? "white" : Palette.chatBackgroundColor
                    radius: Palette.radius
                    TextEdit {
                        id: send
                        visible: !!task
                        anchors.centerIn: parent
                        width: parent.width - 8
                        height: parent.height - 8
                        wrapMode: TextEdit.WrapAnywhere
                        textFormat: TextEdit.RichText
                        text: qsTr("Напишите сообщение клиенту")
                        color: "darkgray"
                        font.pixelSize: 14
                        selectByMouse: true

                        property string prevText: ""
                        onTextChanged: {
                            var plainText = getText(0, text.length)
                            if (prevText != plainText) {
                                prevText = plainText
                                var richText = plainText.replace(/TODO/g, "<span style=\" font-weight:600; color:#cccc00;\">TODO</span>")
                                var position = cursorPosition
                                text = richText
                                cursorPosition = position
                            }
                        }

                        property string markerText: "TODO"
                        Keys.onPressed: {
                            var text = getText(0, send.text.length)
                            if ((event.key === Qt.Key_Enter || event.key === Qt.Key_Return) && ((event.modifiers & (Qt.ControlModifier | Qt.MetaModifier)) !== 0)) {
                                if (text.indexOf(markerText) < 0) {
                                    self.task.sendMessage(text)
                                    send.text = ""
                                    event.accepted = true
                                }
                                else {
                                    cursorPosition = text.indexOf(markerText)
                                    selectWord()
                                    event.accepted = false
                                }
                            }
                            else if (event.key === Qt.Key_F2) {
                                var nextTodo = text.substring(cursorPosition + 1).indexOf(markerText)
                                if (nextTodo >= 0) {
                                    event.accepted = true
                                    cursorPosition += nextTodo + 1
                                }
                                else {
                                    var firstTodo = text.indexOf(markerText)
                                    if (firstTodo >= 0) {
                                        event.accepted = true
                                        cursorPosition = firstTodo
                                    }
                                }
                                if (event.accepted) {
                                    selectWord()
                                    font.bold = false
                                }
                            }

                        }
                        onActiveFocusChanged: {
                            var text = getText(0, send.text.length)
                            if (activeFocus) {
                                if (text == qsTr("Напишите сообщение клиенту"))
                                    send.text = ""
                                color = "black"
                            }
                            else if (text == "") {
                                send.text = qsTr("Напишите сообщение клиенту")
                                color = "darkgray"
                            }
                        }
                    }
                }
                Item { Layout.preferredWidth: 4 }
                Item {
                    Layout.preferredHeight: 31
                    Layout.preferredWidth: 27
                    Layout.alignment: Qt.AlignBottom
                    ToolbarButton {
                        anchors.centerIn: parent
                        icon: "qrc:/tools/chat_h.png"
                        dark: true
                        size: 27
                        toggle: hello.visible
                        onTriggered: column.showDialog(hello)
                    }
                }
                Item {
                    Layout.preferredHeight: 31
                    Layout.preferredWidth: 27
                    Layout.alignment: Qt.AlignBottom
                    ToolbarButton {
                        anchors.centerIn: parent
                        icon: "qrc:/tools/patterns_h.png"
                        dark: true
                        size: 27
                        toggle: patterns.visible
                        onTriggered: column.showDialog(patterns)
                    }
                }
                Item { Layout.preferredWidth: 4 }
            }
        }
    }
}

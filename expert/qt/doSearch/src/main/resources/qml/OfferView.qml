import QtQuick 2.7
import QtQuick.Controls 2.0
import QtQuick.Layouts 1.1

import QtGraphicalEffects 1.0

import QtWebEngine 1.3

import ExpLeague 1.0

import "."

Item {
    id: self
    property Task task
    property Offer offer: !!self.task ? self.task.offer : null
    property color textColor: Palette.selectedTextColor
    property real storedHeight: 0
    property real maxHeight: tools.implicitHeight + content.contentHeight
    property real minHeight: tools.implicitHeight
    property bool editable: false

    property real offerHeight: topic.implicitHeight + geoLocal.implicitHeight + 4 + attachmentsCount.implicitHeight + 4 +
                               33 + 4 +
                               time.implicitHeight + 4 +
                               comment.implicitHeight + 4 +
                               filterView.implicitHeight + 4 +
                               (offer && offer.hasLocation ? 200 + 4 : 0) +
                               (offer ? offer.images.length * (200 + 4) : 0) +
                               (tagsView.visible ? tagsView.implicitHeight + 4 : 0) +
                               (patternsView.visible ? patternsView.implicitHeight + 4 : 0) +
                               (callsView.visible ? callsView.implicitHeight + 4 : 0) +
                               4

    onOfferChanged: {
        dosearch.main.delay(10, function () {
            if (offer)
                storedHeight = Math.min(400, offerHeight)
            else storedHeight = 0
        })
    }

    implicitHeight: tools.implicitHeight + storedHeight

    onHeightChanged: {
        if (self.state.length == 0) {
            storedHeight = height - tools.height
        }
    }

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
            var screen = root.navigation.activeScreen
            if (!screen || !screen.editor)
                return

            var editor = screen.editor
            var start = editor.selectionStart
            editor.insert(start, pattern.text)
            editor.cursorPosition = editor.cursorPosition - pattern.length
            task.pattern(pattern)
        }
    }

    CallDialog {
        id: callDialog
        visible: false

        onAppendCall: {
            task.phone(phone)
        }
    }

    SuspendDialog {
        id: suspendDialog
        visible: false

        onSuspend: {
            self.task.suspend(time)
        }
    }

    states: [
        State{
            name: "folded"
            PropertyChanges {
                target: self

                implicitHeight: tools.height
            }

            PropertyChanges {
                target: content
                visible: false
            }

            PropertyChanges {
                target: self

                height: tools.height
            }
        }
    ]

    ColumnLayout {
        anchors.fill: parent
        Rectangle {
            id: tools
            gradient: Palette.navigationGradient
            visible: task
            Layout.preferredHeight: implicitHeight
            Layout.fillWidth: true

            implicitHeight: 33
            RowLayout {
                anchors.fill: parent
                id: buttons
                spacing: 3
                Item {Layout.preferredWidth: 1}
                Button {
                    Layout.fillHeight: true
                    Layout.preferredWidth: height
                    indicator: Image {
                        anchors.centerIn: parent
                        fillMode: Image.PreserveAspectFit
                        mipmap: true
                        source: "qrc:/expand.png"
                        width: 16
                        height: 16
                        rotation: content.visible ? 180 : 90
                    }
                    background: Item{}

                    onClicked: {
                        if (self.state == "folded")
                            self.state = ""
                        else
                            self.state = "folded"
                    }
                }

                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    icon: "qrc:/tools/send.png"
                    highlightedIcon: "qrc:/tools/send_h.png"
                    onTriggered: dosearch.main.showDialog(sendDialog)
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    icon: "qrc:/tools/tags.png"
                    highlightedIcon: "qrc:/tools/tags_h.png"
                    onTriggered: dosearch.main.showDialog(tagsDialog)
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    icon: "qrc:/tools/patterns.png"
                    highlightedIcon: "qrc:/tools/patterns_h.png"
                    onTriggered: dosearch.main.showDialog(patternsDialog)
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    icon: "qrc:/tools/phone.png"
                    highlightedIcon: "qrc:/tools/phone_h.png"
                    onTriggered: dosearch.main.showDialog(callDialog)
                }

                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    icon: "qrc:/tools/suspend.png"
                    highlightedIcon: "qrc:/tools/suspend_h.png"
                    onTriggered: suspendDialog.visible = true
                }
                Item { Layout.fillWidth: true }
            }
        }

        Flickable {
            id: content
            Layout.fillHeight: true
            Layout.fillWidth: true

            clip: true
            flickableDirection: Flickable.VerticalFlick
            contentHeight: self.offerHeight
            contentWidth: width

            ColumnLayout {
                anchors.fill: parent
                spacing: 0
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
                        return Qt.rgba(textColor.r + (1 - textColor.r) * (1 - urgency), textColor.g * urgency, textColor.b * urgency, textColor.a + (1 - textColor.a) * urgency)
                    }
                }

                Rectangle {
                    Layout.alignment: Qt.AlignHCenter
                    Layout.preferredWidth: parent.width - 20
                    Layout.maximumWidth: parent.width - 20
                    color: self.editable ? "white" : "transparent"
                    TextEdit {
                        id: topic
                        anchors.fill: parent
                        horizontalAlignment: Qt.AlignHCenter
                        renderType: Text.NativeRendering
                        wrapMode: Text.WordWrap
                        color: Palette.selectedTextColor
                        text: offer ? offer.topic : ""
                        selectByMouse: true
                        readOnly: self.editable
                    }
                }

                Rectangle {
                    Layout.alignment: Qt.AlignHCenter
                    Layout.preferredWidth: parent.width - 20
                    Layout.maximumWidth: parent.width - 20
                    color: self.editable ? "white" : "transparent"
                    TextEdit {
                        id: comment
                        anchors.fill: parent
                        horizontalAlignment: Qt.AlignHCenter
                        renderType: Text.NativeRendering
                        wrapMode: Text.WordWrap
                        color: Palette.selectedTextColor
                        text: (!editable ? "Комментарий администратора: " : "") + (offer && offer.comment != "" ? offer.comment : "нет")
                        selectByMouse: true
                        readOnly: self.editable
                    }
                }

                Flow {
                    id: filterView
                    visible: task && task.tags.length > 0
                    Layout.alignment: Qt.AlignHCenter
                    Layout.preferredWidth: parent.width - 20
                    spacing: 3
                    Label {
                        visible: !!task && task.banned.length > 0
                        text: qsTr("Забанены: ")
                    }
                    Repeater {
                        model: task ? task.banned : []
                        delegate: Avatar {
                            userId: modelData
                            size: 22
                        }
                    }
                    Label {
                        visible: !!task && task.banned.length > 0
                        text: qsTr("Предпочитаются: ")
                    }
                    Repeater {
                        model: task ? task.preferred : []
                        delegate: Avatar {
                            userId: modelData
                            size: 22
                        }
                    }
                    Label {
                        visible: !!task && task.banned.length > 0
                        text: qsTr("Приняты: ")
                    }
                    Repeater {
                        model: task ? task.accepted : []
                        delegate: Avatar {
                            userId: modelData
                            size: 22
                        }
                    }
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

                Text {
                    id: geoLocal
                    Layout.alignment: Qt.AlignHCenter
                    Layout.preferredWidth: parent.width - 20
                    text: qsTr("Гео-специфичный: ") + (offer && offer.local ? qsTr("Да") : qsTr("Нет"))
                }

                Text {
                    id: attachmentsCount
                    Layout.alignment: Qt.AlignHCenter
                    Layout.preferredWidth: parent.width - 20
                    text: qsTr("Приложений: ") + (offer ? offer.images.length : "")
                }

                Item {
                    id: map
                    Layout.preferredHeight: 200
                    Layout.preferredWidth: 300
                    Layout.alignment: Qt.AlignHCenter

                    WebEngineView {
                        anchors.fill: parent
                        visible: offer && offer.hasLocation
                        url: offer ? "qrc:/html/yandex-map.html?latitude=" + offer.latitude + "&longitude=" + offer.longitude : ""
                    }
                    TransparentMouseArea {
                        anchors.fill: parent
                        onPressed: {
                            dosearch.navigation.handleOmnibox("qrc:/html/yandex-map.html?latitude=" + offer.latitude + "&longitude=" + offer.longitude, 0)
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
                                console.log("Status changed to " + status)
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
                            onClicked: dosearch.navigation.handleOmnibox(root.league.imageUrl(modelData), 0)
                        }
                    }
                }

                Repeater {
                    model: offer ? offer.images : []
                    delegate: imageView
                }
            }
        }
    }
}

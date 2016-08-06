import QtQuick 2.7
import QtQuick.Controls 2.0
import QtQuick.Layouts 1.1

import QtGraphicalEffects 1.0

import QtWebEngine 1.3

import ExpLeague 1.0

import "."

Item {
    id: self
//    property TagsDialog tagsDialog
    property Offer offer
    property Task task
    property color textColor: Palette.selectedTextColor

    implicitHeight: topic.implicitHeight + geoLocal.implicitHeight + 4 +
                    (task ? 33 : 0) + 4 +
                    (offer ? time.implicitHeight + 4 : 0) +
                    (offer && offer.hasLocation ? 200 + 4 : 0) +
                    ((offer ? offer.images.length * (200 + 4) : 0)) +
                    (tagsView.visible ? tagsView.implicitHeight + 4 : 0) +
                    (patternsView.visible ? patternsView.implicitHeight + 4 : 0) +
                    (callsView.visible ? callsView.implicitHeight + 4 : 0) +
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

    ColumnLayout {
        anchors.fill: parent
        Rectangle {
            color: Palette.backgroundColor
            visible: task
            Layout.preferredHeight: 33
            Layout.fillWidth: true
            RowLayout {
                anchors.fill: parent
                id: buttons
                spacing: 3
                Item {Layout.preferredWidth: 1}

                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    icon: "qrc:/tools/send.png"
                    onTriggered: sendDialog.visible = true
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    icon: "qrc:/tools/tags.png"
                    onTriggered: tagsDialog.visible = true
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    icon: "qrc:/tools/patterns.png"
                    onTriggered: patternsDialog.visible = true
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    icon: "qrc:/tools/phone.png"
                    onTriggered: callDialog.visible = true
                }

                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    icon: "qrc:/tools/suspend.png"
                    onTriggered: suspendDialog.visible = true
                }
                Item { Layout.fillWidth: true }
            }
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
                return Qt.rgba(textColor.r + (1 - textColor.r) * (1 - urgency), textColor.g * urgency, textColor.b * urgency, textColor.a + (1 - textColor.a) * urgency)
            }
        }

        TextEdit {
            id: topic
            Layout.alignment: Qt.AlignHCenter
            Layout.preferredWidth: parent.width - 20
            Layout.maximumWidth: parent.width - 20
            horizontalAlignment: Qt.AlignHCenter
            renderType: Text.NativeRendering
            wrapMode: Text.WordWrap
            color: Palette.selectedTextColor
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

        Text {
            id: geoLocal
            Layout.alignment: Qt.AlignHCenter
            Layout.preferredWidth: parent.width - 20
            text: qsTr("Гео-специфичный: ") + (offer && offer.local ? qsTr("Да") : qsTr("Нет"))
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

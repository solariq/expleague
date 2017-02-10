import QtQuick 2.5
import QtQuick.Window 2.2
import QtQuick.Controls 1.4
import QtQuick.Controls.Styles 1.4
import QtQuick.Layouts 1.1

import ExpLeague 1.0

Window {
    id: dialog

    property Task task
    property color backgroundColor: "#e8e8e8"
    property int invitationTimeout: 0
    property string text
    property string comment

    width: 350
    height: 200
    minimumHeight: height
    maximumHeight: height
    minimumWidth: width
    maximumWidth: width
    opacity: 0.9

    modality: Qt.WindowModal
    color: backgroundColor

    signal appendTag(TaskTag tag)
    Action {
        id: accept
        text: qsTr("Отправить")
        onTriggered: {
            task.commitOffer(dialog.text, dialog.comment)
            dialog.hide()
        }
    }

    Action {
        id: reject
        text: qsTr("Отменить")
        onTriggered: {
            dialog.hide()
        }
    }

    ColumnLayout {
        anchors.fill: parent
        Item {Layout.preferredHeight: 20}
        Text {
            Layout.alignment: Qt.AlignHCenter
            text: qsTr("Подготовка к отправке задания")
            font.bold: true
            font.pointSize: 14
        }
        Flickable {
            Layout.alignment: Qt.AlignHCenter
            Layout.fillHeight: true
            Layout.fillWidth: true
            contentHeight: contents.implicitHeight
            contentWidth: width - contents.anchors.margins * 2
            clip: true
            GridLayout {
                id: contents
                width: parent.width - anchors.margins * 2
                anchors.margins: 15
                anchors.horizontalCenter: parent.horizontalCenter
                columns: 2
                Text {
                    Layout.columnSpan: 2
                    Layout.fillWidth: true
                    Layout.preferredHeight: implicitHeight
                    horizontalAlignment: Text.AlignHCenter
                    text: dialog.text
                }

                Label {
                    text: qsTr("Комментарий:")
                }
                Item {}

                Text {
                    Layout.columnSpan: 2
                    Layout.fillWidth: true
                    Layout.preferredHeight: implicitHeight
                    text: dialog.comment !== "" ? dialog.comment : qsTr("Нет")
                    horizontalAlignment: Text.AlignHCenter
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
        Item { Layout.preferredHeight: 5 }
        Keys.onEscapePressed: {
            dialog.hide()
        }
    }
}

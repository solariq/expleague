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

    width: 350
    height: 250
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
        enabled: shortAnswer.text.length > 0
        onTriggered: {
            task.sendAnswer(shortAnswer.text)
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
            font.pointSize: 16
        }
        Item {
            Layout.alignment: Qt.AlignHCenter
            Layout.fillHeight: true
            GridLayout {
                anchors.margins: 15
                anchors.horizontalCenter: parent.horizontalCenter
                anchors.top: parent.top
                anchors.bottom: parent.bottom
                columns: 2

                Layout.alignment: Qt.AlignHCenter
                Label {
                    text: "Краткая версия ответа:"
                }

                TextField {
                    Layout.fillWidth: true
                    id: shortAnswer
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
    onVisibleChanged: {
        if (!visible) {
            shortAnswer.text = ""
        }
    }
}

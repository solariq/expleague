import QtQuick 2.5
import QtQuick.Window 2.2
import QtQuick.Controls 1.4
import QtQuick.Controls.Styles 1.4
import QtQuick.Layouts 1.1

import ExpLeague 1.0

import "."

Window {
    id: dialog

    property Task task
    property League league
    property color backgroundColor: Palette.backgroundColor
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

    signal suspend(int time)
    function time() {
        return ((parseInt("0" + days.text) * 24 + parseInt("0" + hours.text)) * 60 + parseInt("0" + minutes.text)) * 60
    }

    Action {
        id: accept
        text: qsTr("Да")
        enabled: {
            return time() > 0
        }
        onTriggered: {
            suspend(time())
            dialog.hide()
        }
    }

    Action {
        id: reject
        text: qsTr("Нет")
        onTriggered: {
            dialog.hide()
        }
    }

    ColumnLayout {
        anchors.fill: parent
        Item {Layout.preferredHeight: 20}
        Text {
            Layout.preferredWidth: 300
            Layout.alignment: Qt.AlignHCenter
            text: qsTr("Вы действительно хотите преостановить выполнение задания?")
            horizontalAlignment: Text.AlignHCenter
            wrapMode: Text.WordWrap
            renderType: Text.NativeRendering
            font.bold: true
            font.pointSize: 15
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
                Text {
                    renderType: Text.NativeRendering
                    Layout.alignment: Qt.AlignLeft
                    text: "Отложить на сколько"
                }

                RowLayout {
                    Layout.columnSpan: 1
                    Layout.alignment: Qt.AlignLeft
                    TextField {
                        Layout.preferredWidth: 20
                        id: days
                    }
                    Text {
                        text: "дней"
                    }
                    TextField {
                        Layout.preferredWidth: 20
                        id: hours
                    }
                    Text {
                        width: 30
                        text: "ч."
                    }
                    TextField {
                        Layout.preferredWidth: 20
                        id: minutes
                    }
                    Text {
                        width: 30
                        text: "м."
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
            }
        }
        Keys.onEscapePressed: {
            dialog.hide()
        }
    }
}

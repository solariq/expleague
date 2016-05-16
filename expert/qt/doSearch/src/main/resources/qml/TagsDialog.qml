import QtQuick 2.5
import QtQuick.Window 2.2
import QtQuick.Controls 1.4
import QtQuick.Controls.Styles 1.4
import QtQuick.Layouts 1.1

import ExpLeague 1.0

Window {
    id: dialog

    property Task task
    property League league
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
        text: qsTr("Добавить")
        enabled: {
            if (!task || tags.currentIndex < 0)
                return false
            var tag = league.tags[tags.currentIndex]
            for (var i = 0; i < task.tags.length; i++) {
                if (task.tags[i] === tag)
                    return false
            }
            return true
        }
        onTriggered: {
            dialog.appendTag(league.tags[tags.currentIndex])
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
            text: qsTr("Добавить тег к заданию")
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
                ComboBox {
                    Layout.columnSpan: 2
                    Layout.alignment: Qt.AlignHCenter

                    id: tags
                    textRole: "name"
                    model: league.tags
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

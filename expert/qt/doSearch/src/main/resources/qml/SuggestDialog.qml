import QtQuick 2.5
import QtQuick.Window 2.2
import QtQuick.Controls 1.4
import QtQuick.Controls.Styles 1.4
import QtQuick.Layouts 1.1

import ExpLeague 1.0

import "."

Window {
    id: self

    property Context suggest
    property Page page
    property real probability

    width: 350
    height: 250
    minimumHeight: height
    maximumHeight: height
    minimumWidth: width
    maximumWidth: width
    opacity: 0.9

    modality: Qt.WindowModal
    color: Palette.navigationColor

    Action {
        id: accept
        text: qsTr("Перенести")
        onTriggered: {
            dosearch.navigation.moveTo(page, suggest)
            self.hide()
        }
    }

    Action {
        id: reject
        text: qsTr("Оставить")
        onTriggered: {
            self.hide()
        }
    }

    ColumnLayout {
        anchors.centerIn: parent
        width: parent.width - 20
        height: parent.height - 20
        Item {Layout.preferredHeight: 20}
        Text {
            Layout.alignment: Qt.AlignHCenter
            text: qsTr("Перенести текущую страницу?")
            font.bold: true
            font.pointSize: 16
        }
        Text {
            Layout.alignment: Qt.AlignLeft
            Layout.maximumWidth: parent.width - 10
            text: qsTr("Рекомендуем (" + probability + ") перенести эту страницу в другой контекст: ")
            wrapMode: Text.WordWrap
            font.bold: false
            font.pointSize: 12
        }
        RowLayout {
            Layout.alignment: Qt.AlignHCenter
            Layout.preferredHeight: 40
            Layout.preferredWidth: 40 + suggestCaption.implicitWidth
            Image {
                Layout.alignment: Qt.AlignVCenter
                Layout.preferredHeight: 30
                Layout.preferredWidth: 30
                source: suggest.icon
            }
            Text {
                id: suggestCaption
                Layout.alignment: Qt.AlignVCenter
                text: suggest.title
                font.bold: true
                font.pointSize: 14
            }
        }
        Item {Layout.fillHeight: true}
        RowLayout {
            Layout.fillWidth: true
            Layout.preferredHeight: acceptButton.implicitHeight + 5
            Button {
                Layout.preferredWidth: 130
                action: reject
            }
            Item { Layout.fillWidth: true }
            Button {
                id: acceptButton
                focus: true
                Layout.preferredWidth: 130
                action: accept
            }
        }

        Keys.onEscapePressed: {
            self.hide()
        }
    }
}

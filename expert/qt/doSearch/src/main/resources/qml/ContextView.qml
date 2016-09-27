import QtQuick 2.7
import QtQuick.Window 2.0
import QtQuick.Controls 2.0
import QtQuick.Controls 1.4 as Legacy
import QtQuick.Layouts 1.1
import QtQuick.Dialogs 1.2

import QtWebEngine 1.3

import ExpLeague 1.0

import "."

Rectangle {
    property alias downloads: downloadsPage
    anchors.fill: parent
    color: Palette.navigationColor
    Item {
        anchors.fill: parent
        anchors.margins: 4

        ColumnLayout {
            anchors.fill: parent
            Label {
                font.pointSize: 14
                text: qsTr("Загрузки")
                color: Palette.activeTextColor
            }
            DownloadsPage {
                Layout.fillWidth: true
                Layout.fillHeight: true
                id: downloadsPage
            }
        }
    }
}

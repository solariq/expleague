import QtQuick 2.5
import QtQuick.Controls 1.4
import QtQuick.Layouts 1.1

import ExpLeague 1.0

Item {
    property Offer offer
    implicitHeight: topic.implicitHeight + 4

    ColumnLayout {
        anchors.fill: parent
        Text {
            id: topic
            Layout.alignment: Qt.AlignHCenter
            renderType: Text.NativeRendering
            text: offer ? offer.topic : ""
        }
    }
}

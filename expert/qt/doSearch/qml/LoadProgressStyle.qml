import QtQuick 2.2
import QtQuick.Controls.Styles 1.2

ProgressBarStyle {
    background: Rectangle {
        radius: 2
        color: "transparent"
        implicitHeight: urlField.height - 2
        implicitWidth: urlField.width
    }
    progress: Rectangle {
        anchors.fill: parent
        color: "#80c342"
        opacity: 0.5
    }

}

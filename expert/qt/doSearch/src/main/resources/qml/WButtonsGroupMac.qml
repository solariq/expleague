import QtQuick 2.5
import QtQuick.Window 2.0
import QtQuick.Layouts 1.1

Item {
    property Window win
    z: parent.z + 10
    implicitWidth: 13 * 3 + 5 * 2
    RowLayout {
        id: macButtons
        anchors.fill: parent
        spacing: 6
        WindowButton {
            id: closeButton
            icon: "qrc:/window/close.png"

            w: win
            windowButtons: windowButtons
            Layout.preferredWidth: 13
            Layout.preferredHeight: 13
            anchors.verticalCenter: parent.verticalCenter
            onClicked: {
                w.close()
            }
        }
        WindowButton {
            id: minimizeButton
            icon: "qrc:/window/minimize.png"

            w: win
            windowButtons: windowButtons
            Layout.preferredWidth: 13
            Layout.preferredHeight: 13
            anchors.verticalCenter: parent.verticalCenter
            onClicked: {
                win.visibility = Window.Minimized
            }
        }
        WindowButton {
            id: maximizeButton
            icon: "qrc:/window/maximize.png"
            iconMaximized: "qrc:/window/maximize_maximized.png"

            w: win
            windowButtons: windowButtons
            Layout.preferredWidth: 13
            Layout.preferredHeight: 13
            anchors.verticalCenter: parent.verticalCenter
            onClicked: {
                if (win.visibility === Window.Maximized || win.visibility === Window.FullScreen) {
                    win.visibility = Window.AutomaticVisibility
                }
                else {
                    win.visibility = Window.FullScreen
                }
            }
        }
    }
    TransparentMouseArea {
        id: windowButtons
        anchors.fill: parent
    }
}

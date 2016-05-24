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
            id: minimizeButton
            iconHover: "image://standard/SP_TitleBarMinButton_h"
            iconPressed: "image://standard/SP_TitleBarMinButton_a"
            iconBg: "image://standard/SP_TitleBarMinButton_d"
//            iconPassive: "image://standard/SP_TitleBarMinButton"
            iconPassive: "image://standard/SP_TitleBarCloseButton_a"

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

            iconHover: "image://standard/SP_TitleBarMaxButton_h"
            iconPressed: "image://standard/SP_TitleBarMaxButton_a"
            iconBg: "image://standard/SP_TitleBarMaxButton_d"
//            iconPassive: "image://standard/SP_TitleBarMaxButton"
            iconPassive: "image://standard/SP_TitleBarCloseButton_h"

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
        WindowButton {
            id: closeButton
            iconHover: "image://standard/SP_TitleBarCloseButton_h"
            iconPressed: "image://standard/SP_TitleBarCloseButton_a"
            iconBg: "image://standard/SP_TitleBarCloseButton_d"
            iconPassive: "image://standard/SP_TitleBarCloseButton"

            w: win
            windowButtons: windowButtons
            Layout.preferredWidth: 13
            Layout.preferredHeight: 13
            anchors.verticalCenter: parent.verticalCenter
            onClicked: {
                w.close()
            }
        }
    }
    TransparentMouseArea {
        id: windowButtons
        anchors.fill: parent
    }
}

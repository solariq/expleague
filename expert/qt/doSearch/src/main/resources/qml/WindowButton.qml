import QtQuick 2.5

import QtQuick.Controls 1.4
import QtQuick.Window 2.2
import QtQuick.Controls.Styles 1.4
import QtQuick.Layouts 1.1

Button {
    property string icon
    property url iconPassive: icon.substring(0, icon.length - 4) + "_passive.png"
    property url iconActive: icon.substring(0, icon.length - 4) + "_active.png"
    property url iconBg: "qrc:/window/grey.png"
    property url iconMaximized: iconActive
    property Window w

    property MouseArea windowButtons

    property url _icon: iconBg

    id: button
    anchors.verticalCenter: parent.verticalCenter
    style: ButtonStyle {
        background: Image {
            fillMode: Image.PreserveAspectFit
            anchors.fill: parent
            mipmap: true
            source: button._icon
            sourceSize: "26x26"
        }
    }
    states: [
        State {
            name: "Window background"
            when: !w.active && !windowButtons.containsMouse
            PropertyChanges {
                target: button
                _icon: iconBg
            }
        },
        State {
            name: "Window maximized"
            when: w.visibility === Window.Maximized || w.visibility === Window.FullScreen
            PropertyChanges {
                target: button
                _icon: iconMaximized
            }
        },
        State {
            name: "Window active"
            when: w.active && !windowButtons.containsMouse
            PropertyChanges {
                target: button
                _icon: iconPassive
            }
        },
        State {
            name: "Window active, mouse over"
            when: windowButtons.containsMouse
            PropertyChanges {
                target: button
                _icon: iconActive
            }
        }
    ]
}

import QtQuick 2.5
import QtQuick.Controls 2.0
import QtQuick.Layouts 1.1
import QtGraphicalEffects 1.0

import ExpLeague 1.0

import "."

RowLayout {
    property NavigationManager navigation: null
    id: tabs
    spacing: 0
    Rectangle {
        id: activeContext

        Layout.fillHeight: true
        Layout.preferredWidth: parent.height
        Layout.alignment: Qt.AlignVCenter
//        color: navigation.activePage === navigation.context ? Palette.selectedColor : Palette.activeColor
        radius: Palette.radius
        Image {
            source: navigation.context ? navigation.context.icon : ""
            mipmap: true
            anchors.centerIn: parent
            width: 20
            height: 20
            fillMode: Image.PreserveAspectFit
        }

        MouseArea {
            anchors.fill: parent
            onClicked: {
                navigation.select(0, navigation.context)
            }
        }
    }
    Item {Layout.preferredWidth: 6}
    Repeater {
        model: navigation.groups
        onModelChanged: {
            console.log("navigation tabs model changed")
        }

        delegate: NavigationGroup {
            Layout.fillHeight: true
            Layout.preferredWidth: implicitWidth

            height: parent.height
            group: modelData
        }
    }
    Item {
        Layout.fillWidth: true
        Layout.minimumWidth: 4
    }

}

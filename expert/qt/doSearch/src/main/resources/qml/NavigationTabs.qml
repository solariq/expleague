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
    Item {
        id: activeContext

        Layout.fillHeight: true
        Layout.preferredWidth: 20
        Layout.alignment: Qt.AlignVCenter

        Image {
            source: navigation.context ? navigation.context.icon : ""
            mipmap: true
            anchors.centerIn: parent
            width: parent.width
            height: width
            fillMode: Image.PreserveAspectFit
        }
    }
    Item {Layout.preferredWidth: 6}
    Repeater {
        model: navigation.groups

        delegate: NavigationGroup {
            Layout.fillHeight: true
            Layout.preferredWidth: implicitWidth
            Layout.alignment: Qt.AlignVCenter

            height: parent.height
            group: modelData
        }
    }
    Item {
        Layout.fillWidth: true
        Layout.minimumWidth: 4
    }

    NavigationGroup {
        id: contexts

        Layout.fillHeight: true
        Layout.preferredWidth: implicitWidth
        Layout.alignment: Qt.AlignVCenter

        group: navigation.contextsGroup
        closeEnabled: false

        append: function() {
            navigation.activate(dosearch.createContext())
        }
    }
}

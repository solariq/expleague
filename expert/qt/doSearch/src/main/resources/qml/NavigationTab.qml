import QtQuick 2.5
import QtQuick.Controls 2.0
import QtQuick.Controls.Styles 1.4
import QtQuick.Layouts 1.1
import QtGraphicalEffects 1.0

import ExpLeague 1.0

import "."

Item {
    id: self
    property Page page
    property color textColor: "black"
    property bool closeEnabled: true
    property alias hover: tabMouseArea.containsMouse
    property string state
    property bool imageOnly: page.title.length === 0
    property bool showTree
    implicitWidth: imageOnly ? 24 : Math.min(invisibleText.implicitWidth, 200) + 24 + 4
    implicitHeight: 24
    //    z: parent.z + (self.state == "selected" ? 2 : (self.state == "active" ? 1 : 0))
    clip: false


    Item {
        id: tab
        Drag.hotSpot.x: width/2
        Drag.hotSpot.y: height/2
        Drag.active: tabMouseArea.drag.active
        Drag.keys: "tab"

        anchors.verticalCenter: parent.verticalCenter
        anchors.horizontalCenter: parent.horizontalCenter

        width: self.width
        height: self.height

        Component.onDestruction: {
            if(Drag.active){
                Drag.drop()
            }
        }

        MouseArea {
            id: tabMouseArea
            anchors.fill: parent
            hoverEnabled: true
            drag.target: tab
            propagateComposedEvents: true
            onClicked: {
                dosearch.navigation.select(group, page)
            }

            onPositionChanged: {
            }

            onReleased: {
                if (drag.active) {
                    tab.Drag.drop()
                    mouse.accepted = true
                }
            }


        }
        Rectangle{
            id: leftBorder
            anchors.top: parent.top
            anchors.left: parent.left
            width: self.state == "idle" ? 1 : 0
            height: parent.height
            color: Palette.borderColor(self.state)
            z: parent.z + 1
        }

        Rectangle {
            id: background
            x: self.state == "selected" ? -1 : (self.state == "active" ? -1 : 0)
            y: self.state != "idle" ? -1 : 0
            width: parent.width + (self.state == "selected" ? 2 : (self.state == "active" ? 1 : 0))
            height: parent.height + (self.state != "idle" ? 2 : 0)
            border.color: Palette.borderColor(self.state)
            border.width: self.state == "selected" ? 2 : (self.state == "active" ? 1 : 0)
            radius: self.state != "idle" ? Palette.radius : 0
            color: Palette.backgroundColor(self.state)
        }


        Rectangle {
            //        visible: self.state != "idle"
            x: self.state == "selected" ? -2 : (self.state == "active" ? -1 : 0)
            width: parent.width + (self.state == "selected" ? 4 : (self.state == "active" ? 2 : 0))
            border.color: Palette.borderColor(self.state)
            border.width: self.state == "selected" ? 2 : (self.state == "active" ? 1 : 0)
            radius: self.state != "idle" ? Palette.radius : 0
            color: Palette.backgroundColor(self.state)
        }


        RowLayout {
            anchors.fill: parent
            spacing: 0
            Item { Layout.preferredWidth: self.imageOnly ? 2 : 3 }
            Image {
                Layout.alignment: Qt.AlignVCenter
                Layout.preferredHeight: self.imageOnly ? 20 : 14
                Layout.preferredWidth: self.imageOnly ? 20 : 14

                id: crossIcon
                z: parent.z + 1
                visible: tabMouseArea.containsMouse || this['icon'] !== null
                source: closeEnabled && tabMouseArea.containsMouse ? "qrc:/cross.png" : this['icon'] !== null ? page.icon : ""
                fillMode: Image.PreserveAspectFit
                mipmap: true
                MouseArea {
                    visible: closeEnabled
                    anchors.fill: parent
                    onClicked: {
                        dosearch.navigation.close(group, page)
                    }
                }
            }
            Item { Layout.preferredWidth: self.imageOnly ? 2 : 3 }
            Text {
                Layout.fillWidth: true
                Layout.preferredHeight: implicitHeight
                Layout.maximumWidth: 200
                id: text

                width: parent.width - crossIcon.width
                visible: !self.imageOnly
                verticalAlignment: Text.AlignVCenter
                horizontalAlignment: Text.AlignLeft
                renderType: Text.NativeRendering
                anchors {
                    horizontalCenterOffset: 6
                    //                verticalCenterOffset: 1
                }
                color: Palette.textColor(self.state)

                font.weight: self.state == "selected" ? Font.Medium : Font.Normal
                font.pixelSize:  13
                font.family: "Helvetica"
                text: {
                    page.title.replace("\n", " ")
                }
                elide: Text.ElideRight
            }
            Item {
                Layout.preferredWidth: 3
                visible: !self.imageOnly
            }
        }
        states: [
            State {
                name: "drag"
                when: tabMouseArea.drag.active
                ParentChange { target: tab; parent: dosearch.main.screenRef }
                AnchorChanges { target: tab; anchors.verticalCenter: undefined; anchors.horizontalCenter: undefined }
                PropertyChanges { target: tab; z: 100 }
                PropertyChanges { target: dosearch.main; drag: page; dragType: "page" }
            }]
    }
    Text {
        id: invisibleText
        visible: false
        font.weight: self.state == "selected" ? Font.Medium : Font.Normal
        font.pixelSize:  13
        font.family: "Helvetica"
        text: page.title.replace("\n", " ")
    }
}

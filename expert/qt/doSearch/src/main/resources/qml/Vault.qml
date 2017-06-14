import QtQuick 2.7
import QtQuick.Controls 2.0
import QtQuick.Layouts 1.1
import QtQml.Models 2.2

import ExpLeague 1.0

import "."
Rectangle {
    id: self

    property Context context
    property bool editMode: false
    property real size: 13
    property var owner: context.vault
    property var group: owner.activeGroup != null ? owner.activeGroup : []
    property var items: group.items
    property int moveTo: -1

    color: Palette.toolsBackground


    DropArea {
        id: dropArea
        visible: !editMode
        anchors.fill: parent
        onEntered: {
            //            console.log("Entered vault drop area")
            if (dosearch.main.dragType != "web" && dosearch.main.dragType != "delay")
                return
            dosearch.main.dragType = "web"
            dosearch.main.drag = drag.source
        }

        onExited: {
            if (dosearch.main.dragType != "web" && dosearch.main.dragType != "delay")
                return
            dosearch.main.dragType = "delay"
            dosearch.main.delay(100, function () {
                if (dosearch.main.dragType == "delay")
                    dosearch.main.drag = null
            })
        }

        onDropped: {
            if (dosearch.main.dragType != "web" && dosearch.main.dragType != "delay")
                return
            drop.getDataAsString(drop.formats[1])
            var source = "empty"
            var src = drop.source
            dosearch.main.drag = null

            if (!!src) {
                for (var i in src.keys) {
                    if (src.keys[i] == "vault")
                        return
                }
            }

            if (drop.source && drop.source.toString().search("Main_QMLTYPE") >= 0) {
                source = dosearch.navigation.activePage.id
            }

            if (context.vault.drop(drop.text, drop.html, drop.urls, source)) {
                drop.accept()
            }
        }
    }

    ColumnLayout {
        anchors.fill: parent
        spacing: 0

        Item {
            Layout.preferredHeight: 33
            Layout.fillWidth: true
            anchors.margins: 2

            RowLayout {
                anchors.centerIn: parent
                height: 27
                width: parent.width - 16
                spacing: 5
                ToolbarButton {
                    id: zoomIn
                    icon: "qrc:/tools/zoom-in.png"
                    highlightedIcon: "qrc:/tools/zoom-in_h.png"
                    dark: true
                    onTriggered: {
                        self.size += 1
                    }
                }
                ToolbarButton {
                    id: zoomOut
                    icon: "qrc:/tools/zoom-out.png"
                    highlightedIcon: "qrc:/tools/zoom-out_h.png"
                    dark: true
                    onTriggered: {
                        if (self.size > 10)
                            self.size -= 1
                    }
                }
                ToolbarButton {
                    id: paste
                    icon: "qrc:/tools/paste.png"
                    highlightedIcon: "qrc:/tools/paste_h.png"

                    dark: true
                    onTriggered: {
                        context.vault.paste()
                    }
                }
                Item {Layout.fillWidth: true}
                ToolbarButton {
                    id: editVaultButton
                    icon: "qrc:/tools/edit.png"
                    highlightedIcon: "qrc:/tools/edit_h.png"

                    toggle: self.editMode
                    dark: true
                    onTriggered: {
                        self.editMode = !self.editMode
                    }
                }
            }
        }
        Item {
            visible: owner.activeGroup.toString().indexOf("Knugget") >= 0
            Layout.preferredHeight: 33
            Layout.fillWidth: true
            anchors.margins: 2
            RowLayout {
                anchors.centerIn: parent
                height: 27
                width: parent.width - 16
                spacing: 5
                ToolbarButton {
                    id: back
                    icon: "qrc:/tools/back.png"
                    highlightedIcon: "qrc:/tools/back_h.png"
                    dark: true
                    onTriggered: {
                        owner.activeGroup = group.group ? group.group : context.vault
                    }
                }
                TextEdit {
                    id: groupName
                    Layout.fillWidth: true
                    Layout.preferredHeight: implicitHeight
                    Layout.alignment: Qt.AlignCenter
                    text: owner.activeGroup["title"] ? owner.activeGroup.title : ""
                    font.pixelSize: 24
                    color: "white"
                    Keys.onReturnPressed: {
                        owner.activeGroup.setName(groupName.text)
                        groupName.focus = false
                    }
                    onTextChanged: {
                        if (text == qsTr("Новая группа")) {
                            groupName.selectAll()
                            groupName.forceActiveFocus()
                        }
                    }
                }
                ToolbarButton {
                    id: ungroupButton
                    icon: "qrc:/cross.png"
                    dark: true
                    onTriggered: {
                        owner.ungroup(owner.activeGroup)
                    }
                }
            }
        }
        GridView {
            id: itemsFlow
            Layout.alignment: Qt.AlignHCenter
            Layout.preferredWidth: parent.width - 2 * self.size
            topMargin: 10
            Layout.fillHeight: true
            contentWidth: width
            clip: true
            cellWidth: self.size * 11; cellHeight: self.size * 11

            displaced: Transition {
                NumberAnimation { properties: "x,y"; easing.type: Easing.OutQuad;}
            }

            model: visualModel
            DelegateModel {
                id: visualModel
                model: self.items
                delegate: tileComponent
            }

        }
    }

    Component {
        id: tileComponent
        MouseArea {
            id: tileArea

            property int visualIndex: DelegateModel.itemsIndex

            width: tile.width
            height: tile.height
            hoverEnabled: true
            propagateComposedEvents: true
            onClicked: open()
            onDoubleClicked: mouse.accepted = false
            onPressAndHold: mouse.accepted = false

            drag.target: tile

            VaultTile {
                id: tile

                editMode: self.editMode
                item: modelData
                size: self.size
                visualIndex: tileArea.visualIndex
                ownerModel: visualModel
                vault: self
                tileArea: tileArea
            }

            onReleased: {
                if (drag.active && !!drag.target && drag.target !== dropArea) {
                    var activeGroup = owner.activeGroup
                    tile.Drag.drop()
                    if (activeGroup === owner.activeGroup && self.moveTo >= 0) {
                        var moveFrom = -1;
                        for (var i in owner.activeGroup.items) {
                            if (owner.activeGroup.items[i] === drag.target.item) {
                                moveFrom = i
                                break
                            }
                        }

                        owner.move(moveFrom, self.moveTo)
                        self.moveTo = -1
                    }
                }
                else mouse.accepted = false
            }
        }
    }
}

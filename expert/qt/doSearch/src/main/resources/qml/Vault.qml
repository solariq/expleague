import QtQuick 2.7
import QtQuick.Controls 2.0
import QtQuick.Layouts 1.1

import ExpLeague 1.0

import "."
Rectangle {
    id: self

    property Context context
    property bool editMode: false
    property real size: 8

    color: Palette.navigationColor

    DropArea {
        id: dropArea
        anchors.fill: parent
        onEntered: {
            console.log("Entered vault drop area")
            dosearch.main.drag = drag.source
        }

        onExited: {
            dosearch.main.drag = "delay"
            dosearch.main.delay(100, function () {
                if (dosearch.main.drag == "delay")
                    dosearch.main.drag = null
            })
        }

        onDropped: {
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

        Rectangle {
            Layout.preferredHeight: 33
            Layout.fillWidth: true
            anchors.margins: 2
            gradient: Palette.navigationGradient

            RowLayout {
                anchors.centerIn: parent
                height: 27
                width: parent.width - 6
                spacing: 5
                ToolbarButton {
                    id: editVaultButton
                    icon: "qrc:/tools/edit.png"
                    toggle: self.editMode
                    onTriggered: self.editMode = !self.editMode
                }
                ToolbarButton {
                    id: paste
                    icon: "qrc:/tools/paste.png"
                    onTriggered: {
                        context.vault.paste()
                    }
                }
                ToolbarButton {
                    id: zoomIn
                    icon: "qrc:/tools/zoom-in.png"
                    onTriggered: {
                        self.size += 1
                    }
                }
                ToolbarButton {
                    id: zoomOut
                    icon: "qrc:/tools/zoom-out.png"
                    onTriggered: {
                        self.size -= 1
                    }
                }
                Item {Layout.fillWidth: true}
            }
        }
        Flickable {
            Layout.fillWidth: true
            Layout.fillHeight: true
            contentWidth: width
            contentHeight: itemsFlow.implicitHeight
            rightMargin: 4
            leftMargin: 4
            topMargin: 4
            bottomMargin: 4
            clip: true

            Flow {
                id: itemsFlow
                width: parent.width - 8
                spacing: 5
                Repeater {
                    model: context.vault.items
                    delegate: Component {
                        MouseArea {
                            id: thumbnailArea
                            width: self.size * 10 + 6
                            height: self.size * 10 + 6
                            hoverEnabled: true
                            propagateComposedEvents: true
                            onClicked: open()
                            onDoubleClicked: mouse.accepted = false
                            onPressAndHold: mouse.accepted = false

                            drag.target: thumbnail

                            onReleased: {
                                if (drag.active && !!drag.target && drag.target !== dropArea) {
                                    mouse.accepted = true
                                    thumbnail.Drag.drop()
                                }
                                else mouse.accepted = false
                            }

                            Rectangle {
                                id: thumbnail
                                radius: Palette.radius
                                width: thumbnailArea.width
                                height: thumbnailArea.height
                                anchors.margins: 3

                                anchors.verticalCenter: parent.verticalCenter
                                anchors.horizontalCenter: parent.horizontalCenter

                                color: thumbnailArea.containsMouse || self.editMode ? Palette.selectedColor : Palette.activeColor
                                property color textColor: thumbnailArea.containsMouse || self.editMode ? Palette.selectedTextColor : Palette.activeTextColor
                                children: [ui(false)]
                                onChildrenChanged: {
                                    for (var i in children) {
                                        var child = children[i]
                                        child.visible = true
                                        child.parent = thumbnail
                                        child.color = Qt.binding(function () {return thumbnail.color})
                                        child.textColor = Qt.binding(function () {return thumbnail.textColor})
                                        child.width = Qt.binding(function () {return thumbnail.width - 6})
                                        child.height = Qt.binding(function () {return thumbnail.height - 6})
                                        child.enabled = false
                                        child.size = Qt.binding(function () {return self.size})
                                        child.hover = Qt.binding(function () {return thumbnailArea.containsMouse})
                                    }
                                }
                                Drag.dragType: Drag.Automatic
                                Drag.active: thumbnailArea.drag.active

                                Drag.mimeData: { "text/plain": md, "vault": thumbnail }
                                Drag.keys: ["text/plain", "vault"]
                            }
                            RowLayout {
                                id: tools
                                visible: self.editMode
                                anchors.centerIn: parent
                                width: 20
                                height: 20
                                ToolbarButton {
                                    id: close
                                    anchors.centerIn: parent
                                    icon: "qrc:/cross.png"
                                    size: 16
                                    onTriggered: {
                                        context.vault.remove(modelData)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

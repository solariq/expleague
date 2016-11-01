import QtQuick 2.7
import QtQuick.Controls 2.0
import QtQuick.Layouts 1.1
import QtGraphicalEffects 1.0

import ExpLeague 1.0

import "."

Item {
    id: self
    property PagesGroup group: null
    property var append: null
    property var visiblePages: group.visiblePages
    property var activePages: group.activePages
    property var closedPages: group.closedPages
    property bool closeEnabled: true

    visible: visiblePages.length > 0// || closedPages.length > 0
    implicitWidth: visibleList.implicitWidth + (group.parentGroup ? separator.width: 0)

    RowLayout {
        anchors.fill: parent
        spacing: 0
        RowLayout {
            Layout.fillHeight: true
            Layout.preferredWidth: 24
            Layout.minimumWidth: 24
            id: separator
            visible: group.parentGroup
            spacing: 0
            Item {
                Layout.preferredWidth: 8
                visible: self.group.type === PagesGroup.SUGGEST
            }
            Image {
                Layout.alignment: Qt.AlignVCenter
                Layout.preferredHeight: 11
                Layout.preferredWidth: self.group.type === PagesGroup.SUGGEST ? 12 : 20

                source: self.group.type === PagesGroup.SUGGEST ? "qrc:/tools/graph-arrow_suggest.png" : "qrc:/tools/graph-arrow_child.png"
            }
            Item {
                Layout.preferredWidth: 4
            }
        }

        Item {
            Layout.alignment: Qt.AlignVCenter
            Layout.preferredHeight: 22
            Layout.preferredWidth: visibleList.implicitWidth

            Rectangle {
                id: mask
                x: -1
                y: -1
                width: parent.width + 2
                height: parent.height + 2
                color: Palette.borderColor("idle")
                radius: Palette.radius
                smooth: true
            }

            Row {
                id: visibleList
                spacing: 1
                anchors.fill: parent

                Repeater {
                    delegate: NavigationTab {
                        height: visibleList.height
                        width: implicitWidth
                        state: {
                            if (group.selectedPage !== modelData)
                                return "idle"
                            else if (selected)
                                return "selected"
                            else
                                return "active"
                        }
                        closeEnabled: self.closeEnabled
                    }

                    implicitWidth: {
                        var result = 0
                        for(var i in contentItem.children) {
                            var child = contentItem.children[i]
                            if (!child.visible)
                                continue
                            result += child.implicitWidth
                        }
                        return result + 1
                    }
                    model: visiblePages

                }
                Button {
                    id: others
                    property bool opened: false
                    height: parent.height
                    visible: self.activePages.length > self.visiblePages.length
                    enabled: !opened

                    background: Rectangle {
                        Layout.fillHeight: true
                        Layout.fillWidth: true
                        color: popup.visible ? Palette.selectedColor: Palette.idleColor
                    }

                    indicator: Image {
                        anchors.centerIn: parent
                        width: 7
                        height: 4
                        source: "qrc:/tools/rollup-menu-arrow.png"
                    }
                    property real closedTime: 0
                    onClicked: {
                        var now = new Date().getTime()
                        if (now - closedTime > 200)
                            popup.open()
                    }
                }
            }

            Popup {
                x: Math.max(parent.width - width, 0)
                y: parent.mapFromItem(others.parent, 0, others.y).y + others.height + 1
                width: flickableContainer.contentWidth + 4
                height: Math.min(flickableContainer.contentHeight + 4, dosearch.main.height - 300)
                id: popup
                clip: true
                modal: false
                focus: true
                padding: 2
                closePolicy: Popup.CloseOnEscape | Popup.CloseOnPressOutside

                onVisibleChanged: {
                    if (!visible) {
                        others.closedTime = new Date().getTime()
                    }
                }

                Rectangle {
                    color: Palette.navigationColor
                    anchors.fill: parent
                    Flickable {
                        id: flickableContainer

                        anchors.fill: parent
                        flickableDirection: Flickable.VerticalFlick
                        topMargin: 0
                        rightMargin: 0
                        bottomMargin: 0
                        leftMargin: 0

                        contentHeight: 24 * (self.activePages.length + self.closedPages.length) + (closedLabel.visible ? closedLabel.height : 0)
                        contentWidth: {
                            var result = 0
                            for(var i in foldedList.children) {
                                var child = foldedList.children[i]
                                if (!child.visible)
                                    continue
                                result = Math.max(child.implicitWidth, result)
                            }
                            return result
                        }

                        Column {
                            id: foldedList
                            anchors.fill: parent
                            spacing: 0
                            Repeater {
                                delegate: NavigationTab {
                                    width: flickableContainer.contentWidth
                                    height: 24
                                    anchors.left: parent.left
                                    state: {
                                        var visible = false
                                        for (var i in self.visiblePages) {
                                            if (self.visiblePages[i] === modelData) {
                                                visible = true
                                                break
                                            }
                                        }

                                        if (visible) {
                                            return hover ? "selected" : "active"
                                        }
                                        else {
                                            return hover ? "active" : "idle"
                                        }
                                    }
                                    closeEnabled: false
                                }
                                model: self.activePages
                            }
                            Text {
                                id: closedLabel
                                text: qsTr("Закрытые:")
                                color: Palette.idleTextColor
                                visible: closedPages.length > 0
                            }
                            Repeater {
                                delegate: NavigationTab {
                                    width: flickableContainer.contentWidth
                                    height: 24
                                    state: hover ? "active" : "idle"
                                    textColor: hover ? Palette.selectedTextColor : Palette.idleTextColor
                                    closeEnabled: false
                                }
                                model: self.closedPages
                            }
                        }
                    }
                }
            }
        }
    }
}

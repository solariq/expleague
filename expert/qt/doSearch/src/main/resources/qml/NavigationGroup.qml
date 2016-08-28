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
    property var foldedPages: group.foldedPages
    property var closedPages: group.closedPages
    property bool closeEnabled: true

    implicitWidth: visibleList.implicitWidth + (group.parentGroup ? separator.width : 0)

    RowLayout {
        anchors.fill: parent
        spacing: 0
        Item {
            id: separator
            Layout.fillHeight: true
            Layout.preferredWidth: separatorText.implicitWidth + 10
            visible: group.parentGroup && (visiblePages.length > 0 || foldedPages.length > 0)

            Text {
                id: separatorText
                anchors.centerIn: parent
                text: {
//                    group.selectedPageIndex >= 0 ? ">" : ">>"
                    if (group.selectedPage)
                        return ">"
                    else
                        return ">>"
                }
                color: Palette.idleTextColor
            }
        }
        Item {
            Layout.fillHeight: true
            Layout.preferredWidth: visibleList.implicitWidth
            Row {
                id: visibleList
                anchors.fill: parent
                spacing: 1
                Repeater {
                    delegate: NavigationTab {
                        height: self.height
                        width: implicitWidth
                        color: {
                            if (group.selectedPage !== modelData)
                                return Palette.idleColor
                            else if (selected)
                                return Palette.selectedColor
                            else
                                return Palette.activeColor
                        }
                        textColor: {
                            if (group.selectedPage !== modelData)
                                return Palette.idleTextColor
                            else if (selected)
                                return Palette.selectedTextColor
                            else
                                return Palette.activeTextColor
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
                    id: appendButton
                    height: parent.height
                    visible: append != null

                    background: Rectangle {
                        Layout.fillHeight: true
                        Layout.fillWidth: true
                        color: appendButton.pressed ? Palette.selectedColor: Palette.activeColor
                    }
                    contentItem: Text {
                        anchors.fill: parent
                        horizontalAlignment: Text.AlignHCenter
                        verticalAlignment: Text.AlignVCenter
                        text: "+"
                        color: appendButton.pressed ? Palette.selectedTextColor: Palette.activeTextColor
                    }

                    onClicked: self.append()
                }

                Button {
                    id: others
                    property bool opened: false
                    height: parent.height
                    visible: foldedPages.length > 0
                    enabled: !opened

                    background: Rectangle {
                        Layout.fillHeight: true
                        Layout.fillWidth: true
                        color: popup.visible ? Palette.selectedColor: (group.selected? Palette.activeColor : Palette.idleColor)
                    }
                    contentItem: Text {
                        anchors.fill: parent
                        horizontalAlignment: Text.AlignHCenter
                        verticalAlignment: Text.AlignVCenter
                        text: ("➥") + foldedPages.length
                        color: popup.visible ? Palette.selectedTextColor: (group.selected ? Palette.activeTextColor : Palette.idleTextColor)
                    }
                    property real closedTime: 0
                    onClicked: {
                        var now = new Date().getTime()
                        if (now - closedTime > 200)
                            popup.open()
                    }
                }
            }

            Rectangle {
                id: mask
                anchors.fill: visibleList
                radius: Palette.radius
                smooth: true
                visible: false
            }

            OpacityMask {
                z: parent.z + 10
                anchors.fill: visibleList
                source: visibleList
                maskSource: mask
            }
            Rectangle {
                z: parent.z + 5
                anchors.fill: visibleList
                color: Palette.backgroundColor
            }

            Popup {
                x: Math.max(parent.width - width, 0)
                y: parent.mapFromItem(others.parent, 0, others.y).y + others.height + 1
                width: flickableContainer.contentWidth+ 4
                height: (20 + 1) * foldedPages.length + 4 + (closedPages.length > 0 ? (20 + 1) * closedPages.length + closedLabel.height : 0)

                id: popup
                clip: true
                modal: false
                focus: true
                padding: 0
                closePolicy: Popup.CloseOnEscape | Popup.CloseOnPressOutside

                onVisibleChanged: {
                    if (!visible) {
                        others.closedTime = new Date().getTime()
                    }
                }

                Rectangle {
                    color: Palette.navigationColor
                    anchors.margins: 2
                    anchors.fill: parent
                    Flickable {
                        id: flickableContainer

                        anchors.fill: parent
                        flickableDirection: Flickable.VerticalFlick
                        topMargin: 0
                        rightMargin: 0
                        bottomMargin: 0
                        leftMargin: 0

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
                            spacing: 1
                            Repeater {
                                delegate: NavigationTab {
                                    width: flickableContainer.contentWidth
                                    height: 20
                                    color: hover ? Palette.selectedColor : Palette.idleColor
                                    textColor: hover ? Palette.selectedTextColor : Palette.idleTextColor
                                    closeEnabled: false
                                }
                                model: foldedPages
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
                                    height: 20
                                    color: hover ? Palette.selectedColor : Palette.idleColor
                                    textColor: hover ? Palette.selectedTextColor : Palette.idleTextColor
                                    closeEnabled: false
                                }
                                model: closedPages
                            }
                        }
                    }
                }
            }
        }
    }
}

import QtQuick 2.7

import QtQuick.Controls 2.0
import QtQuick.Layouts 1.1

import "."

Item {
    id: self
    anchors.margins: 2
    anchors.fill: parent

    function select(page) {
        dosearch.navigation.open(page)
        parent.visible = false
    }

    Component {
        id: sectionDelegate
        Rectangle {
            height: 24
            anchors.left: parent.left
            anchors.right: parent.right
            color: Palette.backgroundColor
            Image {
                height: parent.height - 4
                width: parent.height - 4
                anchors.centerIn: parent
                source: history.model[history.currentIndex].context.icon
            }
        }
    }

    Component {
        id: pageDelegate
        Rectangle {
            height: 24
            anchors.left: parent.left
            anchors.right: parent.right
            color: ListView.isCurrentItem ? Palette.selectedColor : Palette.activeColor
            RowLayout {
                anchors.fill: parent
                spacing: 0
                Item { Layout.preferredWidth: 2 }
                Image {
                    Layout.preferredWidth: 20
                    Layout.preferredHeight: 20
                    Layout.alignment: Qt.AlignVCenter
                    anchors.verticalCenter: parent.verticalCenter
                    source: page.icon
                }
                Item { Layout.preferredWidth: 2 }
                Text {
                    Layout.maximumWidth: parent.width - 20 - 20 - 2 * 4
                    Layout.alignment: Qt.AlignVCenter
//                    Layout.preferredWidth: implicitWidth
                    text: page.title
                    color: ListView.isCurrentItem ? Palette.selectedTextColor : Palette.activeTextColor
                    elide: Text.ElideMiddle
                }
                Item { Layout.preferredWidth: 2; Layout.fillWidth: true }
                Text {
                    Layout.alignment: Qt.AlignVCenter
                    Layout.preferredWidth: 20

                    anchors.verticalCenter: parent.verticalCenter
                    color: ListView.isCurrentItem ? Palette.selectedTextColor : Palette.activeTextColor
                    text: owner.visits(page.id)
                }
                Item { Layout.preferredWidth: 2 }
            }
            MouseArea {
                anchors.fill: parent
                onClicked: self.select(page)
            }
        }
    }

    ListView {
        id: history
        anchors.fill: parent
        model: owner.last30
        section.property: "contextName"
        section.delegate: sectionDelegate
        section.labelPositioning: ViewSection.CurrentLabelAtStart
        section.criteria: ViewSection.FullString
        delegate: pageDelegate
        focus: true
    }

    onFocusChanged: {
        if (!focus)
            parent.visible = false
        else
            history.currentIndex = 0
    }

    Keys.onReturnPressed: {
        self.select(history.model[history.currentIndex].page)
    }
    Keys.onEscapePressed: {
        parent.visible = false
    }
    Keys.onDownPressed: {
        if (history.currentIndex < history.model.length - 1)
            history.currentIndex++
    }
    Keys.onUpPressed: {
        if (history.currentIndex > 0)
            history.currentIndex--
    }
}

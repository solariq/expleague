import QtQuick 2.7
import QtQuick.Layouts 1.1

import "."

ColumnLayout {
    id: self
    property var webView: !!requestHolder.children[0] ? requestHolder.children[0].webView : null
    property bool options: false
    anchors.fill: parent
    spacing: 0

    onVisibleChanged: {
        owner.request.ui.visible = visible
    }

    Rectangle {
        id: queries
        visible: options
        Layout.preferredHeight: 25
        Layout.fillWidth: true
        color: Palette.toolsBackground
        clip: true

        ListView {
            id: queriesList
            anchors.centerIn: parent
            height: queries.height - 4
            width: queries.width - 4
            model: owner.queries
            currentIndex: { for (var i in model) { if (model[i] === owner.request) return i } return -1 }
            orientation: ListView.Horizontal
            spacing: 2

            delegate: Rectangle {
                width: queryText.implicitWidth + 10
                height: queries.height - 4
                color: {
                    if (mouseArea.containsMouse)
                        return Palette.buttonHoverBackground
                    else if (index === queriesList.currentIndex)
                        return Palette.buttonPressedBackground
                    else
                        return Palette.toolsBackground
                }
                radius: Palette.radius

                Text {
                    id: queryText
                    anchors.centerIn: parent
                    text: query
                    color: "white"
                }
                MouseArea {
                    id: mouseArea
                    anchors.fill: parent
                    hoverEnabled: true
                    onClicked: owner.request = modelData
                }
            }
        }
    }

    Rectangle {
        Layout.fillHeight: true
        Layout.fillWidth: true
        id: requestHolder
        color: "green"
        children: [owner.request.ui]
    }
}

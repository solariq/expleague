import QtQuick 2.7
import QtQuick.Controls 2.0
import QtQml.Models 2.2

Item {
    id: self
    property color textColor: "black"
//    property color color: "white"
    property bool hover
    property real size: 10
    property real childSize: (self.width - 4)/11/2

    property bool complete: false

    anchors.centerIn: parent

    ListModel {
        id: empty
    }

    GridView {
        id: itemsFlow
        anchors.centerIn: parent
        width: cellWidth * 2
        height: cellHeight * 2

        clip: true

        contentWidth: width
        cellWidth: self.childSize * 11
        cellHeight: self.childSize * 11

        model: !!self.parent && childSize > 2 ? owner.items : empty
        delegate: Item {
            width: childSize * 11
            height: self.childSize * 11
            VaultTile {
                id: tile
                width: implicitWidth
                height: implicitHeight

                editMode: false
                item: modelData
                size: childSize
                notitles: true
            }
        }
    }

    Component.onCompleted: {
        console.log("owner: " + owner + " model power: " + owner.items.length)
        complete = true
    }
}

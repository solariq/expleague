import QtQuick 2.7
import QtQuick.Controls 2.0
import QtGraphicalEffects 1.0

import "."

Item {
    id: self
    implicitWidth: size * 11
    implicitHeight: size * 11
//    color: "green"

    property var item: QtObject {
        property Item ui: Item {}
        property string title: ""
        property string md: ""
    }

    property real size: 10
    property int visualIndex
    property bool editMode
    property var ownerModel
    property var vault
    property var tileArea
    property bool notitles: false

    property string area: ""
    property var dragSource
    property real timeSpentInside: 0

    anchors.verticalCenter: parent.verticalCenter
    anchors.horizontalCenter: parent.horizontalCenter

    Drag.dragType: self.editMode ? Drag.Internal : Drag.Automatic
    Drag.active: !!tileArea && tileArea.drag.active
//                Drag.hotSpot.x: 8
//                Drag.hotSpot.y: 8
    Drag.mimeData: { "text/plain": item.md, "vault": self }
    Drag.keys: ["text/plain", "vault"]


    Timer {
        id: insideTimer
        running: dropArea.containsDrag
        repeat: true
        interval: 50
        onTriggered: {
            self.timeSpentInside += 0.05
        }
    }

    Item {
        id: thumbnail
        visible: false
        anchors.horizontalCenter: parent.horizontalCenter
        y: size / 2
        width: size * 10 - (caption.visible ? caption.height : 0)
        height: width

        property real size: self.size

        children: [item.uiNoCache]
        onChildrenChanged: {
            for (var i in children) {
                var child = children[i]
                child.visible = true
                child.parent = thumbnail
                child.width = Qt.binding(function () {return thumbnail.width})
                child.height = Qt.binding(function () {return thumbnail.height})
                child.enabled = false
                child.size = Qt.binding(function () {return self.size})
            }
        }

        transitions: Transition {
            PropertyAnimation {properties: "width,height"; easing.type: Easing.InOutQuad }
        }
    }
    Text {
        id: caption
        visible: !notitles && size > 5
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.top: thumbnailHolder.bottom
        horizontalAlignment: Text.AlignHCenter
        verticalAlignment: Text.AlignBottom
        text: item.title
        height: implicitHeight + 2
        width: size * 10
        elide: Text.ElideRight
        color: "white"
        font.pixelSize: 12
    }

    Rectangle {
        id: thumbnailHolder
        visible: true

        anchors.centerIn: thumbnail

        width: size * 10 - (caption.visible ? caption.height : 0) + 2
        height: size * 10 - (caption.visible ? caption.height : 0) + 2
        radius: Math.min(self.size, 12) + 1
        border.width: 1
        border.color: "white"
        color: "#434553"
        OpacityMask {
            anchors.centerIn: parent
            width: thumbnail.width
            height: thumbnail.height
            source: thumbnail
            maskSource: Item {
                anchors.centerIn: parent
                width: thumbnail.width
                height: thumbnail.height
                Rectangle {
                    anchors.fill: parent
                    radius: Math.min(thumbnail.size, 12)
                }
            }
        }
    }

    Glow {
        id: thumbnailGlow
        visible: false
        anchors.fill: thumbnailHolder
        source: thumbnailHolder
        radius: 0
        samples: 17
        color: Palette.borderColor("selected")
        spread: 0.4
        NumberAnimation on radius {
            easing.type: Easing.InOutQuad; duration: 750
        }
    }

//    Text {
//        anchors.centerIn: parent
//        verticalAlignment: Text.AlignVCenter
//        text: Math.round(self.size*10)/10
//        font.pixelSize: self.size * 5
//        color: "white"
//    }

    onTimeSpentInsideChanged: {
        if (self.timeSpentInside < 0.2)
            return
//        console.log("Side: " + self.area + " spent: " + timeSpentInside)

        var effectiveIndex = self.visualIndex == 0 || self.visualIndex + 1 >= ownerModel.items.length - 1 ? self.visualIndex : self.visualIndex - 1
        var moveFrom = self.dragSource.visualIndex
        var moveTo = -1
        if (self.visualIndex > moveFrom) {
            if (self.area == "left")
                moveTo = self.visualIndex - 1
            else if (self.area == "right")
                moveTo = self.visualIndex
        }
        else {
            if (self.area == "left")
                moveTo = self.visualIndex
            else if (self.area == "right")
                moveTo = self.visualIndex + 1
        }

        if (moveTo >= 0) {
            ownerModel.items.move(moveFrom, moveTo)
            vault.moveTo = moveTo
            timeSpentInside = 0
        }
    }

    DropArea {
        id: dropArea
        visible: self.editMode
        anchors.fill: parent

        function updateArea(drag) {
            var area = ""
            if (drag.x < width / 4.0)
                area = "left"
            else if (drag.x < width * 3.0/4.0)
                area = "center"
            else
                area = "right"
            if (area != self.area) {
                self.timeSpentInside = 0
                self.area = area
//                console.log("Update position: (" + drag.x + ", " + drag.y + ") -> " + self.area)
            }
        }

        onPositionChanged: {
            if (drag.source !== self)
                updateArea(drag)
        }

        onEntered: {
            if (drag.source === self || drag.keys.indexOf("vault") < 0)
                return
            self.dragSource = drag.source
            updateArea(drag)
        }

        onExited: {
            self.area = ""
            self.timeSpentInside = 0
            self.dragSource = null
        }

        onDropped: {
            if (self.state == "mkgroup")
                vault.owner.group(item, drag.source.item)

            self.area = ""
            self.timeSpentInside = 0
            self.dragSource = null
        }
    }

    Button {
        visible: self.editMode
        id: close
        x: thumbnailHolder.x + thumbnailHolder.width - 11
        y: thumbnailHolder.y - 8
        height: 20
        width: 20
        hoverEnabled: true

        background: Rectangle {
            color: self.pressed ? "#4E92E0" : (close.hovered ? Palette.buttonPressedBackground: Palette.toolsBackground)
            radius: width/2
            border.width: 1
            border.color: "white"
            layer.mipmap: true
        }

        indicator: Image {
            anchors.centerIn: parent
            source: "qrc:/cross_h.png"
            width: 10
            height: 10
            mipmap: true
        }

        onClicked: {
            context.vault.remove(modelData)
        }
    }

    states: [
        State {
            name: "drag"
            when: tileArea.drag.active
            ParentChange { target: self; parent: dosearch.main.screenRef }
            AnchorChanges { target: self; anchors.verticalCenter: undefined; anchors.horizontalCenter: undefined }
        },
        State {
            name: "selected"
            when: tileArea.containsMouse
            PropertyChanges { target: thumbnailGlow; radius: 10; visible: true }
        },
        State {
            name: "mkgroup"
            when: self.timeSpentInside > 0.4 && self.area == "center" && !!self.dragSource
            PropertyChanges { target: thumbnail; size: !!modelData["items"] ? self.size : self.size - 2}
            PropertyChanges { target: self.dragSource; size: self.size - 2 }
        }
    ]
}

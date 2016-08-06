import QtQuick 2.7
import QtQuick.Controls 2.0

import "."

Button {
    id: self
    property url icon
    property bool toggle: false
    property int size: 29
    property var action
    hoverEnabled: true
    padding: 0
    implicitHeight: size
    implicitWidth: size
    focusPolicy: Qt.NoFocus

//    ToolTip.delay: Qt.styleHints.mousePressAndHoldInterval

    signal triggered()

    onActionChanged: {
        if (!action)
            return
        icon = action.iconSource
//        self.ToolTip.text = action.shortcut + " " + action.tooltip
//        self.ToolTip.visible = Qt.binding(function() {return self.hovered})
    }

    onClicked: {
       if (action)
           action.trigger(self)
       else
           triggered()
    }

    background: Rectangle {
        radius: 4
        color: self.pressed || self.toggle ? Palette.selectedColor : Palette.activeColor
    }

    indicator: Image {
        width: (size - 4)
        height: (size - 4)
        anchors.centerIn: parent
        source: icon
        mipmap: true
    }
}

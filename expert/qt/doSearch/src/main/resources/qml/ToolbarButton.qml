import QtQuick 2.7
import QtQuick.Controls 2.0

import "."

Button {
    id: self
    property string icon
    property bool toggle: false
    property real size: 27
    property real imgPadding: 4
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
        enabled = Qt.binding(function() {return !!action ? action.enabled : false})
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
        radius: Palette.radius
        color: self.pressed || self.toggle ? Palette.buttonPressedBackground : (hovered ? Palette.buttonHoverBackground : "transparent")
    }

    indicator: Image {
        width: (size - self.imgPadding * 2)
        height: (size - self.imgPadding * 2)
        anchors.centerIn: parent
        source: {
            if (!self.enabled)
                return icon.substring(0, icon.length - 4) + "_d.png"
            else if (self.toggle || self.pressed)
                return icon.substring(0, icon.length - 4) + "_h.png"
            return icon
        }
        mipmap: true
    }
}

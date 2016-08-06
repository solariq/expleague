import QtQuick 2.7

import QtQuick.Controls 2.0

import "."

Button {
    id: self
    property bool active: false
    antialiasing: true
    implicitHeight: label.implicitWidth + 50
    clip: true
    padding: 0
    hoverEnabled: true

    background: Rectangle {
        color: {
            if (active)
                return Palette.selectedColor
            return hovered ? Palette.activeColor : Palette.idleColor
        }
    }
    contentItem: Item {
        Text {
            id: label
            x: height
            y: (parent.height - width) / 2

            width: implicitWidth
            height: implicitHeight
            transform: Rotation {angle: 90;}
            renderType: Text.NativeRendering
            horizontalAlignment: Text.AlignHCenter
            verticalAlignment: Text.AlignVCenter
            text: self.text
            color: {
                if (self.active)
                    return Palette.selectedTextColor
                return self.hovered ? Palette.activeTextColor : Palette.idleTextColor
            }
        }
    }
}

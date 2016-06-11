import QtQuick 2.5
import QtQuick.Controls 1.4
import QtQuick.Controls.Styles 1.3

import "."

Button {
    property url icon

    Component.onCompleted: {
        if (action) {
//            console.log(action.iconSource);
            icon = action.iconSource
            action.iconSource = undefined
        }
    }

    style: ButtonStyle {
        background: Rectangle {
            implicitHeight: 29
            implicitWidth: 29
            radius: 4
            color: control.pressed ? Palette.backgroundColor : Palette.navigationColor
            Image {
                height: 25
                width: 25
                anchors.centerIn: parent
                source: icon
                mipmap: true
            }
        }
        label: Item {}
    }
}

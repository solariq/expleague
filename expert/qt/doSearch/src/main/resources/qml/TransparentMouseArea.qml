import QtQuick 2.5

MouseArea {
    hoverEnabled: true
    propagateComposedEvents: true

    onPressed: mouse.accepted = false
    onReleased: mouse.accepted = false
    onClicked: mouse.accepted = false
    onMouseXChanged: mouse.accepted = false
    onMouseYChanged: mouse.accepted = false
    onDoubleClicked: mouse.accepted = false;
    onPressAndHold: mouse.accepted = false;
    onPositionChanged: mouse.accepted = false
}

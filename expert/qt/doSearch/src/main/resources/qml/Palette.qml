pragma Singleton
import QtQuick 2.0

QtObject {
    property color backgroundColor: "#2e3135"
    property color navigationColor: backgroundColor

    property color idleColor: "#1F2123"
    property color idleTextColor: "#697488"
    property color activeColor: "#1F2123"
    property color activeTextColor: "white"
    property color selectedColor: "#C6CBD2"
    property color selectedTextColor: "black"

    property color textColor: "#505050"
    property color chatBackgroundColor: "#353637"
    property color focusBorderColor: "#bdbebf"
    property color idleControlBackground: "white"
    property color activeControlBackground: Qt.lighter(backgroundColor, 2.)

    property real radius: 5;
}

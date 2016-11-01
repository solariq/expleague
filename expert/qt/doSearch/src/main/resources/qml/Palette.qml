pragma Singleton
import QtQuick 2.0

QtObject {
//    property color navigationColor: "#E0DEE0"
    property color navigationColor: "#D2D0D2"

    property Gradient navigationGradient: Gradient {
        GradientStop {position: 0.0; color: "#E8E6E8" }
        GradientStop {position: 1.0; color: "#D2D0D2" }
    }

    property color idleColor: "#CACACA"
    property color idleTextColor: "#575757"
    property color activeColor: "#F5F5F5"
    property color activeTextColor: "black"
    property color selectedColor: "white"
    property color selectedTextColor: "black"

//    property color textColor: "#505050"
    property color chatBackgroundColor: "#353637"
    property color focusBorderColor: "#bdbebf"

    property color buttonHoverBackground: "#C1BBC3"
    property color buttonPressedBackground: "#726F73"

    property color toolsBackground: "#262730"
    property color toolsActiveColor: "#4E92E0"
    property color documentColor: "#FFFBF0"

    property real radius: 4;

    function backgroundColor(state) {
        if (state == "idle") {
            return idleColor
        }
        else if (state == "active") {
            return activeColor
        }
        else if (state == "selected"){
            return selectedColor
        }
        return "red"
    }

    function borderColor(state) {
        if (state == "idle" || state == "active") {
            return "#B4B4B4"
        }
        else if (state == "selected"){
            return "#5DC9F5"
        }
        return "green"
    }

    function textColor(state) {
        if (state == "idle") {
            return idleTextColor
        }
        else if (state == "active") {
            return activeTextColor
        }
        else if (state == "selected"){
            return selectedTextColor
        }
        return "blue"
    }
}

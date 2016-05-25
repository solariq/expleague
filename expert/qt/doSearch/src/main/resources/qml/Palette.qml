pragma Singleton
import QtQuick 2.0

QtObject {
    property color backgroundColor: "#e8e8e8"
    property color navigationColor: Qt.lighter(backgroundColor, 1.05)
    property color activeColor: navigationColor
    property color idleColor: Qt.darker(backgroundColor, 1.1)
    property color textColor: "#505050"
    property color chatBackgroundColor: "#353637"
    property color focusBorderColor: "#bdbebf"
}

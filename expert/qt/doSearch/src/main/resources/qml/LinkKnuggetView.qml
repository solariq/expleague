import QtQuick 2.7
import QtQuick.Controls 2.0

Item {
    property color textColor: "black"
    property color color: "white"
    property bool hover
    property real size

    anchors.centerIn: parent

    ToolTip.text: owner.url
    ToolTip.delay: 2000
    ToolTip.visible: hover

    Image {
        mipmap: true
        anchors.fill: parent
        source: owner.screenshot.length > 0 ? "file:" + owner.screenshot : ""
        fillMode: Image.PreserveAspectCrop
    }

    Connections {
        target: dosearch
        onMainChanged: {
            if (!owner.hasScreenshot()) {
                dosearch.main.saveScreenshot(owner.url, "400x400", owner)
            }
        }
    }

    Component.onCompleted: {
        if (dosearch.main && !owner.hasScreenshot())
            dosearch.main.saveScreenshot(owner.url, "400x400", owner)
    }
}

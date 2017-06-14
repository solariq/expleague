import QtQuick 2.7
//import QtWebEngine 1.3
import QtGraphicalEffects 1.0

import "."

GridView {
    id: self
    property var context

    cellWidth: 280
    cellHeight: 340

    clip: true

    function rebuildDocuments() {
        var documents = []
        for (var i in context.documents) {
            documents.push(context.documents[i])
        }
        if (!context.task)
            documents.push("append")
        return documents
    }

    Connections {
        target: context
        onDocumentsChanged: self.model = rebuildDocuments()
    }

    model: rebuildDocuments()

    delegate: Item {
        width: 280
        height: 340
        Item {
            id: editorPreview
            property string htmlContent: "<!DOCTYPE html><html><head>
                                         <script src=\"qrc:///md-scripts.js\"></script>
                                         <link rel=\"stylesheet\" href=\"qrc:///markdown-tile.css\"></head>
                                         <body>" + (modelData != "append" ? modelData.html : "") + "</body></html>"


            visible: modelData != "append"

            Drag.dragType: Drag.Automatic //TODO uncommet
            Drag.active: tileArea.drag.active
            Drag.mimeData: { "text/html": htmlContent}
            Drag.keys: ["text/html"]


            anchors.centerIn: parent
            width: parent.width - 40
            height: parent.height - 40

            onHtmlContentChanged: {
                var focused = dosearch.main ? dosearch.main.activeFocusItem : null
                web.webView.loadHtml(htmlContent)
                if (focused)
                    focused.forceActiveFocus()
            }

            Rectangle {
                id: tile
                anchors.fill: parent
                color: "#FFFBF0"
                CefView {
                    z: parent.z + 1
                    id: web
                    anchors.centerIn: parent
                    webView.running: visible
                    width: parent.width - 20
                    height: parent.height - 20
                    Component.onCompleted: web.webView.loadHtml(editorPreview.htmlContent)
                }
            }

            Glow {
                visible: context.document === modelData
                anchors.fill: tile
                source: tile
                radius: 8
                samples: 17
                color: Palette.borderColor("selected")
            }

            DropShadow {
                anchors.fill: parent
                cached: true
                radius: 10.0
                samples: 32
                color: "#aa000000"
                source: tile
            }
            MouseArea {
                id: tileArea
                anchors.fill: parent
                onClicked: dosearch.navigation.open(modelData)
                drag.target: parent
                hoverEnabled: true
            }
            Item {
                visible: tileArea.containsMouse
                height: 16
                width: 16
                x: parent.width - 16
                y: 0
                Image {
                    anchors.fill: parent
                    source: "qrc:/cross.png"
                }
                MouseArea {
                    anchors.fill: parent
                    onClicked: context.removeDocument(modelData)
                }
            }
        }
        Rectangle {
            visible: modelData == "append"
            anchors.centerIn: parent
            width: parent.width - 40
            height: parent.height - 40
            color: "#EEEEEE"
            Text {
                anchors.centerIn: parent
                verticalAlignment: Text.AlignVCenter
                text: "+"
                font.pixelSize: 80
                color: "#979797"
            }
            MouseArea {
                anchors.fill: parent
                onClicked: {
                    dosearch.navigation.open(context.createDocument())
                }
            }
        }
    }
}

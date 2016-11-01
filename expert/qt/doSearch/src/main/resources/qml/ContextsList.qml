import QtQuick 2.7
import QtQuick.Controls 2.0
import QtQuick.Layouts 1.1

import "."

Rectangle {
    id: self

    color: Palette.toolsBackground

    ColumnLayout {
        anchors.fill: parent
        RowLayout {
            Layout.fillWidth: true
            Layout.preferredHeight: 24
            Layout.maximumHeight: 24
            spacing: 0
            Item { Layout.preferredWidth: 10 }
            Label {
                Layout.fillWidth: true
//                Layout.fillHeight: true
                Layout.alignment: Qt.AlignVCenter

//                verticalAlignment: Text.AlignVCenter
                text: qsTr("Контексты")
                color: "white"
            }
            Item { Layout.preferredWidth: 5 }
            Button {
                Layout.preferredWidth: 24
                Layout.preferredHeight: 24
                indicator: Label{
                    anchors.centerIn: parent
                    color: "white"
                    font.pixelSize: 20
                    text: "+"
                }
                background: Item {}
                onPressed: {
                    var context = dosearch.createContext(qsTr("Новый контекст"))
                    dosearch.navigation.open(context)
                    dosearch.navigation.select(0, context)

                }
            }
        }
        ListView {
            Layout.fillHeight: true
            Layout.fillWidth: true
            id: contextsView
            model: dosearch.contexts

            spacing: 0

            currentIndex: {
                for (var i in model) {
                    if (dosearch.navigation.context === model[i])
                        return i
                }
                return -1
            }

            delegate: Item {
                width: contextsView.width
                height: 32

                MouseArea {
                    anchors.fill: parent
                    onClicked: dosearch.navigation.open(modelData)
                    RowLayout {
                        id: contextRow

                        anchors.fill: parent
                        spacing: 0
                        Item { Layout.preferredWidth: 10 }
                        Image {
                            Layout.preferredWidth: 24
                            Layout.preferredHeight: 24
                            Layout.alignment: Qt.AlignVCenter
                            source: modelData.icon
                        }
                        Item { Layout.preferredWidth: 5 }
                        Text {
                            id: contextName

                            Layout.fillWidth: true

                            horizontalAlignment: Text.AlignLeft
                            verticalAlignment: Text.AlignVCenter

                            color: "white"
                            text: modelData.title
                            elide: Text.ElideRight
                        }

                        Item { Layout.preferredWidth: 5 }
                    }
                    Rectangle {
                        anchors.fill: parent
                        color: "transparent"
                        border.color: Palette.borderColor("selected")
                        border.width: dropArea.containsDrag ? 3 : 0
                    }
                }
                DropArea {
                    id: dropArea
                    anchors.fill: parent

                    onDropped: {
                        if (dosearch.main.dragType !== "page")
                            return
                        dosearch.navigation.moveTo(dosearch.main.drag, modelData)
                        dosearch.main.drop = null
                        dosearch.main.dropType = ""
                    }
                }
            }
            highlight: Rectangle {
                color: Palette.toolsActiveColor
                width: self.width
                height: 32
                y: !!contextsView.currentItem ? contextsView.currentItem.y : 0
                Behavior on y {
                    SpringAnimation {
                        spring: 3
                        damping: 0.2
                    }
                }
            }
        }
    }
}

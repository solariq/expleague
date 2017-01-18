import QtQuick 2.7
import QtQuick.Window 2.0
import QtQuick.Controls 2.0
import QtQuick.Controls 1.4 as Legacy
import QtQuick.Layouts 1.1
import QtQuick.Dialogs 1.2

import QtWebEngine 1.3

import ExpLeague 1.0

import "."

Rectangle {
    id: self
    color: "white"
    anchors.fill: parent
    property alias downloads: downloadsPage
    property var ownerCtxt: owner

    onFocusChanged: {
        var text = owner.title
        if (focus && (text == "" || text == qsTr("Новый контекст"))) {
            contextName.forceActiveFocus()
        }
    }

    RowLayout {
        anchors.fill: parent
        spacing: 0
        ContextsList {
            Layout.preferredWidth: 260
            Layout.fillHeight: true
        }

        ListView {
            id: aspectsView
            Layout.preferredWidth: 200
            Layout.fillHeight: true
            property real rowHeight: 24

            currentIndex: {
                for (var i = 0; i < model.count; i++) {
                    if (content.state == model.get(i).item)
                        return i
                }
                return -1
            }
            model: ListModel {
                ListElement {
                    name: qsTr("Хранилище")
                    item: "vault"
                }
                ListElement {
                    name: qsTr("Документы")
                    item: "documents"
                }
                ListElement {
                    name: qsTr("Загрузки")
                    item: "downloads"
                }
            }
            delegate: MouseArea {
                property string targetState: item
                width: aspectsView.width
                height: aspectsView.rowHeight
                onClicked: content.state = targetState

                RowLayout {
                    anchors.fill: parent
                    Item { Layout.preferredWidth: 10 }
                    Text {
                        Layout.fillWidth: true
                        Layout.alignment: Qt.AlignVCenter
                        text: name
                    }
                }
            }
            header: Item { height: 40 }
            footer: Item {
                height: Math.max(0, aspectsView.height - aspectsView.model.length * aspectsView.rowHeight)
            }
            highlight: Rectangle {
                width: aspectsView.width
                height: aspectsView.rowHeight
                color: "#E1EDFE"
                y: aspectsView.currentItem.y
                Behavior on y {
                    SpringAnimation {
                        spring: 3
                        damping: 0.2
                    }
                }
            }
        }
        Rectangle {
            Layout.fillHeight: true
            Layout.preferredWidth: 2
            color: "lightgray"
        }

        Rectangle {
            Layout.fillWidth: true
            Layout.fillHeight: true
            ColumnLayout {
                id: content
                anchors.fill: parent
                spacing: 0
                RowLayout {
                    Layout.preferredHeight: 40
                    Layout.fillWidth: true
                    anchors.margins: 5
                    spacing: 0
                    Item { Layout.preferredWidth: 20 }
                    Image {
                        Layout.preferredHeight: 33
                        Layout.preferredWidth: 33
                        source: owner.icon
                    }
                    Item { Layout.preferredWidth: 20 }
                    TextEdit {
                        id: contextName
                        Layout.fillWidth: true
                        Layout.preferredHeight: implicitHeight
                        Layout.alignment: Qt.AlignVCenter
                        text: owner.title
                        font.pixelSize: 24
                        clip: true
//                        color: "black"
                        Keys.onReturnPressed: {
                            owner.setName(contextName.text)
                            contextName.focus = false
                        }
                        onTextChanged: {
                            if (text == "" || text == qsTr("Новый контекст")) {
                                contextName.selectAll()
                            }
                        }
                    }
                    ToolbarButton {
                        id: ungroupButton
                        icon: "qrc:/cross.png"
                        onTriggered: dosearch.remove(owner);
                    }
                    Item { Layout.preferredWidth: 10 }
                }

                Item {
                    Layout.fillWidth: true
                    Layout.fillHeight: true

                    DownloadsPage {
                        id: downloadsPage
                        anchors.fill: parent

                        visible: false
                    }

                    EditorsList {
                        id: editorsPage
                        anchors.fill: parent

                        visible: false
                        context: owner
                    }

                    Vault {
                        id: vaultPage
                        anchors.fill: parent
                        editMode: true
                        size: 15

                        visible: false
                        context: ownerCtxt
                    }
                }

                state: aspectsView.currentItem ? aspectsView.currentItem.targetState : (owner.vault.items.length > 0 ? "vault" : "documents")
                states: [
                    State { name: "downloads"; PropertyChanges { target: downloadsPage; visible: true } },
                    State { name: "documents"; PropertyChanges { target: editorsPage; visible: true } },
                    State { name: "vault"; PropertyChanges { target: vaultPage; visible: true } }
                ]
            }
        }
    }
}

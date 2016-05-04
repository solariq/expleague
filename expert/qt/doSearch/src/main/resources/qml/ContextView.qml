import QtQuick 2.5
import QtQuick.Window 2.0
import QtQuick.Controls 1.4
import QtQuick.Controls.Styles 1.4
import QtQuick.Layouts 1.1

import QtWebEngine 1.2

import ExpLeague 1.0

Item {
    property Window window
    property Item statusBar

    property color backgroundColor
    property alias activeColor: tabs.activeColor

    property Context context

    Rectangle {
        antialiasing: true
        anchors.fill: parent
        color: backgroundColor
        RowLayout {
            spacing: 0
            anchors.fill: parent
            SplitView {
                id: mainSplit
                Layout.fillHeight: true
                Layout.fillWidth: true

                anchors.fill: parent
                orientation: Qt.Horizontal

                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 0
                    TabButtons {
                        id: tabs
                        Layout.fillWidth: true
                        Layout.preferredHeight: 23

                        model: context && context.folder ? context.folder.screens : []
                        position: true
                        activeColor: activeColor
                    }
                    Rectangle {
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        color: backgroundColor
                        id: central
                    }
                }
                SplitView {
                    id: rightSide
                    Layout.minimumWidth: 320
                    Layout.fillHeight: true
                    visible: context && !!context.task
                    orientation: Qt.Vertical

                    ColumnLayout {
                        Layout.fillWidth: true
                        Layout.minimumHeight: 20
                        Layout.preferredHeight: taskView.height + 20

                        Rectangle {
                            color: backgroundColor
                            Layout.fillWidth: true
                            Layout.preferredHeight: 22
                            RowLayout {
                                spacing: 3
                                anchors.fill: parent
                                Button {
                                    style: ButtonStyle {
                                        background: Component {
                                            Item {
                                                implicitWidth: 16
                                                implicitHeight: 16
                                                Image {
                                                    anchors.fill: parent
                                                    fillMode: Image.PreserveAspectFit
                                                    mipmap: true
                                                    source: "qrc:/expand.png"
                                                    rotation: taskView.visible ? 0 : -90
                                                }
                                            }
                                        }
                                    }
                                    property real storedHeight: 0
                                    onClicked: {
                                        if (taskView.visible) {
                                            storedHeight = taskView.parent.height
                                            taskView.visible = false
                                        }
                                        else {
                                            taskView.visible = true
                                            taskView.parent.height = storedHeight
                                        }
                                    }
                                }
                                Label {
                                    text: qsTr("Задание")
                                }
                                Item {Layout.fillWidth: true}
                            }
                        }

                        OfferView {
                            Layout.fillHeight: true
                            Layout.fillWidth: true
                            id: taskView
                            visible: true
                            offer: context.task.offer
                            Rectangle {
                                anchors.fill: parent
                                color: activeColor
                                z: parent.z - 1
                            }
                        }
                    }
                    Rectangle {
                        Layout.fillHeight: true
                        Layout.fillWidth: true
                        color: backgroundColor
                        FocusScope {
                            anchors.fill: parent
                            WebEngineView {
                                id: preview

                                visible: true
                                focus: false

                                property string html: ""

                                onHtmlChanged: {
                                    var focused = window.activeFocusItem
                                    loadHtml(html)
                                    focused.forceActiveFocus()
                                }

                                anchors.fill: parent
                            }
                            Rectangle {
                                id: dialog
                                visible: false

                                color: "green"
                                anchors.fill: parent
                            }
                        }
                    }
                }
            }

            Rectangle {
                id: rightSidebar
                Layout.fillHeight: true
                Layout.minimumWidth: 23
                Layout.maximumWidth: 23
                visible: context && !!context.task

                property var active: previewButton

                color: backgroundColor

                RowLayout {
                    rotation: 90

                    anchors.centerIn: parent
                    width: parent.height
                    height: parent.width

                    spacing: 0
                    Item {Layout.preferredWidth: 20}
                    Button {
                        id: dialogButton
                        Layout.fillHeight: true
                        style: ButtonStyle {
                            background: SidebarButton {
                                anchors.centerIn: parent
                                active: rightSidebar.active === dialogButton
                                hovered: dialogButton.hovered
                                text: qsTr("Диалог")
                                onActiveChanged: {
                                    dialog.visible = active
                                }
                            }
                        }
                        onClicked: {
                            rightSidebar.active = dialogButton
                        }
                    }
                    Button {
                        id: previewButton
                        Layout.fillHeight: true
                        style: ButtonStyle {
                            background: SidebarButton {
                                anchors.centerIn: parent
                                hovered: previewButton.hovered
                                active: rightSidebar.active === previewButton
                                text: qsTr("Ответ")
                                property real storedWidth
                                onActiveChanged: {
                                    if (active) {
                                        storedWidth = rightSide.width
                                        rightSide.width = 320
                                    }
                                    else {
                                        rightSide.width = storedWidth
                                    }

                                    preview.visible = active
                                }
                            }
                        }
                        onClicked: {
                            rightSidebar.active = previewButton
                        }
                    }
                    Item {Layout.fillWidth: true}
                }
            }
        }
    }
    function rebind(screen) {
        central.children = []
        if (screen)
            screen.bind(central)
        if (screen && screen.toString().indexOf("MarkdownEditorScreen") >= 0) {
            preview.html = Qt.binding(function() {return screen.html})
        }
    }

    onContextChanged: {
        if (context) {
            if (context.folder) {
                rebind(context.folder.screen)
                return
            }
        }
        rebind(null)
    }

    Connections {
        target: context

        onFolderChanged: {
            if (folder)
                rebind(folder.screen)
            else
                rebind(null)
        }
    }

    Connections {
        target: context.folder

        onScreenChanged: {
            rebind(screen)
        }
    }
}

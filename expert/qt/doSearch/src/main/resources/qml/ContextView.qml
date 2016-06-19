import QtQuick 2.5
import QtQuick.Window 2.0
import QtQuick.Controls 1.4
import QtQuick.Controls.Styles 1.4
import QtQuick.Layouts 1.1
import QtQuick.Dialogs 1.2

import QtWebEngine 1.2

import ExpLeague 1.0

import "."

Item {
    property Window window
    property TagsDialog tagsDialog
    property Item statusBar

    property color backgroundColor

    property Context context

    MessageDialog {
        id: taskCancelledDialog
        title: qsTr("Задание закрыто")
        text: qsTr("Задание отменено сервером, обычно такое случается, если клиент отменил задание.")
        onAccepted: {
            visible = false
        }

        visible: false
    }

    Connections {
        target: context.task

        onCancelled: {
            window.showDialog(taskCancelledDialog)
        }
    }

    Rectangle {
        antialiasing: true
        anchors.fill: parent
        color: Palette.backgroundColor
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
                    }
                    Rectangle {
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        color: Palette.backgroundColor
                        id: central
                    }
                }
                Item {
                    id: rightSide
                    Layout.minimumWidth: 341
                    Layout.fillHeight: true
                    visible: context && !!context.task && rightSidebar.active
                    anchors.rightMargin: 21

                    SplitView {
                        orientation: Qt.Vertical
                        anchors.fill: parent
                        anchors.rightMargin: 21

                        ColumnLayout {
                            Layout.fillWidth: true
                            Layout.minimumHeight: 23
                            Layout.preferredHeight: taskView.height + 23
                            spacing: 0

                            Rectangle {
                                color: Palette.backgroundColor
                                Layout.fillWidth: true
                                Layout.preferredHeight: 23
                                RowLayout {
                                    spacing: 3
                                    anchors.fill: parent
                                    Item {Layout.preferredWidth: 3}
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
                                offer: context.task ? context.task.offer : null
                                task: context.task
//                                tagsDialog: tagsDialog

                                Rectangle {
                                    anchors.fill: parent
                                    color: Palette.activeColor
                                    z: parent.z - 1
                                }
                            }
                        }
                        Rectangle {
                            Layout.fillHeight: true
                            Layout.fillWidth: true
                            color: Palette.backgroundColor
                            WebEngineView {
                                id: preview

                                visible: true
                                focus: false
                                url: "about:blank"

                                property string html: ""

                                onHtmlChanged: {
                                    var focused = window.activeFocusItem
                                    loadHtml(html)
                                    if (focused)
                                        focused.forceActiveFocus()
                                }

                                onUrlChanged: {
                                    var url = "" + preview.url
//                                    console.log("New url: " + url)
                                    if (url.length > 0 && url != "about:blank" && url.indexOf("data:") !== 0) {
                                        preview.goBack()
                                        context.handleOmniboxInput(url, true)
                                    }
                                }

                                anchors.fill: parent
                            }
                            Chat {
                                id: dialog
                                visible: false
                                anchors.fill: parent

                                task: context.task
                            }
                        }
                    }
                }
            }
            Rectangle{visible: context.task; Layout.preferredWidth: 1; Layout.fillHeight: true; color: "darkgray"}
            Rectangle{
                id: rightSidebar
                Layout.fillHeight: true
                Layout.preferredWidth: 20
                visible: context && context.task

                property var active: dialogButton

                color: Palette.backgroundColor

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
                            rightSidebar.active = rightSidebar.active !== dialogButton ? dialogButton : null
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
                                        rightSide.width = 341
                                    }
                                    else {
                                        rightSide.width = storedWidth
                                    }

                                    preview.visible = active
                                }
                            }
                        }
                        onClicked: {
                            rightSidebar.active = rightSidebar.active !== previewButton ? previewButton : null
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
        preview.html = ""
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

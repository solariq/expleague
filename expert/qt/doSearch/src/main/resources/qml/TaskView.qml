import QtQuick 2.7
import QtQuick.Window 2.0
import QtQuick.Controls 2.0
import QtQuick.Controls 1.4 as Legacy
import QtQuick.Layouts 1.3
import QtQuick.Dialogs 1.2

import QtWebEngine 1.3

import ExpLeague 1.0

import "."

Item {
    id: self
    property Window window
    property TagsDialog tagsDialog
    property Item statusBar
    property Context context
    property var task: context ? context.task : null
    property real maxWidth: rightSide.visible ? -1 : rightSidebar.width
    property real minWidth: (rightSide.visible ? 320 : 0) + rightSidebar.width

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
        target: task

        onCancelled: {
            window.showDialog(taskCancelledDialog)
        }
    }

    RowLayout {
        anchors.fill:parent
        spacing: 0
        Legacy.SplitView {
            id: rightSide

            Layout.minimumWidth: 320
            Layout.fillWidth: true
            Layout.fillHeight: true
            visible: rightSidebar.active != null

            orientation: Qt.Vertical

            ColumnLayout {
                Layout.fillWidth: true
                Layout.minimumHeight: 23
                Layout.maximumHeight: offerViewHolder.visible ? offerView.implicitHeight + 23 : 23
                Layout.preferredHeight: offerViewHolder.visible ? offerView.implicitHeight + 23 : 23
//                height: Math.max(400, offerView.implicitHeight + 23)
                spacing: 0

                onHeightChanged: {
                    console.log("Offer height: " + height)
                }

                Rectangle {
                    Layout.fillWidth: true
                    Layout.minimumHeight: 23
                    Layout.maximumHeight: 23
                    Layout.preferredHeight: 23

                    color: Palette.navigationColor
                    RowLayout {
                        spacing: 3
                        anchors.fill: parent
                        Item {Layout.preferredWidth: 3}
                        Button {
                            Layout.fillHeight: true
                            Layout.preferredWidth: height
                            indicator: Image {
                                anchors.centerIn: parent
                                fillMode: Image.PreserveAspectFit
                                mipmap: true
                                source: "qrc:/expand.png"
                                width: 16
                                height: 16
                                rotation: offerViewHolder.visible ? 0 : -90
                            }
                            background: Item{}
                            property real storedHeight: 450
                            enabled: self.task !== null
                            onEnabledChanged: {
                                if (enabled)
                                    offerViewHolder.parent.height = 450
                                else
                                    offerViewHolder.parent.height = 0
                            }

                            onClicked: {
                                if (offerViewHolder.visible) {
                                    storedHeight = offerView.parent.height
                                    offerViewHolder.visible = false
                                }
                                else {
                                    offerViewHolder.visible = true
                                    offerViewHolder.parent.height = storedHeight
                                }
                            }
                        }
                        Label {
                            Layout.fillHeight: true
                            Layout.preferredWidth: implicitWidth
                            text: qsTr("Задание")
                            color: Palette.activeTextColor
                        }
                        Item {Layout.fillWidth: true}
                    }
                }
                Rectangle {
                    id: offerViewHolder
                    Layout.fillWidth: true
                    Layout.fillHeight: offerView.implicitHeight
                    color: Palette.selectedColor
                    visible: self.task !== null
                    Flickable {
                        anchors.fill: parent
                        flickableDirection: Flickable.VerticalFlick
                        contentHeight: offerView.implicitHeight
                        OfferView {
                            id: offerView

                            anchors.fill: parent
                            offer: self.task ? self.task.offer : null
                            task: self.task
                            textColor: Palette.selectedTextColor
                        }
                    }
                }
            }
            Item {
                Layout.fillHeight: true
                Layout.fillWidth: true
                WebEngineView {
                    id: preview

                    visible: rightSidebar.active == previewButton
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
                            dosearch.navigation.handleOmnibox(url, 0)
                        }
                    }

                    anchors.fill: parent
                }
                LeagueChat {
                    id: dialog
                    visible: rightSidebar.active == dialogButton
                    anchors.fill: parent

                    task: self.task
                }
            }
        }
        ColumnLayout {
            id: rightSidebar
            Layout.fillHeight: true
            Layout.preferredWidth: 20
            Layout.minimumWidth: 20
            Layout.maximumWidth: 20

            property var active: dialogButton
            property real sideWidth: 320 + rightSidebar.width
            property real dialogWidth: 320 + rightSidebar.width

            spacing: 0
            property real storedWidth

            function foldSide() {
                if (active == dialogButton)
                    dialogWidth = self.width - rightSidebar.width
                active = null
                self.width = rightSidebar.width
            }

            Item {Layout.preferredHeight: 20}
            SidebarButton {
                id: dialogButton
                Layout.fillWidth: true
                Layout.preferredHeight: implicitHeight
                active: rightSidebar.active === dialogButton
                text: qsTr("Диалог")

                onClicked: {
                    if (rightSidebar.active !== dialogButton) {
                        rightSidebar.active = dialogButton
                        self.width = rightSidebar.dialogWidth + rightSidebar.width
                    }
                    else rightSidebar.foldSide()
                }
            }
            SidebarButton {
                id: previewButton
                Layout.fillWidth: true
                Layout.preferredHeight: implicitHeight
                active: rightSidebar.active === previewButton
                text: qsTr("Ответ")
                onClicked: {
                    if (rightSidebar.active !== previewButton) {
                        rightSidebar.active = previewButton
                        if (self.width > rightSidebar.width)
                            rightSidebar.dialogWidth = self.width - rightSidebar.width
                        self.width = 320 + rightSidebar.width
                    }
                    else rightSidebar.foldSide()
                }
            }
            Item {Layout.fillHeight: true}
        }
    }

    Connections {
        target: dosearch.navigation
        onActivePageChanged: {
            if (dosearch.navigation.activePage.toString().indexOf("MarkdownEditorPage") >= 0) {
                var page = dosearch.navigation.activePage
                preview.html = Qt.binding(function() {return page.html})
            }
        }
    }

    onContextChanged: {
        preview.html = ""
    }
}

import QtQuick 2.7
import QtQuick.Window 2.0
import QtQuick.Controls 2.1
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
    property real defaultWidth: 320
    property bool screenDnD: false
    property bool containsDnD: false
    property bool inDnD: containsDnD || screenDnD
    property real maxWidth: 0
    property real minWidth: 0
    property var activeItem
    implicitWidth: 0

    onWidthChanged: {
        if (width <= 0)
            return
        if (!!activeItem) {
            activeItem.storedWidth = self.width
            defaultWidth = self.width
        }
    }

    onActiveItemChanged: {
        if (!!activeItem) {
            implicitWidth = activeItem.storedWidth > 0 ? activeItem.storedWidth : self.defaultWidth
            maxWidth = activeItem.maxWidth
            minWidth = activeItem.minWidth
            self.width = implicitWidth
        }
        else {
            implicitWidth = 0
            maxWidth = 0
            minWidth = 0
        }
    }


    property SidebarButton selectedBeforeDnD
    onInDnDChanged: {
//        console.log("DnD changed to " + inDnD + ", active: " + (!!rightSidebar.activeButton ? rightSidebar.activeButton.text : "none"))
        if (inDnD) {
            selectedBeforeDnD = rightSidebar.activeButton
            if (rightSidebar.activeButton != vaultButton)
                rightSidebar.choose(vaultButton)
        }
        else {
            if (selectedBeforeDnD != vaultButton)
                rightSidebar.choose(selectedBeforeDnD)
        }
    }

    PropertyAnimation {
        id: widthAnimation
        target: self
        property: "width"
        easing.type: Easing.OutSine
        duration: 200
    }

    function animateWidthChange(to) {
        self.implicitWidth = to
        widthAnimation.from = self.width
        widthAnimation.to = to
        widthAnimation.start()
    }

    MessageDialog {
        id: taskCancelledDialog
        title: qsTr("Задание закрыто")
        text: qsTr("Задание отменено сервером, обычно такое случается, если клиент отменил задание.")
        onAccepted: visible = false
        visible: false
    }

    Rectangle {
        color: Palette.toolsBackground
        anchors.fill: parent

        Legacy.SplitView {
            id: rightSide
            anchors.fill: parent
            orientation: Qt.Vertical


            OfferView {
                id: offerView

                Layout.fillWidth: true
                Layout.maximumHeight: offerView.maxHeight
                Layout.minimumHeight: offerView.minHeight
                task: self.task
                visible: !!self.task
            }

            Item {
                Layout.fillHeight: true
                Layout.fillWidth: true

                id: screenHolder
                WebEngineView {
                    id: preview
                    visible: false
                    focus: false
                    anchors.fill: parent

                    property real minWidth: 320
                    property real maxWidth: 320

                    property real storedWidth: -1
                    property string html: ""

                    url: "about:blank"

                    onHtmlChanged: {
                        var focused = window.activeFocusItem
                        var html = "<!DOCTYPE html><html><head>
                                <script src=\"qrc:/md-scripts.js\"></script>
                                <link rel=\"stylesheet\" href=\"qrc:/markdownpad-github.css\"></head>
                                <body>" + preview.html+ "</body></html>"

                        loadHtml(html)
                        if (focused)
                            focused.forceActiveFocus()
                    }
                    onUrlChanged: {
                        var url = "" + preview.url
                        // console.log("New url: " + url)
                        if (url.length > 0 && url != "about:blank" && url.indexOf("data:") !== 0) {
                            preview.goBack()
                            dosearch.navigation.handleOmnibox(url, 0)
                        }
                    }
                }
                LeagueChat {
                    id: dialog
                    visible: false
                    anchors.fill: parent

                    property real minWidth: 320
                    property real maxWidth: -1

                    property real storedWidth: -1

                    task: self.task
                }
                Vault {
                    id: vault
                    visible: false
                    anchors.fill: parent

                    property real minWidth: !!self.task ? 320 : -1
                    property real maxWidth: -1

                    property real storedWidth: -1

                    context: self.context
                }
            }
        }
    }

    states: [
        State {
            name: "vault"
            PropertyChanges {
                target: vault
                visible: true
            }

            PropertyChanges {
                target: self
                activeItem: vault
            }
        },
        State {
            name: "preview"
            PropertyChanges {
                target: preview
                visible: true
            }

            PropertyChanges {
                target: self
                activeItem: preview
            }
        },
        State {
            name: "dialog"
            PropertyChanges {
                target: dialog
                visible: true
            }

            PropertyChanges {
                target: self
                activeItem: dialog
            }
        }
    ]

    Connections {
        target: dosearch.navigation
        onActivePageChanged: {
            if (dosearch.navigation.activePage.toString().indexOf("MarkdownEditorPage") >= 0) {
                var page = dosearch.navigation.activePage
                preview.html = Qt.binding(function() {return page.html})
            }
        }
    }

    Connections {
        target: task

        onCancelled: {
            window.showDialog(taskCancelledDialog)
        }
    }


    onContextChanged: {
        preview.html = ""
        if (!!context.task)
            state = "dialog"
        else
            state = ""
    }
}

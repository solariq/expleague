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
    property real maxWidth: rightSidebar.activeButton ? (rightSidebar.activeButton.maxAssociatedWidth >= 0 ? rightSidebar.activeButton.maxAssociatedWidth + rightSidebar.width : -1) : rightSidebar.width
    property real minWidth: rightSidebar.activeButton ? rightSidebar.activeButton.minAssociatedWidth + rightSidebar.width: rightSidebar.width
    implicitWidth: rightSidebar.width
    property bool screenDnD: false
    property bool containsDnD: false
    property bool inDnD: containsDnD || screenDnD

    property SidebarButton selectedBeforeDnD
    DropArea {
        anchors.fill: parent
        onEntered: {
            containsDnD = true
        }

        onExited: {
            dosearch.main.delay(100, function () {containsDnD = false})
        }

        onDropped: {
            containsDnD = false
            drop.getDataAsString(drop.formats[1])
            var source = "empty"
            if (drop.source.toString().search("Main_QMLTYPE") >= 0) {
                source = dosearch.navigation.activePage.id
            }

            if (context.vault.drop(drop.text, drop.html, drop.urls, source)) {
                drop.accept()
            }
        }
    }

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
        anchors.fill: parent
        spacing: 0
        Legacy.SplitView {
            id: rightSide
            Layout.fillWidth: true
            Layout.fillHeight: true

            orientation: Qt.Vertical

            ColumnLayout {
                id: taskView

                visible: !!self.task
                Layout.fillWidth: true
                Layout.minimumHeight: 23
                Layout.maximumHeight: offerHeight + 23
                Layout.preferredHeight: Math.min(450, offerHeight)
                spacing: 0

                property real offerHeight: 0
                property real storedHeight: 450
                onVisibleChanged: {
                    if (visible) {
                        offerView.task = self.task
                        storedHeight = Math.min(450, offerView.implicitHeight + 23)
                        offerViewHolder.visible = true
                        offerHeight = storedHeight
                        height = offerHeight + 23
                    }
                    else {
                        offerView.task = null
                        storedHeight = 0
                        offerHeight = 0
                        offerViewHolder.visible = true
                        height = 0
                    }
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

                            onClicked: {
                                if (offerViewHolder.visible) {
                                    taskView.storedHeight = taskView.height
                                    offerViewHolder.visible = false
                                    taskView.height = 23
                                }
                                else {
                                    taskView.height = taskView.storedHeight
                                    taskView.offerHeight = taskView.storedHeight
                                    offerViewHolder.visible = true
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
                    visible: false
                    Flickable {
                        anchors.fill: parent
                        flickableDirection: Flickable.VerticalFlick
                        contentHeight: offerView.implicitHeight
                        OfferView {
                            id: offerView

                            anchors.fill: parent
                            task: null
                            textColor: Palette.selectedTextColor
                        }
                    }
                }
            }
            Item {
                id: screenHolder
//                z: 100500

                Layout.fillHeight: true
                Layout.fillWidth: true
                WebEngineView {
                    id: preview

                    visible: false
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
                        // console.log("New url: " + url)
                        if (url.length > 0 && url != "about:blank" && url.indexOf("data:") !== 0) {
                            preview.goBack()
                            dosearch.navigation.handleOmnibox(url, 0)
                        }
                    }

                    anchors.fill: parent
                }
                LeagueChat {
                    id: dialog
                    visible: false
                    anchors.fill: parent

                    task: self.task
                }
                Flow {
                    id: vault
                    visible: false
                    anchors.fill: parent
                    anchors.margins: 3
                    Repeater {
                        model: context.vault.items
                        delegate: Component {
                            Rectangle {
                                id: thumbnail
                                radius: Palette.radius
                                width: 80
                                height: 80
                                anchors.margins: 3
                                color: thumbnailArea.containsMouse ? Palette.selectedColor : Palette.activeColor
                                property color textColor: thumbnailArea.containsMouse ? Palette.selectedTextColor : Palette.activeTextColor
                                children: [modelData.ui()]
                                onChildrenChanged: {
                                    for (var i in children) {
                                        var child = children[i]
                                        child.visible = true
                                        child.parent = thumbnail
                                        child.color = Qt.binding(function () {return thumbnail.color})
                                        child.textColor = Qt.binding(function () {return thumbnail.textColor})
                                        child.width = width - 6
                                        child.height = height - 6
                                        child.enabled = false
                                    }
                                }
                                MouseArea {
                                    id: thumbnailArea
                                    x: 0
                                    y: 0
                                    height: 80
                                    width: 80
                                    z: thumbnail.z + 10
                                    hoverEnabled: true
                                    onPressed: {
                                        console.log("Pressed mouse")
                                    }

                                    onContainsMouseChanged: {
                                        console.log("Contains mouse")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        ColumnLayout {
            id: rightSidebar
            Layout.fillHeight: true
            Layout.preferredWidth: 22
            Layout.minimumWidth: 22
            Layout.maximumWidth: 22

            property SidebarButton activeButton
            property real sideWidth: 320 + rightSidebar.width
            property real dialogWidth: 320 + rightSidebar.width

            spacing: 0
            function choose(button) {
                if (!!activeButton) {
                    activeButton.storedWidth = rightSide.width
                    activeButton.active = false
                }

                if (activeButton !== button && !!button) {
//                    console.log("Choose " + button.text)
                    activeButton = button
                    activeButton.active = true
                    activeButton.associated.visible = true
                    for(var i in screenHolder.children) {
                        var child = screenHolder.children[i]
                        if (child !== activeButton.associated)
                            child.visible = false
                    }
                    self.animateWidthChange(rightSidebar.width + activeButton.storedWidth)
                }
                else {
//                    console.log("Choose none")
                    activeButton = null
                    self.animateWidthChange(rightSidebar.width)
                }
            }

            Item {Layout.preferredHeight: 20}
            SidebarButton {
                id: dialogButton
                Layout.fillWidth: true
                Layout.preferredHeight: implicitHeight
                visible: !!task
                associated: dialog
                text: qsTr("Диалог")
                onClicked: rightSidebar.choose(dialogButton)
            }
            SidebarButton {
                id: previewButton
                Layout.fillWidth: true
                Layout.preferredHeight: implicitHeight
                visible: !!task
                associated: preview
                storedWidth: 320
                minAssociatedWidth: 320
                maxAssociatedWidth: 320
                text: qsTr("Ответ")
                onClicked: rightSidebar.choose(previewButton)
            }
            SidebarButton {
                id: vaultButton
                Layout.fillWidth: true
                Layout.preferredHeight: implicitHeight
                associated: vault
                text: qsTr("Хранилище")
                onClicked: rightSidebar.choose(vaultButton)
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
        if (!!context.task)
            rightSidebar.choose(dialogButton)
        else
            rightSidebar.choose(null)
    }
}

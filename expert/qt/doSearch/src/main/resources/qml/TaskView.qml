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
        id: dropArea
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
            var src = drop.source
            if (!!src) {
                for (var i in src.keys) {
                    if (src.keys[i] == "vault")
                        return
                }
            }

            if (drop.source && drop.source.toString().search("Main_QMLTYPE") >= 0) {
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
                ColumnLayout {
                    id: vault
                    visible: false
                    property bool editMode: false
                    property real size: 8
                    anchors.fill: parent
                    anchors.margins: 5
                    spacing: 0
                    Rectangle {
                        Layout.preferredHeight: 33
                        Layout.fillWidth: true
                        color: Palette.navigationColor
                        RowLayout {
                            spacing: 5
                            ToolbarButton {
                                id: editVaultButton
                                icon: vault.editMode ? "qrc:/tools/noedit.png" : "qrc:/tools/edit.png"
                                onTriggered: vault.editMode = !vault.editMode
                            }
                            ToolbarButton {
                                id: paste
                                icon: "qrc:/tools/paste.png"
                                onTriggered: {
                                    context.vault.paste()
                                }
                            }
                            ToolbarButton {
                                id: zoomIn
                                icon: "qrc:/tools/zoom-in.png"
                                onTriggered: {
                                    vault.size += 1
                                }
                            }
                            ToolbarButton {
                                id: zoomOut
                                icon: "qrc:/tools/zoom-out.png"
                                onTriggered: {
                                    vault.size -= 1
                                }
                            }
                        }
                    }
                    Flickable {
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        contentWidth: width
                        contentHeight: itemsFlow.implicitHeight
                        clip: true
                        Flow {
                            id: itemsFlow
                            width: parent.width
                            spacing: 5
                            Repeater {
                                model: context.vault.items
                                delegate: Component {
                                    MouseArea {
                                        id: thumbnailArea
                                        width: vault.size * 10 + 6
                                        height: vault.size * 10 + 6
                                        hoverEnabled: true
                                        propagateComposedEvents: true
                                        onClicked: open()
                                        onDoubleClicked: mouse.accepted = false
                                        onPressAndHold: mouse.accepted = false

                                        drag.target: thumbnail

                                        onReleased: {
                                            if (drag.active && !!drag.target && drag.target !== dropArea) {
                                                mouse.accepted = true
                                                thumbnail.Drag.drop()
                                            }
                                            else mouse.accepted = false
                                        }

                                        Rectangle {
                                            id: thumbnail
                                            radius: Palette.radius
                                            width: thumbnailArea.width
                                            height: thumbnailArea.height
                                            anchors.margins: 3

                                            anchors.verticalCenter: parent.verticalCenter
                                            anchors.horizontalCenter: parent.horizontalCenter

                                            color: thumbnailArea.containsMouse || vault.editMode ? Palette.selectedColor : Palette.activeColor
                                            property color textColor: thumbnailArea.containsMouse || vault.editMode ? Palette.selectedTextColor : Palette.activeTextColor
                                            children: [ui()]
                                            onChildrenChanged: {
                                                for (var i in children) {
                                                    var child = children[i]
                                                    child.visible = true
                                                    child.parent = thumbnail
                                                    child.color = Qt.binding(function () {return thumbnail.color})
                                                    child.textColor = Qt.binding(function () {return thumbnail.textColor})
                                                    child.width = Qt.binding(function () {return thumbnail.width - 6})
                                                    child.height = Qt.binding(function () {return thumbnail.height - 6})
                                                    child.enabled = false
                                                    child.size = Qt.binding(function () {return vault.size})
                                                    child.hover = Qt.binding(function () {return thumbnailArea.containsMouse})
                                                }
                                            }
                                            Drag.dragType: Drag.Automatic
                                            Drag.active: thumbnailArea.drag.active

                                            Drag.mimeData: { "text/plain": md, "vault": thumbnail }
                                            Drag.keys: ["text/plain", "vault"]
                                        }
                                        RowLayout {
                                            id: tools
                                            visible: vault.editMode
                                            anchors.centerIn: parent
                                            width: 20
                                            height: 20
                                            ToolbarButton {
                                                id: close
                                                anchors.centerIn: parent
                                                icon: "qrc:/cross.png"
                                                size: 16
                                                onTriggered: {
                                                    context.vault.remove(modelData)
                                                }
                                            }
                                        }
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

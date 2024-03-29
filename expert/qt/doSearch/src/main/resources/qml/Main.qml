import QtQuick 2.8
import QtQuick.Window 2.2
import QtQuick.Controls 1.4
import QtQuick.Controls 2.0 as ControlsNG
import QtGraphicalEffects 1.0
import QtQuick.Layouts 1.1
import QtQuick.Controls.Styles 1.4
import Qt.labs.settings 1.0
//import QtWebEngine 1.3

import ExpLeague 1.0

import "."

ApplicationWindow {
    id: self
        onActiveFocusItemChanged: {
            var item = activeFocusItem
            console.log("active focus changed on")
            while(item){
                console.log("---",item)
                item = item.parent
            }
            console.log("---------------")
        }
    property QtObject activeDialog
    property alias omnibox: omnibox
    //property alias webProfileRef: webProfile
    property alias commonActionsRef: commonActions
    property alias editorActionsRef: editorActions
    property alias screenRef: screen
    property alias sidebarRef: sidebar
    property alias vault: vault
    property bool options: !!dosearch.navigation.context.task ? true : false
    property real rightMargin: 0
    property real leftMargin: 0
    property string dragType: ""
    property var drag

    property alias activeScreenHolder: activeScreenHolder
    property alias screensHolder: screensHolder

    property int someInt: 100
    //    flags: {
    //        if (Qt.platform.os === "osx")
    //            return Qt.FramelessWindowHint | Qt.WindowCloseButtonHint | Qt.WindowMinimizeButtonHint | Qt.WindowMaximizeButtonHint
    //        return 134279169
    //    }

    WindowStateSaver {
        window: self
        id: mainPageSettings
        defaultHeight: 700
        defaultWidth: 1000
    }

    Component.onCompleted: {
        root.main = self;
    }

    visible: true

    function delay(delayTime, cb) {
        var timer = Qt.createQmlObject("import QtQuick 2.0; Timer {}", root);
        timer.interval = delayTime;
        timer.repeat = false;
        timer.triggered.connect(cb);
        timer.start();
    }

    function openLink(request, owner, focusOpened) {
        if (linkReceiver.operation != "") {
            console.log("Link receiver is busy, putting open link operation to the queue")
            linkReceiver.queue.push(function () {
                openLink(request, owner, focusOpened)
            })
            return
        }
        console.log("Requesting resolve for request " + request)
        linkReceiver.operation = "resolve"
        linkReceiver.context = owner
        linkReceiver.focusOpened = focusOpened
        request.openIn(linkReceiver)
    }

    function screenshot(url, size, file, callback) {
        if (screenshotMaker.busy) {
            console.log("screenshotMaker is busy, putting screenshot operation to the queue")
            screenshotMaker.queue.push(function () {
                screenshot(url, size, callback)
            })
            return
        }
        console.log("Requesting screenshot for url " + url)
        screenshotMaker.callback = callback
        screenshotMaker.size = size
        screenshotMaker.file = file
        screenshotMaker.url = url
    }

    function saveScreenshot(url, size, owner) {
        var qurl = url.toString()
        qurl.trim()
        if (qurl === "")
            return
        screenshot(url, size, owner.screenshotTarget(), function () {
            console.log("Saving screenshot " + owner.screenshotTarget())
            owner.screenshotChanged()
        })
    }


    function invite(offer) {
        inviteDialog.offer = offer
        inviteDialog.invitationTimeout = 5 * 60 * 1000
        showDialog(inviteDialog)
    }

    function showHistory() {
        showDialog(history)
    }

    function showDialog(dialog) {
        if (activeDialog && activeDialog.visible)
            activeDialog.visible = false
        activeDialog = dialog
        activeDialog.visible = true
        if (!!dialog && !!activeDialog["forceActiveFocus"]) {
            activeDialog.forceActiveFocus()
        }
    }

    Connections {
        target: activeDialog
        onVisibleChanged: {
            if (activeDialog && !activeDialog.visible)
                activeDialog = null
        }
    }

    Connections {
        target: dosearch.navigation
        onActiveScreenChanged: {
            if (!!dosearch.navigation.activeScreen && "" + dosearch.navigation.activeScreen["options"] != "undefined") {
                dosearch.navigation.activeScreen.options = self.options
            }
        }
    }

    InviteDialog {
        id: inviteDialog
        objectName: "invite"
        visible: false
        x: (Qt.platform.os == "windows" ? self.x : 0) + (self.width - width) / 2
        y: (Qt.platform.os == "windows" ? self.y : 0) + (self.y + dosearch.main.height - height) / 4

        onAccepted: root.league.acceptInvitation(inviteDialog.offer)
        onRejected: root.league.rejectInvitation(inviteDialog.offer)
    }

    TagsDialog {
        id: tagsDialog
        visible: false
        x: (Qt.platform.os == "windows" ? self.x : 0) + (self.width - width) / 2
        y: (Qt.platform.os == "windows" ? self.y : 0) + (self.y + dosearch.main.height - height) / 4

        league: root.league
    }

    SelectProfileDialog {
        id: selectProfile
        visible: false
        objectName: "selectProfile"
        x: (Qt.platform.os == "windows" ? self.x : 0) + (self.width - width) / 2
        y: (Qt.platform.os == "windows" ? self.y : 0) + (self.y + dosearch.main.height - height) / 4
    }

    SuggestDialog {
        id: suggestDialog
        visible: false
        x: (Qt.platform.os == "windows" ? self.x : 0) + (self.width - width) / 2
        y: (Qt.platform.os == "windows" ? self.y : 0) + (self.y + dosearch.main.height - height) / 4
        page: dosearch.navigation.activePage
        suggest: dosearch.navigation.context
    }

    Connections {
        target: dosearch.navigation
        onSuggestAvailable: {
            suggestDialog.suggest = ctxt
            suggestDialog.show()
        }
    }

    ProfileWizard {
        id: createProfile
        visible: false
        x: (Qt.platform.os == "windows" ? self.x : 0) + (self.width - width) / 2
        y: (Qt.platform.os == "windows" ? self.y : 0) + (self.y + dosearch.main.height - height) / 4
    }

    Action {
        id: newProfile
        text: qsTr("Новый...")
        onTriggered: showDialog(createProfile)
    }

    Action {
        id: switchProfile
        text: qsTr("Выбрать...")
        onTriggered: {
            showDialog(selectProfile)
        }
    }

    CommonActions {
        id: commonActions
    }

    EditorActions {
        id: editorActions
    }

    menuBar: menu

    onMenuBarChanged: {
        console.log(menuBar)
    }

    MenuBar {
        id: menu
        Menu {
            title: qsTr("Профиль")
            MenuItem {
                action: newProfile
            }
            MenuItem {
                action: switchProfile
            }
            MenuItem {
                action: commonActions.gotoAdmin
            }
        }
        Menu {
            title: qsTr("Серфинг")
            MenuItem {
                action: commonActions.reload
            }
            MenuItem {
                action: commonActions.closeTab
            }
            MenuItem {
                action: commonActions.saveToVault
            }
            MenuItem {
                action: commonActions.showHistory
            }
            //            MenuItem {
            //                action: commonActions.exitFullScreen
            //            }

            MenuSeparator{}
            MenuItem {
                action: commonActions.showEditor
            }

            MenuItem {
                action: commonActions.showVault
            }

            MenuSeparator{}
            MenuItem {
                action: commonActions.searchInternet
            }
            MenuItem {
                action: commonActions.searchSite
            }
            MenuItem {
                action: commonActions.searchOnPage
            }
            MenuItem {
                action: commonActions.issueUrl
            }

            MenuSeparator{}
            MenuItem {
                action: commonActions.zoomIn
            }
            MenuItem {
                action: commonActions.zoomOut
            }
            MenuItem {
                action: commonActions.resetZoom
            }
        }
        Menu {
            title: qsTr("Правка")
            MenuItem {
                action: commonActions.undo
            }
            MenuItem {
                action: commonActions.redo
            }
            MenuSeparator{}
            MenuItem {
                action: commonActions.cut
            }
            MenuItem {
                action: commonActions.copy
            }
            MenuItem {
                action: commonActions.copyToEditor
            }
            MenuItem {
                action: commonActions.paste
            }
            MenuSeparator{}
            MenuItem {
                action: commonActions.selectAll
            }
        }
        Menu {
            title: qsTr("Редактор")
            enabled: editorActions.editor
            MenuItem {
                action: editorActions.makeBold
            }
            MenuItem {
                action: editorActions.makeItalic
            }
            MenuItem {
                action: editorActions.insertHeader3
            }
            MenuItem {
                action: editorActions.insertImage
            }
            MenuItem {
                action: editorActions.insertLink
            }
            MenuItem {
                action: editorActions.insertSplitter
            }
            MenuItem {
                action: editorActions.makeCut
            }
            MenuItem {
                action: editorActions.insertCitation
            }
            MenuItem {
                action: editorActions.makeEnumeration
            }
            MenuItem {
                action: editorActions.makeList
            }
            MenuItem {
                action: editorActions.insertTable
            }
            MenuItem {
                action: editorActions.todo
            }
        }
    }


    Rectangle {
        id: screen
        color: Palette.backgroundColor("selected")
        z: parent.z + 10
        anchors.fill: parent

        clip: false
        ColumnLayout {
            anchors.fill:parent
            spacing: 0
            Rectangle {
                id: navigation
                Layout.preferredHeight: 40
                Layout.fillWidth: true
                z: parent.z + 10
                gradient: Palette.navigationGradient
                RowLayout {
                    id: tabs
                    anchors.fill: parent
                    spacing: 0
                    Item {Layout.preferredWidth: 4}
                    NavigationTabs {
                        Layout.fillWidth: true
                        Layout.alignment: Qt.AlignVCenter
                        Layout.preferredHeight: 24
                        Layout.maximumHeight: 24

                        navigation: root.navigation
                    }

                    Item {Layout.preferredWidth: 4}
                    ToolbarButton {
                        Layout.preferredHeight: implicitHeight
                        Layout.preferredWidth: implicitWidth
                        imgPadding: 1
                        toggle: omnibox.visible
                        action: commonActions.searchInternet
                    }

                    Item {Layout.preferredWidth: 4}
                    ToolbarButton {
                        Layout.preferredHeight: implicitHeight
                        Layout.preferredWidth: implicitWidth
                        imgPadding: 1
                        action: commonActions.showEditor
                        toggle: ("" + dosearch.navigation.activePage).search(/MarkdownEditorPage/) >=0
                    }
                    Item {visible: !!dosearch.navigation.context.task; Layout.preferredWidth: 4}
                    ToolbarButton {
                        visible: !!dosearch.navigation.context.task
                        Layout.preferredHeight: implicitHeight
                        Layout.preferredWidth: implicitWidth
                        icon: "qrc:/tools/chat.png"
                        highlightedIcon: "qrc:/tools/chat_h.png"
                        imgPadding: 3
                        toggle: sidebar.state == "dialog"
                        onTriggered: sidebar.state = sidebar.state == "dialog" ? "" : "dialog"
                    }
                    Item {visible: !!dosearch.navigation.context.task; Layout.preferredWidth: 4}
                    ToolbarButton {
                        visible: !!dosearch.navigation.context.task
                        Layout.preferredHeight: implicitHeight
                        Layout.preferredWidth: implicitWidth
                        icon: "qrc:/tools/preview.png"
                        highlightedIcon: "qrc:/tools/preview_h.png"
                        toggle: sidebar.state == "preview"
                        onTriggered: sidebar.state = sidebar.state == "preview" ? "" : "preview"
                    }
                    Item {Layout.preferredWidth: 4}
                    ToolbarButton {
                        Layout.preferredHeight: implicitHeight
                        Layout.preferredWidth: implicitWidth
                        toggle: sidebar.state == "vault"
                        action: commonActions.showVault
                    }
                    Item {Layout.preferredWidth: 8}
                    StatusAvatar {
                        Layout.preferredHeight: 33
                        Layout.preferredWidth: 36
                        Layout.alignment: Qt.AlignVCenter
                        id: avatar
                        icon: root.league.profile ? root.league.profile.avatar : "qrc:/avatar.png"
                        size: 33
                    }
                    Item {Layout.preferredWidth: 4}
                }
            }
            RowLayout {
                Layout.fillWidth: true
                Layout.fillHeight: true
                spacing: 0
                SplitView {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    id: centralSplit
                    Item {
                        id: central
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        Item {
                            id: screensHolder
                            objectName: "screensHolder"
                            anchors.fill: parent
                        }
                        Rectangle {
                            color: "white"
                            anchors.fill: parent
                            MouseArea {
                                anchors.fill: parent
                            }
                        }
                        Item {
                            id: activeScreenHolder
                            objectName: "activeScreenHolder"
                            anchors.fill: parent
                            z: 5
                            //                            onChildrenChanged: {
                            //                                if(!children.length > 0){
                            //                                    children[0].forceActiveFocus()
                            //                                }
                            //                            }

                        }
                        Rectangle {
                            anchors.fill: parent
                            color: "transparent"
                        }
                    }

                    SideBar {
                        id: sidebar

                        Layout.fillHeight: true
                        Layout.preferredWidth: implicitWidth
                        Layout.minimumWidth: minWidth
                        Layout.maximumWidth: maxWidth

                        context: dosearch.navigation.context
                        window: self
                    }
                }
            }
        }

        ControlsNG.Button {
            id: optionsButton
            x: tabs.x + 24 + 4 + 2 - width/2
            y: tabs.y + tabs.height - 8
            z: navigation.z - 1
            width: 16
            height: 16
            padding: 3
            visible: !!dosearch.navigation.activeScreen && "" + dosearch.navigation.activeScreen["options"] != "undefined"
            focusPolicy: Qt.NoFocus
            indicator: Image {
                anchors.centerIn: parent
                fillMode: Image.PreserveAspectFit
                mipmap: true
                source: "qrc:/expand.png"
                width: 8
                height: 8
                z: navigation.z + 1
                rotation: self.options ? 180 : 90
            }
            background: Rectangle {
                color: Palette.navigationColor
                radius: height/2
                border.color: Palette.borderColor("active")
                border.width: 1
            }

            onClicked: {
                self.options = !self.options
                dosearch.navigation.activeScreen.options = self.options
            }
        }

        Omnibox {
            id: omnibox
            x: (self.width - width)/2
            y: 200

            width: 500
            height: 30
            visible: false

            window: self
            completion: suggest
            navigation: root.navigation
        }

        GoogleSuggest {
            id: suggest
            x: parent.mapFromItem(omnibox.parent, omnibox.x, omnibox.y + omnibox.height).x
            y: parent.mapFromItem(omnibox.parent, omnibox.x, omnibox.y + omnibox.height).y
            z: omnibox.z + 100
            visible: false
            width: omnibox.width
            height: Math.min(rowHeight*20, list.implicitHeight)

            textField: omnibox
            textToSugget: omnibox.text
        }

        Rectangle {
            id: history
            visible: false

            x: (self.width - width)/2
            y: 200
            z: parent.z + 10
            width: 500
            height: Math.min(24 * (dosearch.history.last30.length + 1) + 4, self.height - y)

            color: Palette.backgroundColor("selected")

            children: [dosearch.history.ui]
            onFocusChanged: {
                if (focus)
                    children[0].forceActiveFocus()
            }
            onChildrenChanged: {
                for(var i in children) {
                    children[i].visible = true
                    children[i].parent = history
                }
            }
        }

        DropShadow {
            visible: !!activeDialog && !!activeDialog["forceActiveFocus"]
            anchors.fill: !!activeDialog && !!activeDialog["forceActiveFocus"] ? activeDialog : null
            cached: true
            radius: 8.0
            samples: 16
            spread: 0.4
            color: "#80000000"
            source: !!activeDialog && !!activeDialog["forceActiveFocus"] ? activeDialog : null
        }

        Vault {
            id: vault
            visible: false
            anchors.right: parent.right
            anchors.bottom: parent.bottom
            x: parent.width - vault.width
            z: parent.z + 80
            y: parent.mapFromItem(centralSplit.parent, centralSplit.x, centralSplit.y).y
            width: 320
            height: centralSplit.height

            context: dosearch.navigation.context

            PropertyAnimation {
                id: vaultWidthAnimation
                target: vault
                property: "width"
                easing.type: Easing.OutSine
                duration: 200
            }

            property string storedState: ""
            function show() {
                if (sidebar.state == "") {
                    vaultWidthAnimation.from = 0
                    vaultWidthAnimation.to = 320
                    self.rightMargin = 320
                    vaultWidthAnimation.start()
                    vault.visible = true
                }
                else sidebar.state = "vault"
                storedState = sidebar.state
            }

            function hide() {
                if (storedState == "") {
                    vaultWidthAnimation.from = 320
                    vaultWidthAnimation.to = 0
                    self.rightMargin = 0
                    vaultWidthAnimation.start()
                    delay(200, function (){
                        visible = false
                    })
                }
                else sidebar.state = storedState
            }

            Connections {
                target: self

                onDragChanged: {
                    console.log("Drag changed to " + self.drag + self.dragType)
                    if (self.drag && self.dragType != "page" && sidebar.state != "vault" && !vault.visible) {
                        vault.show()
                    }
                    else if (!self.drag && vault.visible) {
                        vault.hide()
                    }
                }
            }
        }

        ContextsList {
            id: contexts
            visible: false
            anchors.left: parent.left
            anchors.bottom: parent.bottom
            z: parent.z + 80
            y: parent.mapFromItem(centralSplit.parent, centralSplit.x, centralSplit.y).y
            width: 320
            height: centralSplit.height

            PropertyAnimation {
                id: contextsWidthAnimation
                target: vault
                property: "width"
                easing.type: Easing.OutSine
                duration: 200
            }

            function show() {
                contextsWidthAnimation.from = 0
                contextsWidthAnimation.to = 320
                self.leftMargin = 320
                contextsWidthAnimation.start()
                contexts.visible = true
            }

            function hide() {
                contextsWidthAnimation.from = 320
                contextsWidthAnimation.to = 0
                self.leftMargin = 0
                contextsWidthAnimation.start()
                delay(200, function (){
                    contexts.visible = false
                })
            }

            Connections {
                target: self

                onDragTypeChanged: {
                    //                    console.log("Drag changed to " + self.drag)
                    if (self.dragType == "page" && dosearch.navigation.context !== dosearch.navigation.activePage) {
                        contexts.show()
                    }
                    else if (self.dragType != "page" && contexts.visible) {
                        contexts.hide()
                    }
                }
            }
        }

        states: [
            State {
                name: "FullScreen"
                PropertyChanges {
                    target: sidebar
                    visible: false
                }
                PropertyChanges {
                    target: navigation
                    visible: false
                }
                PropertyChanges {
                    target: self
                    menuBar: null
                }
            }
        ]
    }

    CefItem{
        id: screenshotMaker
        property bool busy: false
        property string file
        property size size
        property var callback
        property var queue: []


        visible: true
        height: 1280
        width: 1024

        z: parent.z - 10

        onUrlChanged: {
            if(url != ""){
                busy = true
            }
        }

        onLoadEnd: {
            if(busy){
                saveScreenshot(file, 0, 0, size.width, size.height)
            }
        }

        onScreenShotSaved: {
            busy = false
            //url = "about:blank"
            callback()
            if(queue.length > 0){
                call = queue.shift()
                call()
            }
        }
    }
}

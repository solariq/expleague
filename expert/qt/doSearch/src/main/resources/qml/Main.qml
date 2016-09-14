import QtQuick 2.7
import QtQuick.Window 2.2
import QtQuick.Controls 1.4
import QtQuick.Controls 2.0 as ControlsNG
import QtGraphicalEffects 1.0
import QtQuick.Layouts 1.1
import QtQuick.Controls.Styles 1.4
import Qt.labs.settings 1.0
import QtWebEngine 1.3

import ExpLeague 1.0

import "."

ApplicationWindow {
    id: self
    property QtObject activeDialog
    property alias omnibox: omnibox
    property alias webProfileRef: webProfile
    property alias commonActionsRef: commonActions
    property alias editorActionsRef: editorActions
    property alias screenRef: screen
    property bool options: false
    property var drag

    onDragChanged: {
        if (!!drag)
            right.screenDnD = true
        else
            delay(100, function() {right.screenDnD = false})
    }

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
        root.main = self
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

    function screenshot(url, size, callback) {
        if (linkReceiver.operation != "") {
            linkReceiver.queue.push(function () {
                screenshot(url, size, callback)
            })
            return
        }
        console.log("Requesting screenshot for url " + url)
        linkReceiver.operation = "screenshot"
        linkReceiver.context = callback
        linkReceiver.size = size
        linkReceiver.url = url
    }

    function saveScreenshot(url, size, owner) {
        var qurl = url.toString()
        qurl.trim()
        if (qurl === "")
            return
        screenshot(url, size, function (result) {
            console.log("Saving screenshot " + owner.screenshotTarget())
            result.saveToFile(owner.screenshotTarget())
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
        x: (self.width / 2) - (width / 2)
        y: 20

        onAccepted: root.league.acceptInvitation(inviteDialog.offer)
        onRejected: root.league.rejectInvitation(inviteDialog.offer)
    }

    TagsDialog {
        id: tagsDialog
        visible: false
        x: (self.width / 2) - (width / 2)
        y: 20

        league: root.league
    }

    SelectProfileDialog {
        id: selectProfile
        visible: false
        objectName: "selectProfile"
        x: self.width / 2 - width / 2
        y: 20
    }

    WebEngineProfile {
        id: webProfile
        storageName: dosearch.league.profile ? dosearch.league.profile.deviceJid : "default"
        httpUserAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/45.0.2454.101 Safari/537.36"
        persistentCookiesPolicy: WebEngineProfile.ForcePersistentCookies

        onDownloadRequested: {
            console.log("Download requested: " + download.path)
            var contextUI = dosearch.navigation.context.ui()
            contextUI.downloads.append(download)
            download.accept()
            dosearch.navigation.select(0, dosearch.navigation.context)
        }
    }

    Action {
        id: newProfile
        text: qsTr("Новый...")
        onTriggered: {
            var wizardComponent = Qt.createComponent("ProfileWizard.qml");
            var wizard = wizardComponent.createObject(self)
            showDialog(wizard)
        }
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

    menuBar: MenuBar {
        Menu {
            title: qsTr("Профиль")
            MenuItem {
                action: newProfile
            }
            MenuItem {
                action: switchProfile
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
            MenuItem {
                action: commonActions.exitFullScreen
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
        }
    }

    Rectangle {
        id: screen
        color: Palette.backgroundColor
        z: parent.z + 10
        anchors.fill: parent
        ColumnLayout {
            anchors.fill:parent
            spacing: 0
            Rectangle {
                Layout.preferredHeight: 40
                Layout.fillWidth: true
                z: parent.z + 10
                color: Palette.navigationColor
                RowLayout {
                    id: tabs
                    anchors.fill: parent
                    spacing: 0
                    Item {Layout.preferredWidth: 6}
                    NavigationTabs {
                        Layout.fillWidth: true
                        Layout.alignment: Qt.AlignVCenter
                        Layout.preferredHeight: 24
                        Layout.maximumHeight: 24

                        navigation: root.navigation
                    }
                    Item {Layout.preferredWidth: 3}
                    StatusAvatar {
                        Layout.preferredHeight: 34
                        Layout.preferredWidth: 34
                        Layout.alignment: Qt.AlignVCenter
                        id: avatar
                        icon: root.league.profile ? root.league.profile.avatar : "qrc:/avatar.png"
                        size: 33
                    }
                    Item {Layout.preferredWidth: 2}
                }
            }
            SplitView {
                Layout.fillWidth: true
                Layout.fillHeight: true
                id: centralSplit
                clip: false
                Item {
                    id: central
                    clip: false
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    children: root.navigation.screens
                }
                TaskView {
                    id: right

                    Layout.fillHeight: true
                    Layout.preferredWidth: implicitWidth
                    Layout.minimumWidth: minWidth
                    Layout.maximumWidth: maxWidth

                    context: dosearch.navigation.context
//                    screenDnD: !!drag
//                    visible: !!dosearch.navigation.context.task
                    window: self
                }
            }
        }

        ControlsNG.Button {
            id: optionsButton
            x: tabs.x + 5
            y: tabs.y + tabs.height -5
            z: tabs.z + 1
            width: 12
            height: 12
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
                rotation: self.options ? 0 : -90
            }
            background: Rectangle {
                color: Palette.activeColor
                radius: height/2
                border.color: Palette.navigationColor
                border.width: 2
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

            color: Palette.backgroundColor

            children: [dosearch.history.ui()]
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
            anchors.fill: activeDialog
            cached: true
            radius: 8.0
            samples: 16
            spread: 0.4
            color: "#80000000"
            source: activeDialog
        }

        states: [
            State {
                name: "FullScreen"
                PropertyChanges {
                    target: right
                    visible: false
                }
                PropertyChanges {
                    target: tabs
                    visible: false
                }
            }
        ]
    }

    WebEngineView {
        id: linkReceiver
        //            visible: false
        property string operation: ""
        property bool focusOpened: false
        property var context
        property bool jsredir: false
        property size size
        property var queue: []
        width: 370
        height: 370
        profile: webProfile

        url: "about:blank"
        function finish() {
            operation = "finish"
            if (url.toString() != "about:blank") {
                console.log("Back from: " + url + " history length: " + navigationHistory.items.rowCount())
                if (navigationHistory.items.rowCount() > 1) {
                    goBack()
                    return
                }
                url = "about:blank"
            }
            console.log("Link operation finished. Url: " + url)
            operation = ""
            if (queue.length > 0) {
                var callback = queue.shift()
                console.log("Next from queue " + callback)
                callback()
            }
        }

        onUrlChanged: {
//            console.log("url changed to " + url.toString())
            if (operation == "finish") {
                finish()
                return
            }
            if (operation != "resolve")
                return

            var surl = url.toString()
            surl.trim()
            if (surl == "" || surl == "about:blank" || surl.search(/google\.\w+\/url/) !== -1 || surl.search(/yandex\.\w+\/clck\/jsredir/) !== -1) {
                return
            }

            dosearch.navigation.open(url, context, focusOpened, false)
            finish()
        }

        onLoadingChanged: {
            if (operation != "screenshot")
                return
            var surl = url.toString()
            if (surl == "" || surl == "about:blank" || surl.search(/google\.\w+\/url/) !== -1 || surl.search(/yandex\.\w+\/clck\/jsredir/) !== -1) {
                return
            }
            console.log("Screenshot progress: " + surl + " " + loading + " progress: " + loadProgress)
            if (!loading && surl != "about:blank") {
                linkReceiver.grabToImage(context, size)
                finish()
            }
        }
    }
}

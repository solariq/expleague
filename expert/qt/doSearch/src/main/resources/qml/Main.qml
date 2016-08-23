import QtQuick 2.7
import QtQuick.Window 2.2
import QtQuick.Controls 1.4
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
    property alias commonActionsRef: commonActions
    property alias editorActionsRef: editorActions

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
        if (linkReceiver.busy) {
            delay(500, function () {
                openLink(request, owner, focusOpened)
            })
            return
        }
        linkReceiver.context = owner
        linkReceiver.focusOpened = focusOpened
        request.openIn(linkReceiver)
    }

    function invite(offer) {
        inviteDialog.offer = offer
        inviteDialog.invitationTimeout = 5 * 60 * 1000
        showDialog(inviteDialog)
    }

    function showDialog(dialog) {
        if (activeDialog && activeDialog.visible)
            activeDialog.visible = false
        activeDialog = dialog
        activeDialog.visible = true
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
                action: commonActions.searchInternet
            }
            MenuItem {
                action: commonActions.searchSite
            }
            MenuItem {
                action: commonActions.searchOnPage
            }
            MenuItem {
                action: commonActions.closeTab
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
            MenuSeparator{}
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
            property var s: EditorActions.childAt(assd)
        }
    }

    Rectangle {
        color: Palette.backgroundColor
        anchors.fill: parent
        ColumnLayout {
            anchors.fill:parent
            spacing: 0
            RowLayout {
                Layout.preferredHeight: 40
                Layout.fillWidth: true
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
            SplitView {
                Layout.fillWidth: true
                Layout.fillHeight: true
                id: centralSplit
                Item {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    id: central
                    children: root.navigation.screens
                }
                TaskView {
                    Layout.fillHeight: true
                    Layout.preferredWidth: implicitWidth
                    Layout.minimumWidth: minWidth
                    Layout.maximumWidth: maxWidth

                    context: dosearch.navigation.context
                    visible: dosearch.navigation.context.task != null
                    window: self
                }
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
        WebEngineView {
            id: linkReceiver
            visible: false
            property bool focusOpened: false
            property var context
            property bool busy: false
            property bool jsredir: false

            url: "about:blank"

            onUrlChanged: {
                var surl = url.toString()
                if (surl.length == 0 || surl == "about:blank")
                    return
                else if (surl.search(/google\.\w+\/url/) != -1 || surl.search(/yandex\.\w+\/clck\/jsredir/) != -1) {
                    jsredir = true
                    return
                }

                dosearch.navigation.open(url, context, focusOpened)
                goBack()
                if (jsredir)
                    goBack()
                busy = false
            }
        }
    }
}

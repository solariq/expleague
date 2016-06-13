import QtQuick 2.5
import QtQuick.Window 2.2
import QtQuick.Controls 1.4
import QtGraphicalEffects 1.0
import QtWebView 1.0
import QtQuick.Layouts 1.1
import QtQuick.Controls.Styles 1.4
import Qt.labs.settings 1.0

import ExpLeague 1.0

import "."

ApplicationWindow {
    id: mainWindow
    property QtObject activeDialog
    property alias omnibox: omnibox

    flags: {
        if (Qt.platform.os === "osx")
            return Qt.FramelessWindowHint | Qt.WindowCloseButtonHint | Qt.WindowMinimizeButtonHint | Qt.WindowMaximizeButtonHint
        return 134279169
    }

    WindowStateSaver {
        window: mainWindow
        id: mainPageSettings
        defaultHeight: 700
        defaultWidth: 1000
    }

    Component.onCompleted: {
        root.main = mainWindow
    }

    visible: true

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
        x: (mainWindow.width / 2) - (width / 2)
        y: 20

        onAccepted: root.league.acceptInvitation(inviteDialog.offer)
        onRejected: root.league.rejectInvitation(inviteDialog.offer)
    }

    TagsDialog {
        id: tagsDialog
        visible: false
        x: (mainWindow.width / 2) - (width / 2)
        y: 20

        league: root.league
    }

    SelectProfileDialog {
        id: selectProfile
        visible: false
        objectName: "selectProfile"
        x: mainWindow.width / 2 - width / 2
        y: 20
    }

    Action {
        id: newProfile
        text: qsTr("Новый...")
        onTriggered: {
            var wizardComponent = Qt.createComponent("ProfileWizard.qml");
            var wizard = wizardComponent.createObject(mainWindow)
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
        }

        Menu {
            title: qsTr("Правка")
            MenuItem {
                action: CommonActions.undo
            }
            MenuItem {
                action: CommonActions.redo
            }
            MenuSeparator{}
            MenuItem {
                action: CommonActions.cut
            }
            MenuItem {
                action: CommonActions.copy
            }
            MenuItem {
                action: CommonActions.paste
            }
            MenuSeparator{}
            MenuItem {
                action: CommonActions.selectAll
            }
            MenuSeparator{}
            Menu {
                title: qsTr("Редактор")
                enabled: EditorActions.editor
                MenuItem {
                    action: EditorActions.makeBold
                }
                MenuItem {
                    action: EditorActions.makeItalic
                }
                MenuItem {
                    action: EditorActions.insertHeader3
                }
                MenuItem {
                    action: EditorActions.insertImage
                }
                MenuItem {
                    action: EditorActions.insertLink
                }
                MenuItem {
                    action: EditorActions.insertSplitter
                }
                MenuItem {
                    action: EditorActions.makeCut
                }
                MenuItem {
                    action: EditorActions.insertCitation
                }
                MenuItem {
                    action: EditorActions.makeEnumeration
                }
                MenuItem {
                    action: EditorActions.makeList
                }
                MenuItem {
                    action: EditorActions.insertTable
                }
            }
            MenuSeparator {}
            MenuItem {
                action: CommonActions.searchInternet
            }
            MenuItem {
                action: CommonActions.searchSite
            }
            MenuItem {
                action: CommonActions.searchOnPage
            }
        }
    }

    menuBar: Qt.platform.os === "osx" ? menu : undefined

    Rectangle {
        color: Palette.backgroundColor
        z: parent.z + 10
        anchors.margins: 2
        anchors.fill: parent
        ColumnLayout {
            anchors.fill:parent
            spacing: 0

            Item {
                Layout.fillWidth: true
                Layout.minimumHeight: 65
                id: navigation

                RowLayout {
                    anchors.fill: parent
                    spacing: 0
                    ColumnLayout {
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        spacing: 0

                        RowLayout {
                            id: upperNavigation
                            Layout.fillWidth: true
                            Layout.minimumHeight: 25
                            Layout.maximumHeight: 25
                            spacing: 0

                            Item {Layout.minimumWidth: 8}

                            WButtonsGroupMac {
                                Layout.preferredWidth: implicitWidth
                                Layout.fillHeight: true
                                win: mainWindow

                                visible: Qt.platform.os === "osx"
                            }

                            Item {Layout.minimumWidth: 14; visible: Qt.platform.os === "osx"}

                            TabButtons {
                                Layout.topMargin: 5
                                Layout.fillWidth: true
                                Layout.fillHeight: true

                                model: root.contexts
                            }
                            Item {Layout.minimumWidth: 14; visible: Qt.platform.os !== "osx"}
//                            WButtonsGroupWin {
//                                Layout.preferredWidth: implicitWidth
//                                Layout.fillHeight: true
//                                win: mainWindow

//                                visible: Qt.platform.os !== "osx"
//                            }

                        }
                        Rectangle {
                            Layout.fillWidth: true
                            Layout.minimumHeight: 40
                            Layout.maximumHeight: 40

                            id: bottomNavigation
                            color: Palette.navigationColor
                            RowLayout {
                                id: foldersRow
                                anchors.fill: parent
                                z: parent.z + 10
                                spacing: 0

                                Component {
                                    id: folderDelegate
                                    Item {
                                        Layout.preferredWidth: 38
                                        Layout.fillHeight: true
                                        Image {
                                            source: icon
                                            height: 25
                                            mipmap: true
                                            fillMode: Image.PreserveAspectFit

                                            anchors.centerIn: parent
                                            MouseArea {
                                                anchors.fill: parent
                                                onClicked: {
                                                    active = true
                                                }
                                            }
                                        }
                                    }
                                }
                                Item { Layout.preferredWidth: 5; }
                                Repeater {
                                    model: root.context.folders
                                    delegate: folderDelegate
                                }

                                Item { Layout.preferredWidth: 5; }
                                Item {
                                    Layout.fillWidth: true
                                    Layout.fillHeight: true
                                    Omnibox {
                                        id: omnibox
                                        window: mainWindow
                                        anchors.centerIn: parent
                                        height: 20
                                        width: parent.width > 600 ? 600 : parent.width
                                        text: root.location
                                    }
                                }
                            }
                        }
                    }
                    Item {
                        Layout.fillHeight: true
                        Layout.preferredWidth: 65

//                        color: mainWindow.navigationColor
                        StatusAvatar {
                            anchors.centerIn: parent
                            height: 49
                            width: 49
                            id: status
                            icon: root.league.profile ? root.league.profile.avatar : "qrc:/avatar.png"

                            size: 49
                        }
                    }
                }
                MouseArea{
                    anchors.fill: parent;
                    z: parent.z - 10
                    property point startPos: "0,0";
                    acceptedButtons: Qt.LeftButton

                    onPressed: {
                        console.log("accepted:" + mouse.accepted)
                        startPos = Qt.point(mouse.x, mouse.y);
                    }

                    onPositionChanged: {
                        var d = Qt.point(mouse.x - startPos.x, mouse.y - startPos.y);
                        mainWindow.x = mainWindow.x + d.x
                        mainWindow.y = mainWindow.y + d.y
                    }
                }
            }
            ContextView {
                id: contextView
                Layout.fillHeight: true
                Layout.fillWidth: true
                statusBar: statusBar
                context: root.context
                window: mainWindow
                tagsDialog: tagsDialog
            }
            Rectangle {
                id: statusBar

                Layout.fillWidth: true
                Layout.minimumHeight: 20

                visible: false
                color: Palette.navigationColor
                RowLayout {

                }
            }
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
    }

    TransparentMouseArea {
        property var window: mainWindow
        property point startWPos: "0,0";
        property point startPos: "0,0";
        property size startSize: "0x0";
        property bool topStart: false
        property bool bottomStart: false
        property bool leftStart: false
        property bool rightStart: false
        property bool resizing: false

        acceptedButtons: Qt.LeftButton

        function shape() {
            if (leftStart) {
                if (topStart) {
                    cursorShape = Qt.SizeFDiagCursor
                }
                else if (bottomStart) {
                    cursorShape = Qt.SizeBDiagCursor
                }
                else {
                    cursorShape = Qt.SizeHorCursor
                }
            }
            else if (rightStart) {
                if (topStart) {
                    cursorShape = Qt.SizeBDiagCursor
                }
                else if (bottomStart) {
                    cursorShape = Qt.SizeFDiagCursor
                }
                else {
                    cursorShape = Qt.SizeHorCursor
                }
            }
            else if (topStart || bottomStart) {
                cursorShape = Qt.SizeVerCursor
            }
            else {
                cursorShape = Qt.ArrowCursor
                return false
            }
            return true
        }

        onMouseXChanged: {
            if (resizing) {
                var dX = window.x + mouse.x - startPos.x
                if (leftStart) {
                    window.x = startWPos.x + dX
                    window.width = startSize.width - dX
                }
                if (rightStart) {
                    window.width = startSize.width + dX
                }
                mouse.accepted = true
            }
            else {
                leftStart = Math.abs(mouseX) < 5
                rightStart = Math.abs(window.width - mouseX) < 5
                mouse.accepted = shape()
            }
        }

        onMouseYChanged: {
            if (resizing) {
                var dY = window.y + mouse.y - startPos.y;
                if (topStart) {
                    window.y = startWPos.y + dY
                    window.height = startSize.height - dY
                }
                if (bottomStart) {
                    window.height = startSize.height + dY
                }
                mouse.accepted = true
            }
            else {
                topStart = Math.abs(mouseY) < 5
                bottomStart = Math.abs(window.height - mouseY) < 5
                mouse.accepted = shape()
            }
        }

        onPressed: {
            leftStart = Math.abs(mouse.x) < 5
            rightStart = Math.abs(window.width - mouse.x) < 5
            topStart = Math.abs(mouse.y) < 5
            bottomStart = Math.abs(window.height - mouse.y) < 5

            startWPos = Qt.point(window.x, window.y);
            startSize = Qt.size(window.width, window.height)
            startPos = Qt.point(mouse.x + window.x, mouse.y + window.y);
            resizing = mouse.accepted = topStart || bottomStart || leftStart || rightStart
        }

        onReleased: {
            if (resizing) {
                resizing = false
                mouse.accepted = true
            }
            else mouse.accepted = false
        }
    }
}

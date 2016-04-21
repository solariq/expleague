import QtQuick 2.5
import QtQuick.Controls 1.4
import QtWebView 1.0
import QtQuick.Layouts 1.1
import QtQuick.Controls.Styles 1.4
import Qt.labs.settings 1.0

import ExpLeague 1.0

ApplicationWindow {
    id: window

    flags:  Qt.FramelessWindowHint|Qt.MacWindowToolBarButtonHint|Qt.WindowMinimizeButtonHint|Qt.WindowMaximizeButtonHint

    property string backgroundColor: "#f0f0f0"
    property string idleColor: "#e0e0e0"
    property string activeColor: "#eeeeee"

    Settings {
        id: mainPageSettings
        category: "MainPage"
        property alias x: window.x
        property alias y: window.y
        property alias width: window.width
        property alias height: window.height
    }

    visible: true

    Profile {
        id: profile
    }

    Action {
        id: newProfile
        text: "Новый"
        onTriggered: {
            profile.register.show()
        }
    }

    menuBar: MenuBar {
        Menu {
            title: "Профиль"
            MenuItem {
                action: newProfile
            }
            MenuItem {
                text: "Переключить"
            }
        }
    }

    Item {
        z: parent.z + 10
        anchors.margins: 3
        anchors.fill:parent
        ColumnLayout {
            spacing: 0
            anchors.fill:parent
            Rectangle {
                Layout.fillWidth: true
                Layout.minimumHeight: 45
                Layout.maximumHeight: 45
                id: navigationBar
                color: backgroundColor
                RowLayout {
                    id: toolsRow
                    anchors.fill: parent
                    anchors.centerIn: parent
                    z: parent.z + 10
                    spacing: 0

                    Item { Layout.preferredWidth: 5 }

                    Component {
                        id: folderDelegate
                        Rectangle {
                            Layout.preferredWidth: 30
                            Layout.fillHeight: true
                            color: backgroundColor
                            Image {
                                source: icon
                                width: 24; height: 24

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

                    Repeater {
                        model: root.context.folders
                        delegate: folderDelegate
                    }

                    Item { Layout.preferredWidth: 5 }
                    TextField {
                        Layout.fillWidth: true
                        id: urlField
                        text: root.location
                        selectByMouse: true
                        inputMethodHints: Qt.ImhNoPredictiveText

                        function commit(tab) {
                            root.context.handleOmniboxInput(this.text, tab)
                        }

                        onTextChanged: {
                            if (!focus)
                                return
                            if (text.length > 2) {
                                suggest.textField = this
                                suggest.textToSugget = text
                                suggest.visible = true
                            }
                            else {
                                suggest.visible = false
                            }
                        }
                        onFocusChanged: {
                            if (focus && !suggest.visible) {
                                selectAll()
                            }
                        }

                        Keys.enabled: true
                        Keys.onPressed: {
                            if (!focus)
                                return

                            if (event.key == Qt.Key_Enter || event.key == Qt.Key_Return) {
                                focus = false
                                commit((event.modifiers & (Qt.ControlModifier | Qt.MetaModifier)) != 0)
                                text = Qt.binding(function() {return root.location})
                                suggest.visible = false
                            }
                            else if (event.key == Qt.Key_Down && suggest.visible) {
                                suggest.focus = true
                            }
                            else if (event.key == Qt.Key_Escape && suggest.visible) {
                                suggest.visible = false
                            }
                        }
                    }

                    Item { Layout.preferredWidth: 5 }
                }
                MouseArea {
                    anchors.fill: parent;
                    property variant startPos: "0,0";
                    onPressed: {
                        startPos = Qt.point(mouse.x, mouse.y);
                    }
                    onPositionChanged: {
                        var d = Qt.point(mouse.x - startPos.x, mouse.y - startPos.y);
                        window.x =  window.x + d.x
                        window.y =  window.y + d.y
                    }
                }
            }
            Rectangle {
                id: tabsContainer
                Layout.fillWidth: true
                Layout.minimumHeight: 20
                Layout.maximumHeight: 20
                anchors.leftMargin: 5
                anchors.rightMargin: 5
                color: backgroundColor

                Component {
                    id: tabButton

                    Rectangle {
                        id: tabItem
                        Layout.minimumHeight: 26
                        Layout.maximumWidth: 240
                        Layout.minimumWidth: 50
                        Layout.bottomMargin: -5
                        height: tabItemText.height + 2
                        border.color: "lightgray"
                        border.width: 1
                        radius: 5
                        color: active ? activeColor : idleColor

                        Image {
                            id: crossIcon
                            z: parent.z + 10
                            anchors {
                                leftMargin: 5
                                rightMargin: 5
                                verticalCenter: parent.verticalCenter
                                verticalCenterOffset: -3
                                left: parent.left
                                right: tabItemText.left
                            }
                            source: tabMouseArea.containsMouse ? "qrc:/cross.png" : this['icon'] !== null ? icon : ""
                            visible: tabMouseArea.containsMouse || this['icon'] !== null
                            fillMode: Image.PreserveAspectFit
                            height: 14
                            width: 14
                            MouseArea {
                                anchors.fill: parent
                                onClicked: {
                                    remove()
                                }
                            }
                        }
                        Text {
                            property bool isLong: name.length >= 30
                            id: tabItemText
                            anchors {
                                leftMargin: 8
                                centerIn: parent
                                horizontalCenterOffset: 5
                                verticalCenterOffset: -3
                            }
                            color: "#505050"
                            text: name
                        }
                        states: [
                            State {
                                name: "wide text"
                                when: tabItemText.isLong
                                PropertyChanges {
                                    target: tabItemText
                                    elide: Text.ElideMiddle
                                    width: 200
                                }
                                PropertyChanges {
                                    target: tabItem
                                    Layout.preferredWidth: 240
                                }
                            },
                            State {
                                name: "not wide text"
                                when: !tabItemText.isLong
                                PropertyChanges {
                                    target: tabItemText
                                    elide: Text.ElideNone
                                    width: tabItem.width - 40
                                }
                                PropertyChanges {
                                    target: tabItem
                                    Layout.preferredWidth: tabItemText.paintedWidth + 40
                                }
                            }
                        ]

                        MouseArea {
                            id: tabMouseArea
                            anchors.fill: parent
                            hoverEnabled: true
                            onClicked: {
                                active = true
                            }
                        }
                    }
                }

                RowLayout {
                    spacing: 1
                    Repeater {
                        Layout.fillWidth: true
                        focus: true
                        id: tabs
                        delegate: tabButton
                        model: root.folder != null ? root.folder.screens : empty
                    }
                }
            }
            Rectangle {
                id: central
                Layout.fillWidth: true
                Layout.fillHeight: true
                Connections {
                    target: root.folder

                    onScreenChanged: {
                        central.children = []
                        if (screen != null)
                            screen.bind(central)
                    }
                }
            }
        }

        GoogleSuggest {
            id: suggest
            x: urlField.x
            y: urlField.y + urlField.height
            z: urlField.z + 100

            visible: false
            width: urlField.width
            height: 100
        }
    }

    ListModel {
        id: empty
    }

    MouseArea {
        property point startWPos: "0,0";
        property point startPos: "0,0";
        property size startSize: "0x0";
        property bool topStart: false
        property bool bottomStart: false
        property bool leftStart: false
        property bool rightStart: false
        property bool resizing: false
        anchors.fill: parent
//        anchors.margins: -5
        propagateComposedEvents: true
        hoverEnabled: true
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
            if (resizing)
                return
            leftStart = Math.abs(mouseX) < 5
            rightStart = Math.abs(window.width - mouseX) < 5
            mouse.accepted = shape()
        }

        onMouseYChanged: {
            if (resizing)
                return
            topStart = Math.abs(mouseY) < 5
            bottomStart = Math.abs(window.height - mouseY) < 5
            mouse.accepted = shape()
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

        onClicked: mouse.accepted = false;
        onReleased: {
            resizing = false
            mouse.accepted = false;
        }
        onDoubleClicked: mouse.accepted = false;
        onPressAndHold: mouse.accepted = false;

        onPositionChanged: {
            if (!resizing) {
                mouse.accepted = false
                return
            }
            var d = Qt.point(window.x + mouse.x - startPos.x, window.y + mouse.y - startPos.y);
            if (leftStart) {
                window.x = startWPos.x + d.x
                window.width = startSize.width - d.x
            }
            if (rightStart) {
                window.width = startSize.width + d.x
            }
            if (topStart) {
                window.y = startWPos.y + d.y
                window.height = startSize.height - d.y
            }
            if (bottomStart) {
                window.height = startSize.height + d.y
            }
        }
    }
}

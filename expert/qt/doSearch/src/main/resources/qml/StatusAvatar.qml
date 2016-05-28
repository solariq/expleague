import QtQuick 2.5
import QtQuick.Controls 1.4
import QtQuick.Dialogs 1.2

import ExpLeague 1.0

Item {
    property alias icon: avatar.src
    property int size

    MessageDialog {
        id: refuseDialog
        title: qsTr("Отказаться от задания")
        text: qsTr("Вы уверены, что хотите отказаться от этого задания?")
        visible: false
        standardButtons: StandardButton.Yes | StandardButton.No
        onYes: {
            root.context.task.cancel()
            visible = false
        }
        onNo: {
            visible = false
        }
    }

    Action {
        id: connectAction
        text: qsTr("Подключиться")
        enabled: statusMenu.connect
        onTriggered: {
            root.league.connect()
        }
    }

    Action {
        id: disconnectAction
        text: qsTr("Отключиться")
        enabled: statusMenu.disconnect
        onTriggered: {
            root.league.disconnect()
        }
    }

    Action {
        id: refuseAction
        text: qsTr("Отказаться от задания")
        enabled: statusMenu.refuse
        onTriggered: {
            refuseDialog.visible = true
        }
    }

    Avatar {
        id: avatar
        anchors.fill: parent
        size: parent.size
        showStatus: false

        Item {
            id: status
            property string statusImg: "qrc:/status/offline.png"
            property string _statusImg: statusImg
            height: 14
            width: 14

            Image {
                id: statusImage
                source: status._statusImg
                sourceSize: "42x42"
                fillMode: Image.PreserveAspectFit
                anchors.fill: parent
                mipmap: true

                states: [
                    State {
                        when: statusArea.containsMouse || statusMenu.visible
                        PropertyChanges {
                            target: status
                            _statusImg: statusImg.substring(0, statusImg.length - 4) + "_h.png"
                        }
                    },
                    State {
                        when: !statusArea.containsMouse
                        PropertyChanges {
                            target: status
                            _statusImg: statusImg
                        }
                    }]
            }
            x: parent.width - status.width - 1
            y: parent.height - status.height - 1
            z: parent.z + 1
            MouseArea {
                anchors.fill: parent
                id: statusArea
                acceptedButtons: Qt.LeftButton
                hoverEnabled: true

                onClicked: {
                    statusMenu.popup()
                    mouse.accepted = true
                }
            }

            Menu {
                id: statusMenu
                visible: false

                property bool connect: false
                property bool disconnect: false
                property bool refuse: false
                MenuItem {
                    action: connectAction
                }
                MenuItem {
                    action: disconnectAction
                }
                MenuSeparator {visible: Qt.platform.os !== "osx"}

                MenuItem {
                    visible: Qt.platform.os !== "osx"
                    action: newProfile
                    text: qsTr("Новый профиль...")
                }
                MenuItem {
                    visible: Qt.platform.os !== "osx"
                    action: switchProfile
                    text: qsTr("Переключить профиль...")
                }

                MenuSeparator {}
                MenuItem {
                    id: statusMenuRefuse
                    text: qsTr("Отказаться от задания")
                    enabled: statusMenu.refuse
                    action: refuseAction
                }
            }
            states: [
                State {
                    name: "no-profile"
                    when: !root.league.profile
                    PropertyChanges {
                        target: status
                        statusImg: "qrc:/status/offline.png"
                    }
                    PropertyChanges {
                        target: statusMenu
                        connect: false
                        disconnect: false
                        refuse: false
                    }
                },
                State {
                    name: "online"
                    when: root.league.profile && root.league.status === League.LS_OFFLINE
                    PropertyChanges {
                        target: status
                        statusImg: "qrc:/status/offline.png"
                    }
                    PropertyChanges {
                        target: statusMenu
                        connect: true
                        disconnect: false
                        refuse: false
                    }
                },
                State {
                    name: "offline"
                    when: root.league.profile && root.league.status === League.LS_ONLINE
                    PropertyChanges {
                        target: status
                        statusImg: "qrc:/status/online.png"
                    }
                    PropertyChanges {
                        target: statusMenu
                        connect: false
                        disconnect: true
                        refuse: false
                    }
                },
                State {
                    name: "check"
                    when: root.league.profile && root.league.status === League.LS_CHECK
                    PropertyChanges {
                        target: status
                        statusImg: "qrc:/status/waiting.png"
                    }
                    PropertyChanges {
                        target: statusMenu
                        connect: false
                        disconnect: true
                        refuse: false
                    }
                },
                State {
                    name: "invite"
                    when: root.league.profile && root.league.status === League.LS_INVITE
                    PropertyChanges {
                        target: status
                        statusImg: "qrc:/status/new_task.png"
                    }
                    PropertyChanges {
                        target: statusMenu
                        connect: false
                        disconnect: true
                        refuse: true
                    }
                },
                State {
                    name: "busy"
                    when: root.league.profile && root.league.status === League.LS_ON_TASK
                    PropertyChanges {
                        target: status
                        statusImg: "qrc:/status/play.png"
                    }
                    PropertyChanges {
                        target: statusMenu
                        connect: false
                        disconnect: true
                        refuse: true
                    }
                }
            ]
        }
    }
}

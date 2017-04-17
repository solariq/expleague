import QtQuick 2.5
import QtQuick.Window 2.2
import QtQuick.Controls 1.4
import QtQuick.Controls.Styles 1.4
import QtQuick.Layouts 1.1

import ExpLeague 1.0

Window {
    id: dialog

    property color backgroundColor: "#e8e8e8"

    property Task task
    property var experts: []
    property var roles: []
    property var rolesInner: []
    property var expertsInner: []

    onExpertsChanged: {
        var expertsInner = []
        for(var i in experts)
            expertsInner.push(experts[i])
        dialog.expertsInner = expertsInner
    }

    onRolesChanged: {
        var rolesInner = []
        for(var i in roles)
            rolesInner.push(roles[i])
        dialog.rolesInner = rolesInner
    }


    width: 400
    height: Math.max(150 + expertsInner.length * 30, 300)
    x: (dosearch.main.x + dosearch.main.width - width) / 2
    y: (dosearch.main.y + dosearch.main.height - height) / 2
    minimumHeight: height
    maximumHeight: height
    minimumWidth: width
    maximumWidth: width
    opacity: 0.9

    modality: Qt.WindowModal
    color: backgroundColor

    signal appendTag(TaskTag tag)
    Action {
        id: accept
        text: qsTr("Принять")
        onTriggered: {
            var rolesInner = dialog.rolesInner
            var expertsInner = dialog.expertsInner
            task.clearFilter()
            for (var i = 0; i < expertsInner.length; i++) {
                task.filter(expertsInner[i], rolesInner[i])
            }

            dialog.hide()
        }
    }

    Action {
        id: reject
        text: qsTr("Отменить")
        onTriggered: {
            dialog.hide()
        }
    }

    Component {
        id: expertRole
        RowLayout {
            id: expertSelf
            Layout.preferredHeight: 30
            Layout.preferredWidth: 24 + 200 + 100
            Layout.alignment: Qt.AlignHCenter
            property Member expert: modelData
            property var roles: ["accepted", "rejected", "prefered", "none"]
            property int index: {
                for (var i in dialog.expertsInner) {
                    if (dialog.expertsInner[i] === expert)
                        return i
                }
                return -1
            }
            property string role: index >= 0 ? expertSelf.roles[dialog.rolesInner[index]] : "none"
            Avatar {
                Layout.preferredHeight: implicitHeight
                Layout.preferredWidth: implicitWidth
                user: expertSelf.expert
                size: 24
            }
            Text {
                Layout.preferredHeight: implicitHeight
                Layout.preferredWidth: 200
                text: expertSelf.expert.name
            }
            ComboBox {
                Layout.preferredHeight: implicitHeight
                Layout.preferredWidth: 100
                model: ListModel {
                    ListElement { text: qsTr("выбран"); role: "accepted" }
                    ListElement { text: qsTr("забанен"); role: "rejected" }
                    ListElement { text: qsTr("приоритет"); role: "prefered" }
                    ListElement { text: qsTr("нет"); role: "none" }
                }
                currentIndex: {
                    for (var i = 0; i < model.count; i++) {
                        var element = model.get(i)
                        if (element.role == role)
                            return i
                    }
                    return -1
                }
                onActivated: {
                    dialog.rolesInner[expertSelf.index] = index
                }
            }
        }
    }

    ColumnLayout {
        anchors.fill: parent
        Item {Layout.preferredHeight: 20}
        Text {
            Layout.alignment: Qt.AlignHCenter
            text: qsTr("Задайте роли экспертов")
            font.bold: true
            font.pointSize: 14
        }
        Item {Layout.preferredHeight: 10}
        Flickable {
            id: expertsLst
            Layout.alignment: Qt.AlignHCenter
            Layout.fillHeight: true
            Layout.fillWidth: true
            anchors.margins: 10
            contentWidth: width - expertsLst.anchors.margins * 2
            contentHeight: expertsInner.length * 30
            flickDeceleration: Flickable.VerticalFlick

            clip: true
            ColumnLayout {
                anchors.fill: parent
                spacing: 0
                Repeater {
                    model: expertsInner
                    delegate: expertRole
                }
                RowLayout {
                    Layout.alignment: Qt.AlignHCenter
                    Layout.preferredHeight: 30
                    Layout.preferredWidth: 30 + 200 + 100
                    visible: expertsOptions.model.length > 0
                    Item {
                        Layout.preferredWidth: 26
                    }
                    ComboBox {
                        Layout.preferredHeight: implicitHeight
                        Layout.preferredWidth: 200
                        id: expertsOptions
                        model: {
                            var result = []
                            var experts = dosearch.league.experts
                            for (var i in experts) {
                                var found = false
                                for (var j in dialog.expertsInner) {
                                    found |= dialog.expertsInner[j].name == experts[i]
                                }
                                if (!found)
                                    result.push(experts[i])
                            }
                            result.sort()
                            return result
                        }
                    }
                    ComboBox {
                        id: suggestRole
                        Layout.preferredHeight: implicitHeight
                        Layout.preferredWidth: 100
                        model: ListModel {
                            ListElement { text: qsTr("выбран"); role: "accepted" }
                            ListElement { text: qsTr("забанен"); role: "rejected" }
                            ListElement { text: qsTr("приоритет"); role: "prefered" }
                            ListElement { text: qsTr("нет"); role: "none" }
                        }
                        currentIndex: 3
                        onActivated: {
                            if (index > 2)
                                return
                            var experts = []
                            var roles = []
                            for (var i in dialog.expertsInner) {
                                experts.push(dialog.expertsInner[i])
                                roles.push(dialog.rolesInner[i])
                            }
                            experts.push(dosearch.league.findMemberByName(expertsOptions.currentText))
                            roles.push(index)
                            dialog.rolesInner = roles
                            dialog.expertsInner = experts
                            expertsOptions.currentIndex = -1
                            dosearch.main.delay(100, function () {
                                suggestRole.currentIndex = 3
                            })
                        }
                    }
                }
            }
        }

        RowLayout {
            Layout.preferredHeight: rejectButton.implicitHeight
            Layout.fillWidth: true
            spacing: 0
            Item { Layout.preferredWidth: 15 }
            Button {
                id: rejectButton
                Layout.alignment: Qt.AlignLeft
                Layout.preferredWidth: 130
                action: reject
            }
            Item {
                Layout.fillWidth: true
            }
            Button {
                focus: true
                Layout.alignment: Qt.AlignRight
                Layout.preferredWidth: 130
                action: accept
            }
            Item { Layout.preferredWidth: 15 }
        }
        Item { Layout.preferredHeight: 5 }
        Keys.onEscapePressed: {
            dialog.hide()
        }
    }
}

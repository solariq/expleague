import QtQuick 2.5
import QtQuick.Window 2.2
import QtQuick.Controls 1.4
import QtQuick.Controls.Styles 1.4
import QtQuick.Layouts 1.1

import ExpLeague 1.0

Window {
    id: dialog

    property Task task
    property color backgroundColor: "#e8e8e8"
    property int invitationTimeout: 0
    property string text
    property string comment

    width: 350
    height: 200
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
        text: qsTr("Отправить")
        onTriggered: {
            task.commitOffer(dialog.text, dialog.comment)
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

    ColumnLayout {
        anchors.fill: parent
        Item {Layout.preferredHeight: 20}
        Text {
            Layout.alignment: Qt.AlignHCenter
            text: qsTr("Подготовка к отправке задания")
            font.bold: true
            font.pointSize: 14
        }
        Flickable {
            Layout.alignment: Qt.AlignHCenter
            Layout.fillHeight: true
            contentHeight: contents.implicitHeight
            contentWidth: width - contents.anchors.margins * 2
            clip: true
            GridLayout {
                id: contents
                anchors.margins: 15
                anchors.horizontalCenter: parent.horizontalCenter
                columns: 2
                Text {
                    Layout.columnSpan: 2
                    text: dialog.text
                }

                Label {
                    text: qsTr("Комментарий:")
                }
                Item {}

                Text {
                    Layout.columnSpan: 2
                    text: dialog.comment
                }

//                TextField {
//                    Layout.fillWidth: true
//                    id: shortAnswer
//                }

//                GroupBox {
//                    title: qsTr("Было сложно?")
//                    ExclusiveGroup { id: difficulty }
//                    Column {
//                        spacing: 10
//                        RadioButton {
//                            property int value: 3
//                            text: qsTr("АДЪ")
//                            checked: false
//                            exclusiveGroup: difficulty
//                        }
//                        RadioButton {
//                            property int value: 2
//                            text: qsTr("Нормально")
//                            checked: false
//                            exclusiveGroup: difficulty
//                        }
//                        RadioButton {
//                            property int value: 1
//                            text: "Легко"
//                            checked: false
//                            exclusiveGroup: difficulty
//                        }
//                    }
//                }

//                GroupBox {
//                    title: qsTr("Всё нашлось?")
//                    ExclusiveGroup { id: success }
//                    Column {
//                        spacing: 10
//                        RadioButton {
//                            property int value: 3
//                            text: qsTr("Да")
//                            checked: false
//                            exclusiveGroup: success
//                        }
//                        RadioButton {
//                            property int value: 2
//                            text: qsTr("Что-то, но не всё")
//                            checked: false
//                            exclusiveGroup: success
//                        }
//                        RadioButton {
//                            property int value: 1
//                            text: qsTr("Ничего не нашлось!")
//                            checked: false
//                            exclusiveGroup: success
//                        }
//                    }
//                }

//                GroupBox {
//                    title: qsTr("Требовалась доп. информация от клиента?")
//                    Layout.columnSpan: 2
//                    Layout.fillWidth: true
//                    ExclusiveGroup { id: info }
//                    Row {
//                        anchors.centerIn: parent
//                        spacing: 10
//                        RadioButton {
//                            property bool value: true
//                            text: qsTr("Да")
//                            checked: false
//                            exclusiveGroup: info
//                        }
//                        RadioButton {
//                            property bool value: false
//                            text: qsTr("Нет")
//                            checked: false
//                            exclusiveGroup: info
//                        }
//                    }
//                }

//                Item {Layout.fillHeight:true; Layout.columnSpan: 2}
                Button {
                    Layout.alignment: Qt.AlignLeft
                    Layout.preferredWidth: 130
                    action: reject
                }

                Button {
                    focus: true
                    Layout.alignment: Qt.AlignRight
                    Layout.preferredWidth: 130
                    action: accept
                }
            }
        }
        Keys.onEscapePressed: {
            dialog.hide()
        }
    }
}

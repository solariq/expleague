import QtQuick 2.5
import QtQuick.Window 2.2
import QtQuick.Controls 1.4
import QtQuick.Dialogs 1.2
import QtQuick.Layouts 1.1
//import QtWebEngine 1.2

import ExpLeague 1.0

Window {
    id: registerDialog

    property alias current: stack.currentItem
    height: 400
    minimumHeight: height
    maximumHeight: height
    width: 500
    minimumWidth: width
    maximumWidth: width

    modality: Qt.WindowModal
    title: qsTr("Создание профиля лиги")
    Item {
        anchors.fill: parent
        FocusScope {
            anchors.fill: parent
            ColumnLayout {
                anchors.fill: parent
                StackView {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    id: stack
                    initialItem: welcome
                }
                RowLayout {
                    Layout.fillWidth: true
                    Layout.alignment: Qt.AlignRight

                    id: buttons
                    Button {
                        id: back
                        text: qsTr("Назад")
                        onClicked: {
                            stack.currentItem.visible = false
                            stack.pop()
                        }
                        enabled: stack.depth > 1
                        focus: true
                        KeyNavigation.tab: forward
                    }
                    Button {
                        id: forward
                        text: qsTr("Далее")
                        onClicked: {
                            stack.currentItem.visible = false
                            var next = stack.currentItem.next
                            stack.currentItem.go()
                            stack.push(next)
                            next.visible = true
                            next.firstFocus.focus = true
                        }
                        enabled: stack.currentItem && stack.currentItem.next && stack.currentItem.ready
                        KeyNavigation.tab: cancel
                    }
                    Button {
                        id: cancel
                        text: qsTr("Отмена")
                        onClicked: {
                            registerDialog.close()
                            registerDialog.destroy()
                        }
                        KeyNavigation.tab: finish
                    }
                    Button {
                        id: finish
                        text: qsTr("Успех")
                        enabled: stack.currentItem && !stack.currentItem.next && stack.currentItem.ready
                        KeyNavigation.tab: current && stack.currentItem.firstFocus ? stack.currentItem.firstFocus : back
                        onClicked: {
                            root.league.profile = builder.result
                            registerDialog.close()
                            registerDialog.destroy()
                        }
                    }
                }
            }
        }
        Keys.onEscapePressed: {
            registerDialog.close()
            registerDialog.destroy()
        }
    }

    Component.onCompleted: {
        welcome.firstFocus.forceActiveFocus()
    }

    ProfilePreview {
        id: builder
        domain: "localhost"

        onResultChanged: {
            root.league.profilesChanged()
        }
    }

    WizardPage {
        id: welcome
        next: domain
        firstFocus: forward

        content: Label {
            anchors.fill: parent
            text: qsTr("<h3>Регистрация эксперта</h3>\nДля регистрации эксперта нам понадобится рабочий домен и привязка к социальной сети." +
                       " Социальная сеть нам необходима, чтобы узнать Вас немного больше: имя, регион, пол и аватар." +
                       " Эта информация будет использована для представления Вас клиентам лиги. Домен -- это сервер, где вы будете работать." +
                       " В случае неуверенности, просто не меняйте значение этого поля. ")
            wrapMode: Text.WordWrap
            textFormat: Text.RichText
        }
    }

    WizardPage {
        id: domain
        firstFocus: domainField
        ready: domainField.length > 0 && socialField.currentIndex >= 0
        next: social

        go: function() {
            next.url = socialField.model.get(socialField.currentIndex).value
            builder.domain = domainField.text
        }

        content: ColumnLayout {
            anchors.fill: parent
            spacing: 10
            Label {
                Layout.fillWidth: true
                text: "<h3>Где будем создавать</h3>"
                textFormat: Text.RichText
                Layout.alignment: Qt.AlignLeft
            }
            Rectangle {
                Layout.fillWidth: true
                color: "green"
                GridLayout {
                    anchors.fill: parent
                    columns: 2
                    Label {text: "Сервер регистрации"}
                    TextField {
                        id: domainField
                        text: builder.domain
                        anchors.right: parent.right
                        Layout.fillWidth: true
                        KeyNavigation.tab: socialField
                    }
                    Label {text: "Социальная сеть"}
                    ComboBox {
                        id: socialField
                        textRole: "key"
                        currentIndex: 0
                        model: ListModel {
                            ListElement {
                                key: qsTr("ВКонтакте");
                                value: "https://oauth.vk.com/authorize?client_id=5270684&display=popup&scope=offline,wall&response_type=token&lang=ru&v=5.45&state=IntellijIdeaRulezzz&redirect_uri=https://oauth.vk.com/blank.html"
                            }
                        }
                        KeyNavigation.tab: cookiesOff
                    }
                    Label {text: "Отключить cookie"}
                    CheckBox {
                        id: cookiesOff
                        checked: false
                        KeyNavigation.tab: back
                    }
                    Layout.alignment: Qt.AlignCenter
                }
            }
            Item {Layout.fillHeight: true}
        }
    }

    function parseToken(url) {
        var tokenRe = /access_token=([^&]+)/g
        var userRe = /user_id=([^&]+)/g

        var arr = tokenRe.exec(url)
        if (arr != null) {
            builder.vkUser = userRe.exec(url)[1]
            builder.vkToken = arr[1]
            return true
        }
        return false
    }

    WizardPage {
        id: social
        property alias url: cefView.url
        property string token: ""

        firstFocus: cefView
        ready: token.length > 0
        next: preview

        content: CefView {
            id: cefView
            anchors.fill: parent
            webView.onRedirect:{
                if(parseToken(url)){
                    if(cookiesOff.checked){
                        webView.clearCookies("vk.com")
                    }
                    stack.push(preview)
                }
            }
            webView.cookiesEnable: cookiesOff.checked
            webView.allowLinkTtransitions: true
            webView.running: visible
            onVisibleChanged: {
                if(visible){
                    forceActiveFocus()
                }
            }
        }
    }


    WizardPage {
        id: preview
        firstFocus: loginField

        ready: true
        next: result
        go: function () {
            builder.login = loginField.text
            builder.password = passwordField.text
            builder.build()
        }

        content: ColumnLayout {
            Item {Layout.fillHeight: true}
            GridLayout {
                Layout.fillWidth: true
                columns: 2
                Image {
                    id: avatar
                    height: 45
                    source: builder.avatar
                }
                Label {
                    text: builder.name
                    font {
                        bold: true
                        pointSize: 17
                    }
                }
                GroupBox {
                    id: sexField
                    title: qsTr("Пол")
                    RowLayout {
                        RadioButton { text: qsTr("Муж."); checked: builder.sex === Profile.MALE; onClicked: {builder.sex = Profile.MALE}}
                        RadioButton {text: qsTr("Жен."); checked: builder.sex === Profile.FEMALE; onClicked: {builder.sex = Profile.FEMALE}}
                    }
                    KeyNavigation.tab: loginField
                    Layout.columnSpan: 2
                }

                Label { text: qsTr("Логин") }
                TextField { id: loginField; text: builder.login; KeyNavigation.tab: passwordField; Layout.fillWidth: true}
                Label { text: qsTr("Пароль") }
                TextField { id: passwordField; text: builder.password; KeyNavigation.tab: back; Layout.fillWidth: true }
            }
            Item {Layout.fillHeight: true}
        }
    }

    WizardPage {
        id: result
        ready: !!builder.result

        content: Text {
            text: builder.result ? builder.jid + " yспешно зарегистрирован" : (builder.error.length > 0 ? builder.error : "Ожидаем регистрации")
            wrapMode: Text.WordWrap
            width: parent.width
        }
    }
}


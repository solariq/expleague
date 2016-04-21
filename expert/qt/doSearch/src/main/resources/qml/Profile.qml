import QtQuick 2.5
import QtQuick.Window 2.2
import QtQuick.Controls 1.4
import QtQuick.Dialogs 1.2
import QtQuick.Layouts 1.1
import QtWebEngine 1.2

import ExpLeague 1.0

Item {
    property Window register: registerDialog
    property ProfilePreview builder: ProfilePreview {}

    property string jid: ""
    id: profile
    clip: true

    Window {
        property alias current: stack.currentItem
        id: registerDialog
        height: 400
        minimumHeight: height
        maximumHeight: height
        width: 500
        minimumWidth: width
        maximumWidth: width

        modality: Qt.WindowModal
        title: qsTr("Создание профиля лиги")
        Rectangle {
            anchors.fill: parent
            FocusScope {
                anchors.fill: parent
                Rectangle {
                    id: a
                    height: parent.height - b.height
                    anchors {
                        left: parent.left
                        right: parent.right
                        top: parent.top
                    }
                    StackView {
                        anchors.fill: parent
                        id: stack
                        initialItem: welcome
                        Layout.alignment: Qt.AlignCenter
                    }
                }
                Rectangle {
                    id: b
                    anchors {
                        right: parent.right
                        bottom: parent.bottom
                    }
                    width: buttons.width
                    height: buttons.height
                    RowLayout {
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
                                var next = stack.currentItem.next()
                                stack.push(next)
                                next.visible = true
                                next.firstFocus.focus = true
                            }
                            enabled: stack.currentItem != null && stack.currentItem.next() !== null && stack.currentItem.ready()
                            KeyNavigation.tab: cancel
                        }
                        Button {
                            id: cancel
                            text: qsTr("Отмена")
                            onClicked: registerDialog.close()
                            KeyNavigation.tab: finish
                        }
                        Button {
                            id: finish
                            text: qsTr("Успех")
                            enabled: stack.currentItem != null && stack.currentItem.next() === null && stack.currentItem.ready()
                            KeyNavigation.tab: stack.currentItem !== null && stack.currentItem.firstFocus !== null ? stack.currentItem.firstFocus : back
                        }
                    }
                }
            }
        }
    }
    Item {
        id: welcome
        property Item firstFocus: back
        function next() {
            return domain
        }

        function ready() {
            return true
        }

        Rectangle {
            anchors.fill: parent
            Label {
                text: qsTr("<h3>Регистрация эксперта</h3>\nДля регистрации эксперта нам понадобится рабочий домен и привязка к социальной сети." +
                           " Социальная сеть нам необходима, чтобы узнать Вас немного больше: имя, регион, пол и аватар." +
                           " Эта информация будет использована для представления Вас клиентам лиги. Домен -- это сервер, где вы будете работать." +
                           " В случае неуверенности, просто не меняйте значение этого поля. ")
                wrapMode: Text.WordWrap
                textFormat: Text.RichText
                width: parent.width - 120
                x: 100
                y: 20
            }
        }
    }

    Item {
        id: domain
        property Item firstFocus: domainField
        function next() {
            var result = cookiesOff.checked ? socialPrivate : social
            result.url = socialField.model.get(socialField.currentIndex).value
            builder.domain = domainField.text
            return result
        }

        function ready() {
            return domainField.length > 0 && socialField.currentIndex >= 0
        }

        Rectangle {
            width: parent.width - 120
            x: 100
            y: 20

            ColumnLayout {
                anchors.fill: parent
                spacing: 10
                Label {
                    text: "<h3>Где будем создавать</h3>"
                    textFormat: Text.RichText
                    Layout.alignment: Qt.AlignLeft
                }

                GridLayout {
                    columns: 2
                    Label {text: "Сервер регистрации"}
                    TextField {
                        id: domainField
                        text: "expleague.com"
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
        }
    }

    Item {
        id: social
        property Item firstFocus: webView
        property url url;
        property string token: ""

        visible: false

        function next() {
            return preview
        }

        function ready() {
            return this.token.length > 0
        }

        onVisibleChanged: {
            if (visible) {
                webView.url = this.url
            }
        }

        Rectangle {
            anchors.fill: parent
//            width: parent.width - 120
//            x: 100
//            y: 20
//            height: parent - 20

            WebEngineView {
                id: webView
                anchors.fill: parent

                onUrlChanged: {
                    var tokenRe = /access_token=([^&]+)/g
                    var userRe = /user_id=([^&]+)/g

                    var arr = tokenRe.exec(url)
                    if (arr != null) {
                        builder.vkUser = userRe.exec(url)[1]
                        builder.vkToken = arr[1]
                        stack.push(social.next())
                    }
                }
            }
        }
    }

    Item {
        id: socialPrivate
        property Item firstFocus: webView
        property url url;
        property string token: ""

        function next() {
            return preview
        }

        function ready() {
            return this.token.length > 0
        }

        onVisibleChanged: {
            if (visible) {
                webView.url = this.url
            }
        }

        Rectangle {
            anchors.fill: parent

            WebEngineView {
                id: webViewPrivate
                anchors.fill: parent
                url: social.url
                profile: WebEngineProfile {
                    offTheRecord: true
                }

                onUrlChanged: {
                    var tokenRe = /access_token=([^&]+)/g
                    var userRe = /user_id=([^&]+)/g

                    var arr = tokenRe.exec(url)
                    if (arr != null) {
                        builder.vkUser = userRe.exec(url)[1]
                        builder.vkToken = arr[1]
                        stack.push(socialPrivate.next())
                    }
                }
            }
        }
    }

    Item {
        id: preview
        property Item firstFocus: loginField
        function next() {
            return result
        }

        function ready() {
            return true
        }

        GridLayout {
            width: parent.width - 120
            x: 100
            y: 20
            height: parent - 20

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
    }

    Item {
        id: result
        visible: false
        property Profile profile
        property Item firstFocus: back

        function next() {
            return null
        }

        function ready() {
            return this.profile !== null && this.profile.jid.length > 0
        }

        onVisibleChanged: {
            if (visible) {
                console.log("Profile build")
                this.profile = builder.build()
                console.log("finished: " + this.profile)
            }
        }
        Rectangle {
            width: parent.width - 120
            x: 100
            y: 20
            height: parent - 20
            Text {
                text: result.ready() ? result.profile.jid + " yспешно зарегистрирован" : (result.profile !== null && result.profile.error.length > 0 ? result.profile.error : "Ожидаем регистрации")
                wrapMode: Text.WordWrap
                width: parent.width
            }
        }
    }
}

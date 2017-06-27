import QtQuick 2.8
import QtQuick.Layouts 1.1
import QtQuick.Controls 2.1
import QtQuick.Controls.impl 2.1
import QtQuick.Controls.Material 2.1
import QtQuick.Controls 1.4 as Legacy
import QtQuick.Window 2.0

import ExpLeague 1.0
import "."

Rectangle{
    id: self
    property alias webView: cefView.webView
    property alias editor: urlText
    property bool options: false
    property var url: owner.url
    property string pageSearch: ""

    visible: false

    anchors.fill: parent;
    //focus: true;
    onUrlChanged:{
        if(webView.url != url){
            webView.url = url
        }
    }

    onPageSearchChanged: {
        options = pageSearch.length > 0
        webView.findText(pageSearch, true)
    }

    Connections{
        target: webView
        onRedirect:{
            owner.redirect = dosearch.webPage(url.toString())
        }

        onTitleChanged: {
            owner.setTitle(title)
        }

        onIconChanged: {
            owner.setIcon(icon)
        }

        onTextRecieved:{
            owner.text = text
        }

        onLoadEnd:{
            var script = owner.customJavaScript()
            if(script.length != 0){
                webView.executeJS(script)
            }
            webView.getText()
        }
    }

    ColumnLayout {
        anchors.fill: parent
        spacing: 0

        RowLayout {
            id: urlTools
            visible: options
            Layout.maximumHeight: urlText.implicitHeight + 6
            Layout.minimumHeight: urlText.implicitHeight + 6
            Layout.fillWidth: true
            spacing: 3
            //z: parent.z + 1

            property int redirectIndex: 0

            onRedirectIndexChanged: {
                webView.redirectEnable(redirectIndex == 0)
                console.log("redirect", urlTools.redirectIndex, owner.redirects.length, owner.redirects[0].originalUrl)
                webView.url = owner.redirects[urlTools.redirectIndex].originalUrl
            }

            Connections {
                target: owner
                onRedirectsChanged: {
                    console.log("redirect", owner.redirects[urlTools.redirectIndex].originalUrl )
                    urlTools.redirectIndex = 0
                }
            }

            Item {Layout.preferredWidth: 40}
            Label {
                Layout.alignment: Qt.AlignVCenter
                text: "mute:"
            }

            CheckBox {
                id: muteCheck
                Layout.maximumHeight: 15
                Layout.preferredHeight: 15

                Material.theme: Material.System
                indicator: CheckIndicator {
                    x: muteCheck.leftPadding + (muteCheck.availableWidth - width) / 2
                    y: muteCheck.topPadding + (muteCheck.availableHeight - height) / 2
                    width: height
                    height: parent.height
                    control: muteCheck
                }

                checked: true
            }

            Label {
                Layout.alignment: Qt.AlignVCenter
                text: "redirs:"
            }

            Item {Layout.preferredWidth: 3}
            Label {
                Layout.alignment: Qt.AlignVCenter
                text: (owner.redirects.length - urlTools.redirectIndex) + "/" + owner.redirects.length
                color: Palette.activeTextColor
            }
            Item {Layout.preferredWidth: 3}
            Button {
                Layout.alignment: Qt.AlignVCenter
                Layout.preferredWidth: 15
                Layout.preferredHeight: 15
                padding: 3
                focusPolicy: Qt.NoFocus
                indicator: Image {
                    height: 10
                    width: 10
                    anchors.centerIn: parent
                    source: "qrc:/cross.png"
                }
                background: Rectangle {
                    color: Palette.activeColor
                    radius: Palette.radius
                }

                onClicked: {
                    pageSearch = ""
                    owner.reset()
                }
            }
            ColumnLayout {
                Layout.preferredHeight: parent.height - 4
                Layout.alignment: Qt.AlignVCenter
                Layout.preferredWidth: 15
                spacing: 0
                Button {
                    Layout.alignment: Qt.AlignHCenter
                    Layout.preferredHeight: 10
                    Layout.preferredWidth: 15
                    padding: 3
                    focusPolicy: Qt.NoFocus
                    indicator: Image {
                        height: 10
                        width: 10
                        anchors.centerIn: parent
                        source: "qrc:/expand.png"
                    }
                    background: Rectangle {
                        color: Palette.activeColor
                        radius: Palette.radius
                    }
                    enabled: urlTools.redirectIndex < owner.redirects.length - 1
                    onClicked: urlTools.redirectIndex++
                }
                Button {
                    Layout.alignment: Qt.AlignHCenter
                    Layout.preferredHeight: 10
                    Layout.preferredWidth: 15
                    padding: 3
                    focusPolicy: Qt.NoFocus
                    indicator: Image {
                        height: 10
                        width: 10
                        anchors.centerIn: parent
                        rotation: 180
                        source: "qrc:/expand.png"
                    }
                    background: Rectangle {
                        color: Palette.activeColor
                        radius: Palette.radius
                    }
                    enabled: urlTools.redirectIndex > 0
                    onClicked: urlTools.redirectIndex--
                }
            }
            Button {
                Layout.alignment: Qt.AlignVCenter
                Layout.preferredWidth: 15
                Layout.preferredHeight: 15
                padding: 3
                focusPolicy: Qt.NoFocus
                indicator: Image {
                    height: 10
                    width: 10
                    anchors.centerIn: parent
                    source: "qrc:/tools/paste.png"
                }
                background: Rectangle {
                    color: Palette.activeColor
                    radius: Palette.radius
                }

                onClicked: owner.copyToClipboard(urlText.text)
            }

            Item {Layout.preferredWidth: 3}
            Rectangle {
                Layout.preferredHeight: parent.height - 4
                Layout.alignment: Qt.AlignVCenter
                Layout.fillWidth: true
                color: Palette.activeColor
                radius: Palette.radius
                clip: true

                TextEdit {
                    id: urlText
                    anchors.margins: 3
                    anchors.fill: parent
                    readOnly: true
                    selectByMouse: true
                    text: owner.redirects.length > 0 ? owner.redirects[urlTools.redirectIndex].originalUrl : ""
                    color: Palette.activeTextColor
                }
            }
            RowLayout {

                Layout.maximumWidth: 250
                Layout.minimumWidth: 250
                Layout.preferredHeight: parent.height - 4
                Layout.alignment: Qt.AlignVCenter

                spacing: 2
                visible: pageSearch && pageSearch.length > 0
                Rectangle {
                    Layout.fillWidth: true
                    Layout.alignment: Qt.AlignVCenter
                    Layout.preferredHeight: searchText.implicitHeight + 6
                    color: Palette.selectedColor
                    radius: height/2
                    clip: true

                    Flickable {
                        anchors.centerIn: parent
                        width: Math.min(parent.width, searchText.implicitWidth)
                        height: searchText.implicitHeight
                        contentWidth: searchText.implicitWidth
                        contentX: 0
                        contentY: 0
                        Text {
                            id: searchText
                            enabled: false
                            color: Palette.selectedTextColor
                            text: pageSearch
                        }
                    }
                }
                Button {
                    Layout.alignment: Qt.AlignVCenter
                    Layout.preferredWidth: 30
                    padding: 3
                    focusPolicy: Qt.NoFocus

                    indicator: Image {
                        height: 20
                        width: 20
                        anchors.centerIn: parent
                        source: "qrc:/expand.png"
                        rotation: -90
                    }
                    background: Rectangle {
                        color: Palette.activeColor
                        radius: Palette.radius
                    }
                    onClicked: webView.findText(pageSearch, false)
                }
                Button {
                    Layout.alignment: Qt.AlignVCenter
                    Layout.preferredWidth: 30
                    padding: 3
                    focusPolicy: Qt.NoFocus
                    indicator: Image {
                        height: 20
                        width: 20
                        anchors.centerIn: parent
                        source: "qrc:/cross.png"
                    }
                    background: Rectangle {
                        color: Palette.activeColor
                        radius: Palette.radius
                    }

                    onClicked: pageSearch = ""
                }
                Button {
                    Layout.alignment: Qt.AlignVCenter
                    Layout.preferredWidth: 30
                    padding: 3
                    focusPolicy: Qt.NoFocus

                    indicator: Image {
                        height: 20
                        width: 20
                        anchors.centerIn: parent
                        source: "qrc:/expand.png"
                        rotation: 90
                    }
                    background: Rectangle {
                        color: Palette.activeColor
                        radius: Palette.radius
                    }
                    onClicked: webView.findText(pageSearch, true)
                }
                Item {Layout.preferredWidth: 1}
            }

        }
        Item {
            Layout.fillHeight: true
            Layout.fillWidth: true
            CefView{
                id: cefView
                anchors.fill: parent
            }
        }
    }

    property bool escape_pressed_in_search: false
    Keys.onPressed: {

        if (pageSearch.length > 0) {
            if (event.key === Qt.Key_Left) {
                webView.findText(pageSearch, false)
                event.accepted = true
            }
            else if (event.key === Qt.Key_Right) {
                webView.findText(pageSearch, true)
                event.accepted = true
            }
            else if (event.key === Qt.Key_Escape) {
                pageSearch = ""
                event.accepted = true
                escape_pressed_in_search = true
            }
            return
        }
        console.log("pressEvent")
        event.accepted = webView.sendKeyPress(event)
        //event.accepted = webView.sendKeyPress(event.key, event.modifiers, event.text, event.autoRepeat, event.count)
    }

    Keys.onReleased: {
        if (pageSearch.length > 0){
            event.accepted = true
            return
        }
        if (event.key === Qt.Key_Escape && escape_pressed_in_search){
            escape_pressed_in_search = false;
            event.accepted = true
            return
        }
        event.accepted = webView.sendKeyRelease(event)
    }
    onVisibleChanged: {
        if(visible){
            cefView.forceActiveFocus()
        }
    }
    Component.onCompleted: {
        if(visible){
            cefView.forceActiveFocus()
        }
    }
}

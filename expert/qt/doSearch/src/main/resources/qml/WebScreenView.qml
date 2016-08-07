import QtQuick 2.7
import QtQuick.Layouts 1.1
import QtQuick.Controls 2.0
import QtQuick.Controls 1.4 as Legacy
import QtWebEngine 1.3
import QtQuick.Window 2.0

import ExpLeague 1.0
import "."

Item {
    id: self
//    property alias editor: urlText
    property Item myParent
    property alias webView: webEngineView
    property real actionTs: 0
    property bool visited: false
    property string pageSearch: ""

    onPageSearchChanged: {
//        console.log("Looking for " + pageSearch)
        webEngineView.findText(pageSearch)
    }

    anchors.fill: parent

//    onVisibleChanged: {
//        if (!visited && visible)
//            webEngineView.audioMuted = false
//        else
//            webEngineView.audioMuted = true
//        visited = visited || visible
//    }

    ColumnLayout {
        anchors.fill: parent
        RowLayout {
            Layout.maximumHeight: urlText.implicitHeight + 6
            Layout.fillWidth: true
            spacing: 0
            Item {Layout.preferredWidth: 3}
            Rectangle {
                Layout.fillHeight: true
                Layout.fillWidth: true
                color: Palette.selectedColor
                radius: Palette.radius
                clip: true

                TextEdit {
                    id: urlText
                    anchors.margins: 3
                    anchors.fill: parent
                    readOnly: true
                    selectByMouse: true
                    text: webEngineView.url
                    color: Palette.selectedTextColor
                }
            }
            RowLayout {
                Layout.maximumWidth: 250
                Layout.minimumWidth: 250
                Layout.fillHeight: true

                spacing: 2
                visible: pageSearch && pageSearch.length > 0
                Item { Layout.preferredWidth: 30 }
                Rectangle {
                    Layout.fillWidth: true
                    Layout.alignment: Qt.AlignVCenter
                    Layout.preferredHeight: searchText.implicitHeight + 6
                    color: Palette.activeColor
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
                            color: Palette.activeTextColor
                            text: pageSearch
                        }
                    }
                }
                Button {
                    Layout.alignment: Qt.AlignVCenter
                    Layout.preferredWidth: 30
                    padding: 3
                    focusPolicy: Qt.NoFocus
                    indicator: Label {
                        anchors.centerIn: parent
                        text: "x"
                        color: Palette.activeTextColor
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

                    indicator: Label {
                        anchors.centerIn: parent
                        text: "<"
                        color: Palette.activeTextColor
                    }
                    background: Rectangle {
                        color: Palette.activeColor
                        radius: Palette.radius
                    }
                    onClicked: webEngineView.findText(pageSearch, WebEngineView.FindBackward)
                }
                Button {
                    Layout.alignment: Qt.AlignVCenter
                    Layout.preferredWidth: 30
                    padding: 3
                    focusPolicy: Qt.NoFocus

                    indicator: Label {
                        anchors.centerIn: parent
                        text: ">"
                        color: Palette.activeTextColor
                    }
                    background: Rectangle {
                        color: Palette.activeColor
                        radius: Palette.radius
                    }
                    onClicked: webEngineView.findText(pageSearch)
                }
                Item {Layout.preferredWidth: 1}
            }
            Item {Layout.preferredWidth: 3}
        }
        Item { Layout.preferredHeight: 2 }
        Item {
            Layout.fillWidth: true
            Layout.fillHeight: true
            WebEngineView {
                anchors.fill: parent
                id: webEngineView

                property bool newTab: false

                function find(text) {
                    findText(text)
                }

                url: owner.url
                settings.hyperlinkAuditingEnabled: true
                settings.linksIncludedInFocusChain: true
                settings.spatialNavigationEnabled: true
                settings.touchIconsEnabled: true

                onLoadingChanged: {
                    if (!loading) {
                        owner.setTitle(title)
                    }
                }

                onTitleChanged: {
//                    console.log("Page title changed to: " + title + " owner title: " + owner.title)
                    if (title.search(/^https?:\/\//) == -1)
                        owner.setTitle(title)
                }

                onIconChanged: {
                    if (icon) {
                        var iconOriginal = icon.toString()
                        if (iconOriginal.search(/^image:\/\/favicon\//) !== -1) {
                            iconOriginal = iconOriginal.substring("image://favicon/".length)
                            owner.setIcon(iconOriginal)
                        }
                    }
                }

                onUrlChanged: {
                    console.log(new Date().getTime() + " url changed: [" + url + "] owner url: [" + owner.url + "]" + " history length: " + navigationHistory.items.rowCount())

                    if (url != owner.url && url.toString().length > 0 && navigationHistory.items.rowCount() > 1) {
                        var now = new Date().getTime()
                        var delta = now - actionTs
                        if (delta < 5000) { //user action
                            console.log("  User action: " + delta + "")
                            dosearch.navigation.open(url, owner, newTab)
                            goBack()
                            newTab = false
                        }
                        else {
                            console.log("  Redirect: " + delta)
                            owner.redirect = dosearch.web(url)
                        }
                        newTab = false
                    }
                }

                Connections {
                    target: webEngineView.navigationHistory.items
                    onDataChanged: {
                        console.log(new Date().getTime() + " History changed. Length: " + navigationHistory.items.rowCount())
                    }
                }

                settings {
                    autoLoadImages: true
                    javascriptEnabled: true
                    errorPageEnabled: false

                    fullScreenSupportEnabled: false
                    javascriptCanAccessClipboard: true
                    pluginsEnabled: true
                }

                profile.httpUserAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/45.0.2454.101 Safari/537.36"

                onNewViewRequested: {
                    if (!request.userInitiated)
                        print("Warning: Blocked a popup window.")
                    else
                        dosearch.main.openLink(request, owner, request.destination === WebEngineView.NewViewInBackgroundTab)
                }
            }
            TransparentMouseArea {
                anchors.fill: parent
                onPressed: {
                    actionTs = new Date().getTime()
                }
            }
            Keys.forwardTo: dosearch.main
            Keys.onPressed: { // chromium sends us back the keyboard events so to prevent endless loop need to skip this one
                event.accepted = true
                console.log("From chrome: " + event.key + " mod: " + event.modifiers)
            }
        }
    }

    focus: true
    property bool complete: false
    onFocusChanged: {
        console.log("Web screen focus changed to: " + focus)
        if (!complete || self.focus || !dosearch.main)
            return
        if (webEngineView.focus || urlText.focus || searchText.focus) {
            forceActiveFocus()
            return
        }
        var parent = dosearch.main ? dosearch.main.activeFocusItem : null
        while (parent) {
            if (parent === webEngineView) {
                self.forceActiveFocus()
                return
            }
            parent = parent.parent
        }
        if (dosearch.main)
            console.log("Focus given to: " + dosearch.main.activeFocusItem)
    }

    Keys.onPressed: {
        actionTs = new Date().getTime()
        console.log("To chrome: " + event.key + " mod: " + event.modifier)
        if (pageSearch.length > 0) {
            if (event.key === Qt.Key_Left) {
                webEngineView.findText(pageSearch, WebEngineView.FindBackward)
                event.accepted = true
            }
            else if (event.key === Qt.Key_Right) {
                webEngineView.findText(pageSearch)
                event.accepted = true
            }
            else if (event.key === Qt.Key_Escape) {
                pageSearch = ""
                event.accepted = true
            }
        }
        else event.accepted = owner.forwardToWebView(event.key, event.modifiers, event.text, event.autoRepeat, event.count, webEngineView)
    }

//    Component.onDestruction: {
//        webView.triggerWebAction(WebEngineView.RequestClose)
//    }

    onVisibleChanged: {
        if (visible)
            self.forceActiveFocus()
    }
}

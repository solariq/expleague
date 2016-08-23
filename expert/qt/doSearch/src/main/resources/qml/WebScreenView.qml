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
    property alias editor: urlText
    property Item myParent
    property alias webView: webEngineView
    property real actionTs: 0
    property bool visited: false
    property var url: owner.url
    property string pageSearch: ""

    onUrlChanged: {
        if (webEngineView.url != url)
            webEngineView.url = url
    }

    onPageSearchChanged: {
//        console.log("Looking for " + pageSearch)
        webEngineView.findText(pageSearch)
    }

    anchors.fill: parent

    ColumnLayout {
        anchors.fill: parent
        RowLayout {
            Layout.maximumHeight: urlText.implicitHeight + 6
            Layout.fillWidth: true
            spacing: 0
            Item {Layout.preferredWidth: 3}
//            Rectangle {
//                Layout.fillHeight: true
//                Layout.fillWidth: true
//                color: Palette.activeColor
//                radius: Palette.radius
//                clip: true

//                TextEdit {
//                    id: pageId
//                    anchors.margins: 3
//                    anchors.fill: parent
//                    readOnly: true
//                    selectByMouse: true
//                    text: owner.id
//                    color: Palette.activeTextColor
//                }
//            }
//            Item {Layout.preferredWidth: 3}
            Rectangle {
                Layout.fillHeight: true
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
                    text: webEngineView.url
                    color: Palette.activeTextColor
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

                url: "about:blank"
                settings.hyperlinkAuditingEnabled: true
                settings.linksIncludedInFocusChain: true
                settings.spatialNavigationEnabled: true
                settings.touchIconsEnabled: true

                profile: dosearch.main.webProfileRef

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

                property int historyLength: 1
                onUrlChanged: {
                    console.log(new Date().getTime() + " url changed: [" + url + "] owner url: [" + owner.url + "]" + " history length: " + navigationHistory.items.rowCount())

                    if (!owner.accept(url) && navigationHistory.items.rowCount() > historyLength) {
                        var now = new Date().getTime()
                        var delta = now - actionTs
                        if (delta < 1000) { //user action
                            console.log("  User action: " + delta + "")
                            dosearch.navigation.open(url, owner, newTab)
                            historyLength = navigationHistory.items.rowCount()
                        }
                        else {
                            console.log("  Redirect: " + delta)
                            owner.redirect = dosearch.web(url)
                        }
                        newTab = false
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

                onNewViewRequested: {
                    if (!request.userInitiated)
                        print("Warning: Blocked a popup window.")
                    else
                        dosearch.main.openLink(request, owner, request.destination === WebEngineView.NewViewInBackgroundTab)
                }

                property var previousVisibility
                onFullScreenRequested: {
                    if (request.toggleOn) {
                        previousVisibility = dosearch.main.visibility
                        dosearch.main.showFullScreen()
                    }
                    else
                        dosearch.main.visibility = previousVisibility
                    request.accept()
                }

                onRenderProcessTerminated: {
                    var status = ""
                    switch (terminationStatus) {
                    case WebEngineView.NormalTerminationStatus:
                        status = "(normal exit)"
                        break;
                    case WebEngineView.AbnormalTerminationStatus:
                        status = "(abnormal exit)"
                        break;
                    case WebEngineView.CrashedTerminationStatus:
                        status = "(crashed)"
                        break;
                    case WebEngineView.KilledTerminationStatus:
                        status = "(killed)"
                        break;
                    }

                    console.log("Render process exited with code " + exitCode + " " + status)
                    reloadTimer.running = true
                }

                Timer {
                    id: reloadTimer
                    interval: 0
                    running: false
                    repeat: false
                    onTriggered: webEngineView.reload()
                }
            }
            TransparentMouseArea {
                anchors.fill: parent
                onPressed: {
                    actionTs = new Date().getTime()
                }
            }
            Keys.onPressed: { // chromium sends us back the keyboard events so to prevent endless loop need to skip this one
                event.accepted = true
            }
        }
    }

    focus: true
    property bool complete: false
    onFocusChanged: {
        if (!complete || self.focus || !dosearch.main)
            return
        var parent = dosearch.main ? dosearch.main.activeFocusItem : null
        while (parent) {
            if (parent === self) {
                console.log("Enforce focus to self")
                self.forceActiveFocus()
                return
            }
            parent = parent.parent
        }
        if (dosearch.main.activeFocusItem && dosearch.main.activeFocusItem.toString().search("QtWebEngineCore::") !== -1) {
            console.log("Enforce focus to self")
            self.forceActiveFocus()
        }
        else console.log("Focus given to: " + dosearch.main.activeFocusItem)
    }

    Keys.onPressed: {
        console.log("Key pressed: " + event.key)
        actionTs = new Date().getTime()
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

    onVisibleChanged: {
        if (visible)
            forceActiveFocus()
    }

    Component.onDestruction: {
        if (complete)
            webView.triggerWebAction(WebEngineView.RequestClose)
    }

    Component.onCompleted: {
        complete = true
//        forceActiveFocus()
    }
}

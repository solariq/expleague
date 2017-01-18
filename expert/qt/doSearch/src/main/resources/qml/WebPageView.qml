import QtQuick 2.7
import QtQuick.Layouts 1.1
import QtQuick.Controls 2.0
import QtQuick.Controls 1.4 as Legacy
import QtWebEngine 1.3
import QtQuick.Window 2.0

import ExpLeague 1.0
import "."

Rectangle {
    id: self
    property alias editor: urlText
    property Item myParent
    property alias webView: webEngineView
    property real actionTs: 0
    property bool visited: false
    property var url: owner.url
    property string pageSearch: ""
    property bool options: false
    clip: false
    color: Palette.navigationColor
    anchors.fill: parent

    WebEngineProfile {
        id: defaultProfile
    }

    onUrlChanged: {
        if (webEngineView.url != url)
            webEngineView.url = url
    }

    onPageSearchChanged: {
        options = (pageSearch.length > 0)
        webEngineView.findText(pageSearch)
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

            property int redirectIndex: 0

            Connections {
                target: owner
                onRedirectsChanged: {
                    urlTools.redirectIndex = 0
                }
            }

            Item {Layout.preferredWidth: 40}
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

                onClicked: owner.reset()
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
                    onClicked: webEngineView.findText(pageSearch, WebEngineView.FindBackward)
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
                    onClicked: webEngineView.findText(pageSearch)
                }
                Item {Layout.preferredWidth: 1}
            }
            Item {Layout.preferredWidth: 3}
        }
        Item { Layout.preferredHeight: 2; visible: urlTools.visible }
        Item {
            Layout.fillWidth: true
            Layout.fillHeight: true

            WebEngineView {
                anchors.fill: parent
                id: webEngineView
                enabled: !dosearch.main || !dosearch.main.drag

                Drag.onDragStarted: {
                    console.log("Web drag started")
                }

                property bool newTab: false

                function find(text) {
                    findText(text)
                }

                function transfer(shortcut) {
                    return owner.forwardShortcutToWebView(shortcut, webEngineView)
                }

                url: "about:blank"

                profile: !!dosearch.main ? dosearch.main.webProfileRef : defaultProfile

                onTitleChanged: {
                    owner.setTitle(title)
                }

                onIconChanged: {
                    if (icon) {
                        var iconOriginal = icon.toString()
                        if (iconOriginal.search(/^image:\/\/favicon\//) !== -1) {
                            iconOriginal = iconOriginal.substring("image://favicon/".length)
                            owner.setIcon(decodeURIComponent(iconOriginal))
                        }
                    }
                }

                property int historyLength: 1
                onUrlChanged: {
                    console.log(new Date().getTime() + " url changed: [" + url + "] owner url: [" + owner.url + "]" + " history length: " + navigationHistory.items.rowCount())
//                    webEngineView.visible = true
                    if (!owner.accept(url)) {
                        var now = new Date().getTime()
                        var delta = now - actionTs
                        if (delta < 30000 && navigationHistory.items.rowCount() > historyLength) { //user action
                            actionTs = 0
                            console.log("  User action: " + delta + "")
                            owner.open(url, newTab)
                            historyLength = navigationHistory.items.rowCount()
                        }
                        else {
                            console.log("  Redirect: " + delta + " from " + owner.originalUrl + " " + url)
                            owner.redirect = dosearch.webPage(url.toString())
                        }
                        newTab = false
                    }
                }

                settings {
                    autoLoadImages: true
                    javascriptEnabled: true
                    errorPageEnabled: false

                    fullScreenSupportEnabled: true
                    javascriptCanAccessClipboard: true
                    javascriptCanOpenWindows: true
                    pluginsEnabled: true

                    hyperlinkAuditingEnabled: false
                    linksIncludedInFocusChain: false
                    spatialNavigationEnabled: false
                    touchIconsEnabled: true
                }

                onNewViewRequested: {
                    if (!request.userInitiated)
                        print("Warning: Blocked a popup window.")
                    else {
                        actionTs = 0
                        dosearch.main.openLink(request, owner, request.destination === WebEngineView.NewViewInBackgroundTab)

//                        owner.open(request, request.destination === WebEngineView.NewViewInBackgroundTab)
                    }
                }

                property var previousVisibility
                onFullScreenRequested: {
                    if (request.toggleOn) {
                        previousVisibility = dosearch.main.visibility
                        dosearch.main.screenRef.state = "FullScreen"
                        dosearch.main.showFullScreen()
                        urlTools.visible = false
                    }
                    else {
                        dosearch.main.visibility = previousVisibility
                        dosearch.main.screenRef.state = ""
                        urlTools.visible = true
                    }
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

                    console.log("Render process exited with code " + exitCode + " " + status + " at url " + self.url)
                    reloadTimer.running = true
                }

                onLoadingChanged: {
                    self.complete = true
//                    console.log("Loading changed: " + self.url + " to " + loading)
                    if (!loading) {
                        webEngineView.visible = Qt.binding(function() {
//                            console.log(owner.container + " active: " + dosearch.navigation.activePage)
                            return self.visible
                        })
//                        if (owner.container === dosearch.navigation.activePage && self.visible) // for ui transfered views
//                            self.forceActiveFocus()
                        runJavaScript("document.body.innerText", function(result) {
                            owner.text = result;
                        });
                    }
                    else
                        webEngineView.visible = true
                }

                onJavaScriptConsoleMessage: {}

                Timer {
                    id: reloadTimer
                    interval: 0
                    running: false
                    repeat: false
                    onTriggered: webEngineView.reload()
                }
            }

            MouseArea {
                id: browserArea
                drag.target: webEngineView
                anchors.fill: parent
                propagateComposedEvents: true
                cursorShape: webEngineView.cursor
                onPressed: {
                    actionTs = new Date().getTime()
                    mouse.accepted = false
                }
                onReleased: mouse.accepted = false
                onClicked: mouse.accepted = false
                onMouseXChanged: mouse.accepted = false
                onMouseYChanged: mouse.accepted = false
                onDoubleClicked: mouse.accepted = false
                onPressAndHold: mouse.accepted = false
                onPositionChanged: mouse.accepted = false
            }
            DropArea {
                x: (dosearch.main ? dosearch.main.leftMargin : 0)
                y: 0
                width: parent.width - (dosearch.main ? (dosearch.main.rightMargin + dosearch.main.leftMargin): 0)
                height: parent.height

                onEntered: {
                    if (dosearch.main.dragType != "web" && dosearch.main.dragType != "none" && dosearch.main.dragType != "")
                        return
                    if (owner.dragToWebView(drag, webEngineView))
                        drag.accept()
//                    console.log("Entered: " + drag.source.toString())
                    if (dosearch.main.dragType == "" && drag.source && drag.source.toString().search("Main_QMLTYPE") >= 0) {
                        dosearch.main.dragType = "web"
                        dosearch.main.drag = drag.source
                    }
                }
                onExited: {
                    if (dosearch.main.dragType != "web" && dosearch.main.dragType != "none" && dosearch.main.dragType != "")
                        return
                    owner.dragToWebView(drag, webEngineView)
                    if (dosearch.main.dragType != "web")
                        return
//                    console.log("Exited")
                    dosearch.main.dragType == "delay"
                    dosearch.main.delay(100, function () {
                        if (dosearch.main.dragType == "delay") {
                            dosearch.main.dragType = ""
                            dosearch.main.drag = null
                        }
                    })
                }
                onPositionChanged: {
                    if (dosearch.main.dragType != "web" && dosearch.main.dragType != "none" && dosearch.main.dragType != "")
                        return
                    if (owner.dragToWebView(drag, webEngineView))
                        drag.accept()
                }

                onDropped: {
                    if (dosearch.main.dragType != "web" && dosearch.main.dragType != "none" && dosearch.main.dragType != "")
                        return
//                    console.log("Dropped: " + drag)
                    dosearch.main.drag = null
                    dosearch.main.dragType = ""
                    if (owner.dragToWebView(drop, webEngineView))
                        drop.accept()
                }
            }

            Keys.onPressed: { // chromium sends us back the keyboard events so to prevent endless loop need to skip this one
                event.accepted = true
            }
        }
    }

    focus: self.visible
    property bool complete: false

    onFocusChanged: {
        if (!dosearch.main || !visible || !complete || focus)
            return

//        console.log("Focus of " + owner.id + " changed to " + focus + " " + complete  + " active: " + dosearch.main.activeFocusItem)
        var parent = dosearch.main ? dosearch.main.activeFocusItem : null
        while (parent) {
            if (parent === self) {
                console.log("Enforce focus to self " + self + " from child view")
                self.forceActiveFocus()
                return
            }
            parent = parent.parent
        }
        if (!dosearch.main.activeFocusItem || dosearch.main.activeFocusItem.toString().search("QtWebEngineCore::") !== -1) {
            console.log("Enforce focus to self " + self + " from web view " + dosearch.main.activeFocusItem)
            self.forceActiveFocus()
        }
        else {
            if (!focus)
                console.log("Focus given from " + self + " (" + owner + ") to " + dosearch.main.activeFocusItem)
        }
    }

    Keys.onPressed: {
        console.log("Key pressed: " + event.key + " caught by " + owner.id)
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

    property bool prevVisibility: false
    onVisibleChanged: {
        if (prevVisibility == visible)
            return
        console.log("Web page " + owner.id + " visibility changed to: " + visible)
        prevVisibility = visible
    }

    Component.onDestruction: {
        if (complete)
            webView.triggerWebAction(WebEngineView.RequestClose)
    }
}

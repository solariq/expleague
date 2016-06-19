import QtQuick 2.5
import QtQuick.Layouts 1.1
import QtQuick.Controls 1.4
import QtWebEngine 1.2
import QtQuick.Window 2.0

import ExpLeague 1.0

Item {
    id: root
    objectName: "root"
    anchors.fill: parent
    property var owner
    Action {
        shortcut: StandardKey.Refresh
        onTriggered: {
            if (webView)
                webView.reload()
        }
    }
    Action {
        shortcut: StandardKey.AddTab
        onTriggered: {
            tabs.createEmptyTab(webView.profile)
            tabs.currentIndex = tabs.count - 1
            addressBar.forceActiveFocus();
            addressBar.selectAll();
        }
    }
    Action {
        shortcut: StandardKey.Close
        onTriggered: {
            webView.triggerWebAction(WebEngineView.RequestClose);
        }
    }
    Action {
        shortcut: "Ctrl+0"
        onTriggered: webView.zoomFactor = 1.0;
    }
    Action {
        shortcut: StandardKey.ZoomOut
        onTriggered: webView.zoomFactor -= 0.1;
    }
    Action {
        shortcut: StandardKey.ZoomIn
        onTriggered: webView.zoomFactor += 0.1;
    }

    Action {
        shortcut: StandardKey.Copy
        onTriggered: webView.triggerWebAction(WebEngineView.Copy)
    }
    Action {
        shortcut: StandardKey.Cut
        onTriggered: webView.triggerWebAction(WebEngineView.Cut)
    }
    Action {
        shortcut: StandardKey.Paste
        onTriggered: webView.triggerWebAction(WebEngineView.Paste)
    }
    Action {
        shortcut: "Shift+"+StandardKey.Paste
        onTriggered: webView.triggerWebAction(WebEngineView.PasteAndMatchStyle)
    }
    Action {
        shortcut: StandardKey.SelectAll
        onTriggered: webView.triggerWebAction(WebEngineView.SelectAll)
    }
    Action {
        shortcut: StandardKey.Undo
        onTriggered: webView.triggerWebAction(WebEngineView.Undo)
    }
    Action {
        shortcut: StandardKey.Redo
        onTriggered: webView.triggerWebAction(WebEngineView.Redo)
    }
    Action {
        shortcut: StandardKey.Back
        onTriggered: webView.triggerWebAction(WebEngineView.Back)
    }
    Action {
        shortcut: StandardKey.Forward
        onTriggered: webView.triggerWebAction(WebEngineView.Forward)
    }

    SplitView {
        anchors.fill: parent
        orientation: Qt.Horizontal
        Item {
            Layout.minimumWidth: 240
            Rectangle {
                id: queriesContainer
                anchors.top: parent.top
                anchors.left: parent.left
                anchors.right: parent.right
                property int queryRowHeight: 20
                Layout.fillWidth: true
                height: queryList.count * queryRowHeight

                ListView {
                    anchors.fill: parent
                    id: queryList
                    objectName: "queryList"
                    orientation: Qt.Vertical
                    verticalLayoutDirection: ListView.BottomToTop
                    clip: true

                    model: owner ? owner.queries : []
                    delegate: Rectangle {
                        Layout.fillWidth: true
                        height: queriesContainer.queryRowHeight
                        color: index % 2 == 0 ? "#f2f2f2" : "#fafafa"

                        RowLayout {
                            spacing: 3
                            Item {Layout.preferredWidth: 5;}
                            Image {
                                Layout.preferredWidth: 15
                                Layout.preferredHeight: 15
                                z: parent.z + 10
                                source: queryMouseArea.containsMouse ? "qrc:/cross.png" : ""
                                fillMode: Image.PreserveAspectFit
                                MouseArea {
                                    anchors.fill: parent
                                    onClicked: {
                                        owner.wipeQuery(query)
                                    }
                                }
                            }

                            Text {
                                Layout.preferredWidth: queriesContainer.width - 5 - 15 - 3 - 3 - 15 - 5
                                clip: true
                                elide: Text.ElideLeft
                                text: query
                            }
                            Text {
                                Layout.preferredWidth: 15
                                clip: true
                                text: clicks
                            }
                            Item {Layout.preferredWidth: 5;}
                            MouseArea {
                                id: queryMouseArea
                                anchors.fill: parent
                                hoverEnabled: true

                                onClicked: {
                                    owner.search(query);
                                }
                            }
                        }
                    }
                }
            }
        }

        WebEngineView {
            Layout.fillWidth: true
            id: webView
            objectName: "webView"
            url: "http://www.google.com"
            focus: true
            onNewViewRequested: {
                console.log("Search click: " + request.destination)
                if (request.destination === WebEngineView.NewViewInBackgroundTab) {
                    request.openIn(owner.landing(false))
                }
                else {
                    request.openIn(owner.landing(true))
                }
            }
        }
    }
}

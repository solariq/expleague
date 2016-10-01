import QtQuick 2.7
import QtQuick.Layouts 1.1
import QtQuick.Controls 1.4
import QtWebEngine 1.3
import QtQuick.Window 2.0

import ExpLeague 1.0

import "."

Rectangle {
    property var context: dosearch.navigation.context
    id: self
    property WebEngineView webView: google.visible ? google : yandex
    anchors.fill: parent
    color: Palette.backgroundColor("selected")

    Connections {
        target: owner
        onLastRequestChanged: {
            google.queryLoading = true
            google.url = owner.lastRequest.googleUrl

            yandex.queryLoading = true
            yandex.url = owner.lastRequest.yandexUrl
        }
    }

    RowLayout {
        anchors.fill: parent
        spacing: 0
        Rectangle {
            Layout.minimumWidth: 33
            Layout.maximumWidth: 33
            Layout.fillHeight: true
            color: Palette.navigationColor

            ColumnLayout {
                anchors.fill: parent
                spacing: 5
                Item {Layout.preferredWidth: 1}

                ToolbarButton {
                    Layout.preferredHeight: 27
                    Layout.preferredWidth: 27
                    Layout.alignment: Qt.AlignHCenter
                    icon: "qrc:/tools/google.png"
                    onClicked: owner.lastRequest.searchIndex = 0
                    toggle: owner.lastRequest.searchIndex === 0
                }
                ToolbarButton {
                    Layout.preferredHeight: 27
                    Layout.preferredWidth: 27
                    Layout.alignment: Qt.AlignHCenter
                    icon: "qrc:/tools/yandex.png"
                    onClicked: owner.lastRequest.searchIndex = 1
                    toggle: owner.lastRequest.searchIndex === 1
                }

                Item { Layout.fillHeight: true }
            }
        }
        Item {
            Layout.fillWidth: true
            Layout.fillHeight: true
            WebEngineView {
                anchors.fill: parent
                id: google
                url: "about:blank"
                focus: true
                visible: owner.lastRequest.searchIndex === 0
                profile: dosearch.main.webProfileRef
                onNewViewRequested: dosearch.main.openLink(request, owner, request.destination === WebEngineView.NewViewInBackgroundTab)
                property bool queryLoading: true

                onUrlChanged: {
                    if (queryLoading)
                        return
                    var query = owner.lastRequest.parseGoogleQuery(url)
                    if (query.length > 0 && query != owner.lastRequest.query) {
                        console.log("Open new search: " + query)
                        url = owner.lastRequest.googleUrl
                        dosearch.navigation.open(dosearch.search(query, 0))
                    }
                }
                onLoadingChanged: {
                    if (!loading) {
                        queryLoading = false
                        runJavaScript("document.body.innerText", function(result) {
                            owner.lastRequest.googleText = result;
                        });
                    }
                }
            }
            WebEngineView {
                anchors.fill: parent
                id: yandex
                url: "about:blank"
                focus: true
                visible: owner.lastRequest.searchIndex === 1
                profile: dosearch.main.webProfileRef
                onNewViewRequested: dosearch.main.openLink(request, owner, request.destination === WebEngineView.NewViewInBackgroundTab)
                property bool queryLoading: true
                onUrlChanged: {
                    if (queryLoading)
                        return
                    var query = owner.lastRequest.parseYandexQuery(url)
                    if (query.length > 0 && query != owner.lastRequest.query) {
                        console.log("Open new search: " + query)
                        url = owner.lastRequest.yandexUrl
                        dosearch.navigation.open(dosearch.search(query, 1))
                    }
                }
                onLoadingChanged: {
                    if (!loading) {
                        queryLoading = false
                        runJavaScript("document.body.innerText", function(result) {
                            owner.lastRequest.yandexText = result;
                        });
                    }
                }
            }
        }
    }
}

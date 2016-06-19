import QtQuick 2.5

import QtQuick.XmlListModel 2.0

Rectangle {
    id: googleSuggest

    border.color: "darkgray"
    border.width: 2

    property Item textField
    property string textToSugget
    property string dsParam
    property alias list: listView
    property int rowHeight: 20

    signal itemChoosen(string suggestion);

    XmlListModel {
        id: xmlModel
        source: "http://suggestqueries.google.com/complete/search?output=toolbar"+ (dsParam !== "" ? "&ds=" + dsParam : "") + "&q="+textToSugget

        query: "/toplevel/CompleteSuggestion"

        XmlRole { name: "suggestion"; query: "suggestion/@data/string()" }
        XmlRole { name: "num_queries"; query: "num_queries/@int/string()" }
    }

    ListView {
        id: listView
        anchors.fill: parent
        anchors.leftMargin: 2
        anchors.bottomMargin: 2
        anchors.rightMargin: 2
        cacheBuffer: 10
        highlight: Rectangle { color: Qt.rgba(0,0,0,0.1); z: listView.z+10 }
        highlightFollowsCurrentItem: true
        interactive: false
        implicitHeight: xmlModel.count * rowHeight
        currentIndex: -1
        clip: true

        model: xmlModel
        delegate: Rectangle {
            id: resultRow

            width: parent.width
            height: rowHeight
            color: (index % 2 === 0 ? "#ffffff" : "#fafafa")

            Text {
                id: resultPrefix
                anchors.verticalCenter: parent.verticalCenter
                anchors.left: parent.left
                anchors.leftMargin: 5
                font.weight: Font.DemiBold
                font.pixelSize: 14
                text: textToSugget
                clip: true
            }
            Text {
                id: result
                anchors.verticalCenter: parent.verticalCenter
                anchors.left: resultPrefix.right
                anchors.leftMargin: 0
                anchors.right: numberOfResults.left
                anchors.rightMargin: 10
                font.pixelSize: 14
                text: suggestion != null ? suggestion.substring(textToSugget.length) : ""
                clip: true
            }

            Text {
                id: numberOfResults
                anchors.verticalCenter: parent.verticalCenter
                anchors.right: parent.right
                anchors.rightMargin: 5
                font.pixelSize: 12
                font.italic: true
                text: num_queries
            }
        }
        Keys.enabled: true
        Keys.onPressed: {
            var selected = listView.currentIndex >= 0 ? listView.model.get(listView.currentIndex) : ""
            if (event.key === Qt.Key_Enter || event.key === Qt.Key_Return) {
                googleSuggest.visible = false
                itemChoosen(selected.suggestion)
                textField.commit((event.modifiers & (Qt.ControlModifier | Qt.MetaModifier)) != 0)
                event.accepted = true
            }
            else if (event.key === Qt.Key_Down) {
                if (listView.currentIndex < listView.count - 1) {
                    listView.currentIndex++
                }
                event.accepted = true
            }
            else if (event.key === Qt.Key_Escape) {
                listView.currentIndex = -1
                textField.forceActiveFocus()
                event.accepted = true
            }
            else if (event.key === Qt.Key_Up) {
                if (listView.currentIndex > 0) {
                    listView.currentIndex--
                }
                else {
                    listView.currentIndex = -1
                    textField.forceActiveFocus()
                }
                event.accepted = true
            }
            else if (event.key === Qt.Key_Tab || event.key === Qt.Key_Right) {
                var ending = selected.suggestion.substring(googleSuggest.textToSugget.length)
                var index = ending.search(/\w\W/)
                textField.text += index >= 0 ? ending.substring(0, index + 1) : ending
                textField.text += " "
                listView.currentIndex = -1
                textField.forceActiveFocus()
                event.accepted = true
            }
        }

        MouseArea {
            anchors.fill: listView
            onMouseYChanged: {
                listView.currentIndex = Math.floor((mouseY+rowHeight) / rowHeight)- 1;
            }
            onReleased: {
                var selected = listView.model.get(listView.currentIndex)
                itemChoosen(selected.suggestion);
            }
        }
        onFocusChanged: {
            if (focus) {
                listView.forceActiveFocus()
                listView.currentIndex = 0
            }
        }
    }
}

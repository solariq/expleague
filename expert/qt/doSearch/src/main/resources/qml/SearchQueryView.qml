import QtQuick 2.7
import QtQuick.Layouts 1.1
import QtQuick.Controls 1.4
//import QtWebEngine 1.3
import QtQuick.Window 2.0

import ExpLeague 1.0

import "."

Item {
    focus: true
    id: self
    property Item selectedSerp: owner.serps[owner.selected].ui
    //property WebEngineView webView: owner.serps[owner.selected].ui.webView
    anchors.fill: parent

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
                Item {Layout.preferredHeight: 5}

                Repeater {
                    model: owner.serps
                    delegate: ToolbarButton {
                        Layout.preferredHeight: 27
                        Layout.preferredWidth: 27
                        Layout.alignment: Qt.AlignHCenter
                        icon: modelData.icon
                        onClicked: owner.selected = index
                        toggle: owner.selected === index
                    }
                }

                Item { Layout.fillHeight: true }
            }
        }
        Item {
            Layout.fillWidth: true
            Layout.fillHeight: true
            Repeater {
                id: serps
                model: owner.serps
                delegate: Item {
                    id: serp
                    anchors.fill: parent
                    visible: owner.selected === index && self.visible
                    children: [ui]
                    onChildrenChanged: {
                        for (var i in children) {
                            var child = children[i]
                            child.visible = Qt.binding(function () { return owner.selected === index && self.visible })
                        }
                    }
                }
            }
        }
    }
    onActiveFocusChanged: {
        if(activeFocus){
            selectedSerp.forceActiveFocus()
        }
    }
}

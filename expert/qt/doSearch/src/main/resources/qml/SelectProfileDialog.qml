import QtQuick 2.5
import QtQuick.Window 2.2
import QtQuick.Layouts 1.1
import QtQuick.Controls 1.4

Window {
    id: selectProfile

    height: 200
    minimumHeight: height
    maximumHeight: height
    width: 350
    minimumWidth: width
    maximumWidth: width

    modality: Qt.WindowModal
    FocusScope {
        anchors.fill: parent
        ColumnLayout {
            anchors.fill: parent
            Item {Layout.preferredHeight: 15}
            Label {
                Layout.fillWidth: true
                horizontalAlignment: "AlignHCenter"
                font.bold: true
                font.pointSize: 15
                text: qsTr("Выберите профиль")
            }

            Item {Layout.preferredHeight: 5}
            Rectangle {
                Layout.fillHeight: true
                Layout.fillWidth: true
                ComboBox {
                    anchors.margins: 50
                    anchors.left: parent.left
                    anchors.right: parent.right
                    id: profileSelection
                    textRole: "deviceJid"
                    model: root.league.profiles
                }
            }

            RowLayout {
                Layout.fillWidth: true
                Item {Layout.fillWidth: true}
                Button {
                   text: qsTr("Ok")
                   onClicked: {
                       root.league.profile = root.league.profiles[profileSelection.currentIndex]
                       selectProfile.hide()
                   }
                }

                Item {Layout.preferredWidth: 5}

                Button {
                   text: qsTr("Отмена")
                   onClicked: {
                       selectProfile.hide()
                   }
                }

                Item {Layout.fillWidth: true}
            }
            Item {Layout.preferredHeight: 5}
        }
    }
}

import QtQuick 2.5
import QtQuick.Controls 1.4
import QtQuick.Layouts 1.1

Item {
    property alias text: urlField.text

    TextField {
        id: urlField
        anchors.fill: parent
        text: root.location
        selectByMouse: true
        inputMethodHints: Qt.ImhNoPredictiveText

        function commit(tab) {
            root.context.handleOmniboxInput(this.text, tab)
        }

        onTextChanged: {
            if (!focus)
                return
            if (text.length > 2) {
                suggest.textField = this
                suggest.textToSugget = text
                suggest.visible = true
            }
            else {
                suggest.visible = false
            }
        }
        onFocusChanged: {
            if (!focus) {
                if (!suggest.focus)
                    suggest.visible = false
                return
            }
            if (!suggest.visible) {
                selectAll()
            }
            else if (suggest.visible) {
                var text = urlField.text
                urlField.text = ""
                urlField.text = text
            }
        }

        Keys.enabled: true
        Keys.onPressed: {
            if (!focus)
                return

            if (event.key === Qt.Key_Enter || event.key === Qt.Key_Return) {
                focus = false
                commit((event.modifiers & (Qt.ControlModifier | Qt.MetaModifier)) != 0)
                text = Qt.binding(function() {return root.location})
                suggest.visible = false
            }
            else if (event.key == Qt.Key_Down && suggest.visible) {
                suggest.focus = true
            }
            else if (event.key == Qt.Key_Escape && suggest.visible) {
                suggest.visible = false
            }
        }
    }
}

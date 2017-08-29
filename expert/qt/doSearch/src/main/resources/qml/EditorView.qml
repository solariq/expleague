import QtQuick 2.7
import QtQuick.Controls 2.0
import QtQuick.Layouts 1.1
//import QtWebEngine 1.3
import QtGraphicalEffects 1.0

import ExpLeague 1.0

import "."

Item {
    id: self
    anchors.fill: parent
    property var editorActions: !!dosearch.main && dosearch.main.editorActionsRef ? dosearch.main.editorActionsRef : "stubActions"
    property bool showPreview: false

    property alias editor: edit
    //    property bool options: false

    onFocusChanged: {
        if (focus && dosearch.navigation.activePage === owner) {
            edit.forceActiveFocus()
            dosearch.navigation.context.document = owner
        }
    }

    Column {
        visible: true
        anchors.fill: parent
        Rectangle {
            id: buttons
            height: 33
            width: parent.width
            color: Palette.toolsBackground
            property int toolTipDelay: 1000
            RowLayout {
                anchors.centerIn: parent
                height: 27
                width: parent.width - 10
                spacing: 5
                Item {Layout.preferredWidth: 1}
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.makeBold
                    dark: true
                    ToolTip.visible: hovered
                    ToolTip.text: qsTr("ctrl+B")
                    ToolTip.delay: 1000
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.makeItalic
                    dark: true
                    ToolTip.visible: hovered
                    ToolTip.text: qsTr("ctrl+I")
                    ToolTip.delay: 1000
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.insertHeader3
                    dark: true
                    ToolTip.visible: hovered
                    ToolTip.text: qsTr("ctrl+H")
                    ToolTip.delay: 1000
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.insertImage
                    dark: true
                    ToolTip.visible: hovered
                    ToolTip.text: qsTr("ctrl+P")
                    ToolTip.delay: 1000
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.insertLink
                    dark: true
                    ToolTip.visible: hovered
                    ToolTip.text: qsTr("ctrl+L")
                    ToolTip.delay: 1000
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.insertSplitter
                    dark: true
                    ToolTip.visible: hovered
                    ToolTip.text: qsTr("ctrl+-")
                    ToolTip.delay: 1000
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.makeCut
                    dark: true
                    ToolTip.visible: hovered
                    ToolTip.text: qsTr("ctrl+M")
                    ToolTip.delay: 1000
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.insertCitation
                    dark: true
                    ToolTip.visible: hovered
                    ToolTip.text: qsTr("ctrl+J")
                    ToolTip.delay: 1000
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.makeList
                    dark: true
                    ToolTip.visible: hovered
                    ToolTip.text: qsTr("ctrl+0")
                    ToolTip.delay: 1000
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.makeEnumeration
                    dark: true
                    ToolTip.visible: hovered
                    ToolTip.text: qsTr("ctrl+1")
                    ToolTip.delay: 1000
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.insertTable
                    dark: true
                    ToolTip.visible: hovered
                    ToolTip.text: qsTr("ctrl+T")
                    ToolTip.delay: 1000
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.todo
                    dark: true
                    ToolTip.visible: hovered
                    ToolTip.text: qsTr("ctrl+/")
                    ToolTip.delay: 1000
                }
                Item {Layout.fillWidth: true}

            }
        }
        Item {height: 3}

        CefView {
            id: preview
            z: parent.z + 1
            zoom: 0.2
            anchors.centerIn: parent
            width: parent.width - 10
            height: parent.height - 10
            visible: showPreview
            function updateHtml() {
                webView.loadHtml("<!DOCTYPE html><html><head>
                            <script src=\"qrc:///md-scripts.js\"></script>
                            <link rel=\"stylesheet\" href=\"qrc:///markdownpad-github.css\"></head>
                            <body>" + owner.html + "</body></html>")
            }

//            Connections {
//                target: owner
////                onHtmlChanged: {
////                    var focused = dosearch.main.activeFocusItem
////                    var html = preview.updateHtml()
////                    if (focused && focused == edit)
////                        focused.forceActiveFocus()
////                }
//                onVisibleChanged: {
//                    preview.updateHtml()
//                }
//            }
        }

        Rectangle {
            id: editorBox
            width: parent.width
            height: parent.height - buttons.height
            color: "white"
            visible: !showPreview
            Flickable {
                id:  scroll
                anchors.fill: parent
                anchors.margins: 2
                flickableDirection: Flickable.VerticalFlick
                clip: true
                interactive: true
                contentHeight: edit.height

                function ensureVisible(r) {
                    if (contentY >= r.y)
                        contentY = r.y;
                    else if (contentY+height <= r.y+r.height)
                        contentY = r.y+r.height-height;
                }

                TextArea {
                    id: edit
                    width: scroll.width - 8
                    height: Math.max(scroll.height - 8, implicitHeight)
                    focus: true
                    wrapMode: TextEdit.Wrap
                    selectByMouse: true
                    onCursorRectangleChanged: scroll.ensureVisible(cursorRectangle)
                    renderType: Text.NativeRendering
                    font.pointSize: 14
                    selectionColor: "steelBlue"

                    Keys.onPressed: {
                        if (event.key === Qt.Key_V && (event.modifiers & (Qt.ControlModifier | Qt.MetaModifier)) != 0) {
                            event.accepted = true
                            pasteMD()
                        }
                        else if (event.key === Qt.Key_Enter || event.key === Qt.Key_Return) {
                            var lineStart = edit.text.substring(0, edit.cursorPosition).lastIndexOf("\n") + 1
                            var lineEnd = edit.text.indexOf("\n", lineStart)
                            var line = edit.text.substring(lineStart, lineEnd >= 0 ? lineEnd : edit.text.length)
                            var lineCursor = edit.cursorPosition - lineStart
                            var prefix = "\n";
                            if (line.search(/\s*(\*|\d+\.)\s+/) == 0) {
                                var result = line.match(/(\s*)(\*|\d+\.)\s+(\S*)/)
                                if (result[3] != "") {
                                    prefix += result[1]
                                    if (result[2] == "*")
                                        prefix += "* "
                                    else
                                        prefix += (parseInt(result[2]) + 1) + ". "
                                }
                                else {
                                    prefix = ""
                                    editor.remove(lineStart, edit.cursorPosition)
                                }
                            }

                            edit.insert(edit.cursorPosition, prefix)
                            event.accepted = true
                        }
                        else if (event.key === Qt.Key_F2) {
                            var nextTodo = editor.text.substring(edit.cursorPosition + 1).indexOf("TODO")
                            if (nextTodo >= 0) {
                                event.accepted = true
                                editor.cursorPosition += nextTodo + 1
                            }
                            else {
                                var firstTodo = editor.text.indexOf("TODO")
                                if (firstTodo >= 0) {
                                    event.accepted = true
                                    editor.cursorPosition = firstTodo
                                }
                            }
                            if (event.accepted) {
                                editor.selectWord()
                            }
                        }
                    }

                    function pasteMD() {
                        var coded = owner.codeClipboard()
                        edit.remove(edit.selectionStart, edit.selectionEnd)

                        for(var i = 0; i < coded.length; i++) {
                            edit.insert(edit.cursorPosition, coded[i])
                        }
                    }
                }
            }

            DropArea {
                x: (dosearch.main ? dosearch.main.leftMargin : 0)
                y: 0
                width: parent.width - (dosearch.main ? (dosearch.main.rightMargin + dosearch.main.leftMargin): 0)
                height: parent.height
                z: parent.z + 10

                onDropped: {
                    if (drop.hasText && !edit.readOnly) {
                        edit.remove(edit.selectionStart, edit.selectionEnd)
                        edit.insert(editor.cursorPosition, drop.text)
                        drop.accept()
                    }
                }

                onPositionChanged: {
                    editor.cursorPosition = editor.positionAt(scroll.contentX + drag.x, scroll.contentY + drag.y)
                }
            }
        }
    }
}

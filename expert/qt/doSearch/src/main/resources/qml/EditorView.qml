import QtQuick 2.7
import QtQuick.Controls 2.0
import QtQuick.Layouts 1.1
import QtWebEngine 1.3

import ExpLeague 1.0

import "."

Item {
    id: self
    anchors.fill: parent
    property var editorActions: !!dosearch.main && dosearch.main.editorActionsRef ? dosearch.main.editorActionsRef : stubActions

    EditorActions {
        id: stubActions
    }

    property alias editor: edit
//    property bool options: false

    onFocusChanged: {
        if (focus && dosearch.navigation.activePage === owner) {
            edit.forceActiveFocus()
            dosearch.navigation.context.document = owner
        }
    }

//    Component.onCompleted: {
//        preview.updateHtml()
//    }

//    WebEngineView {
//        id: preview
//        visible: false
//        anchors.fill: parent
//        enabled: false

//        function updateHtml() {
//            loadHtml("<!DOCTYPE html><html><head>
//                    <script src=\"qrc:/md-scripts.js\"></script>
//                    <link rel=\"stylesheet\" href=\"qrc:/markdownpad-github.css\"></head>
//                    <body>" + owner.html+ "</body></html>")
//        }

//        Connections {
//            target: owner
//            onHtmlChanged: {
//                var focused = dosearch.main.activeFocusItem
//                var html = preview.updateHtml()
//                if (focused && focused == edit)
//                    focused.forceActiveFocus()
//            }
//        }
//    }

    Column {
        visible: true
        anchors.fill: parent
        Rectangle {
            id: buttons
            height: 33
            width: parent.width
            gradient: Palette.navigationGradient
            RowLayout {
                anchors.centerIn: parent
                height: 27
                width: parent.width - 10
                spacing: 5
                Item {Layout.preferredWidth: 1}
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.makeBold
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.makeItalic
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.insertHeader3
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.insertImage
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.insertLink
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.insertSplitter
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.makeCut
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.insertCitation
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.makeList
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.makeEnumeration
                }
                ToolbarButton {
                    Layout.alignment: Qt.AlignVCenter
                    action: editorActions.insertTable
                }

                Item {Layout.fillWidth: true}
            }
        }
        Item {height: 3}

        Rectangle {
            id: editorBox
            width: parent.width
            height: parent.height - buttons.height
            color: "white"

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

                TextEdit {
                    id: edit
                    width: scroll.width - 8
                    height: Math.max(scroll.height - 8, implicitHeight)
                    focus: true
                    wrapMode: TextEdit.Wrap
                    selectByMouse: true
                    onCursorRectangleChanged: scroll.ensureVisible(cursorRectangle)
                    renderType: Text.NativeRendering
                    font.pointSize: 14

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

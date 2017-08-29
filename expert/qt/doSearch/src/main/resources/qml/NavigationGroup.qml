import QtQuick 2.7
import QtQuick.Controls 2.0
import QtQuick.Layouts 1.1
import QtGraphicalEffects 1.0

import ExpLeague 1.0

import "."

Item {
    id: self
    property PagesGroup group: null
    property var append: null
    property var visiblePages: group.visiblePages
    property var innerVisiblePages: []
    property var activePages: group.activePages
    property var closedPages: group.closedPages
    property bool closeEnabled: true 
    property Page selectedPage: group.selectedPage

    visible: activePages.length > 0
    implicitWidth: visibleList.implicitWidth + (group.parentGroup ? separator.width: 0)

    onSelectedPageChanged: {
        repeater.scrollTo(selectedPage)
        update(false, drop.dropId < 0)
    }

    RowLayout {
        anchors.fill: parent
        spacing: 0
        RowLayout {
            Layout.fillHeight: true
            Layout.preferredWidth: 24
            Layout.minimumWidth: 24
            id: separator
            visible: group.parentGroup
            spacing: 0
            Item {
                Layout.preferredWidth: 8
                visible: self.group.type === PagesGroup.SUGGEST
            }
            Image {
                Layout.alignment: Qt.AlignVCenter
                Layout.preferredHeight: 11
                Layout.preferredWidth: self.group.type === PagesGroup.SUGGEST ? 12 : 20

                source: self.group.type === PagesGroup.SUGGEST ? "qrc:/tools/graph-arrow_suggest.png" : "qrc:/tools/graph-arrow_child.png"
            }
            Item {
                Layout.preferredWidth: 4
            }
        }

        Item {
            Layout.alignment: Qt.AlignVCenter
            Layout.preferredHeight: 22
            Layout.preferredWidth: visibleList.implicitWidth

            Rectangle {
                id: mask
                x: -1
                y: -1
                width: parent.width + 2
                height: parent.height + 2
                color: Palette.borderColor("idle")
                radius: Palette.radius
                smooth: true
            }
            Row {
                id: visibleList
                spacing: 1
                anchors.fill: parent
                Flickable {
                    height: parent.height
                    width: repeater.implicitWidth
                    clip: true
                    Repeater {

                        id: repeater
                        delegate: NavigationTab {
                            id: tab
                            height: visibleList.height
                            width: implicitWidth
                            showTree: true
                            page: modelData
                            state: {
                                if (group.selectedPage !== modelData)
                                    return "idle"
                                else if (selected)
                                    return "selected"
                                else
                                    return "active"
                            }
                            closeEnabled: self.closeEnabled
                            Behavior on x {
                                id: animation
                                PropertyAnimation {
                                    duration: 50
                                }
                            }
                            Connections{
                                target: page
                                onTitleChanged: {
                                    dosearch.navigation.rebalanceWidth()
                                }
                            }

                            property alias animation: animation
                        }

                        property  var savedItemsX: []
                        property int totalWidth: 0
                        property int fullTabsWidth: implicitWidth * (1 - Math.atan(totalWidth - implicitWidth)/(4*Math.PI))


                        model: (innerVisiblePages && innerVisiblePages.length > 0) ? innerVisiblePages : group.activePagesModel
                        onModelChanged: {
                            if(group.type == PagesGroup.SUGGEST){
//                                console.log("suggest model changed. size:", model.rowCount())
                            }
                            updateWidth()
                            update(false, drop.dropId < 0)
                        }


                        function updateWidth(){
                            var newTotalWidth = 0
                            for(var i = 0; i < repeater.count; i++){
                                newTotalWidth += repeater.itemAt(i).width
                            }
                            totalWidth = newTotalWidth
                            implicitWidth = Math.min(group.width, totalWidth)
                        }

                        function update(animate, saveItemsX, wheel){
                            var realWidth = implicitWidth
                            if(wheel){
                                group.scroll -= wheel/totalWidth
                            }
                            var leftx = (totalWidth - fullTabsWidth)*group.scroll
                            var lefty = (realWidth - fullTabsWidth)*group.scroll
                            var rightx = leftx + fullTabsWidth
                            var righty = lefty + fullTabsWidth
                            var x = 0
                            var z = parent.z
                            if(saveItemsX)
                                savedItemsX = []
                            for(var i = 0; i < repeater.count; i++){
                                if(!animate)
                                    itemAt(i).animation.enabled = false
                                if(x < leftx){
                                    itemAt(i).x = x * lefty/leftx
                                }
                                else if(x > rightx){
                                    itemAt(i).x = (x - rightx) * (realWidth - righty)/(totalWidth - rightx) + righty
                                }else{
                                    itemAt(i).x = x - leftx + lefty
                                }
                                if(saveItemsX)
                                    savedItemsX.push(itemAt(i).x)
                                x = x + itemAt(i).width
                                if(!animate)
                                    itemAt(i).animation.enabled = true
                                z += 1
                                itemAt(i).z = z
                            }
                            if(saveItemsX)
                                savedItemsX.push(realWidth)
                        }

                        function scrollTo(page){
//                            console.log("scroll to ", page)
                            if(totalWidth - fullTabsWidth == 0){
                                return
                            }
                            var sum = 0
                            var i = 0
                            for(i = 0; i < repeater.count - 1 && activePages[i] != page; i++){
                                sum += itemAt(i).width;
                            }
                            var leftx = (totalWidth - fullTabsWidth) * group.scroll
                            if(sum < leftx){
                                group.scroll = sum/(totalWidth - fullTabsWidth)
                            }
                            else if(sum  > leftx + fullTabsWidth - itemAt(i).width){
                                group.scroll = (sum - fullTabsWidth + itemAt(i).width)/(totalWidth - fullTabsWidth)
                            }
                            update(true, true)
                        }
                    }

                    Connections{
                        target: group
                        onWidthChanged: {
                            repeater.updateWidth()
                            repeater.update(false, drop.dropId < 0)
                        }
                    }

                    MouseArea{
                        z: -100
                        anchors.fill: parent
                        propagateComposedEvents: true
                        onWheel:{
                            if(drop.dropId < 0)
                                repeater.update(true, true, Math.abs(wheel.angleDelta.x) >  Math.abs(wheel.angleDelta.y) ? wheel.angleDelta.x : wheel.angleDelta.y )
                        }
                    }
                }


                Button {
                    id: others
                    property bool opened: false
                    height: parent.height
                    visible:  self.closedPages.length > 0
                    enabled: !opened
                    focusPolicy : Qt.NoFocus

                    background: Rectangle {
                        Layout.fillHeight: true
                        Layout.fillWidth: true
                        color: popup.visible ? Palette.selectedColor: Palette.idleColor
                    }

                    indicator: ColumnLayout {
                        spacing: 0
                        width: Math.max(count.implicitWidth, 7)
                        height: count.implicitHeight + 4
                        anchors.centerIn: parent
                        anchors.verticalCenterOffset: hiddenCount > 0 ? -2 : 0
                        property int hiddenCount: self.closedPages.length
                        Text {
                            id: count
                            Layout.alignment: Qt.AlignHCenter
                            Layout.preferredWidth: implicitWidth
                            Layout.preferredHeight: implicitHeight
                            text: "" + parent.hiddenCount
                            font.pixelSize: 8
                            visible: parent.hiddenCount > 0

                        }
                        Image {
                            Layout.preferredWidth: 7
                            Layout.preferredHeight: 4
                            Layout.alignment: Qt.AlignHCenter
                            source: "qrc:/tools/rollup-menu-arrow.png"
                        }
                    }
                    property real closedTime: 0
                    onClicked: {
                        var now = new Date().getTime()
                        if (now - closedTime > 200)
                            popup.open()
                    }

                }

                move: Transition {
                    NumberAnimation { properties: "x"; easing.type: Easing.OutBounce }
                }

            }
            DropArea{
                id: drop
                anchors{
                    left: parent.left
                    right: parent.right
                    verticalCenter: parent.verticalCenter
                }
                height: parent.height + 40

                property bool active: false
                property int dropId: -1
                onEntered: {
                    active = dosearch.main.dragType == "page" &&
                            navigation.canMovePage(dosearch.main.drag, group)
                    if(active){
                        innerVisiblePages = activePages
                    }
                }
                onExited: {
                    if(active){
                        active = false
                        dropId = -1
                        innerVisiblePages = []
                    }
                }
                onDropIdChanged: {
                    if(dropId == -1){
                        innerVisiblePages = []
                        return
                    }
                    var pages = []
                    for(var i =0; i < activePages.length; i++){
                        pages.push(activePages[i]);
                    }
                    pages.splice(dropId, 0, dosearch.main.drag)
                    innerVisiblePages = pages

                }

                onPositionChanged: {
                    if(active){
                        var itemX = 0
                        var dragWidth = 0
                        if(drop.dragId >=0)
                            dragWidth = repeater.itemAt(i).x
                        var items = repeater.savedItemsX
                        for(var i = 0; i < items.length - 1; i++){
                            itemX = (items[i] + items[i + 1])/2
                            if(itemX > drag.x){
                                dropId = i
                                return;
                            }
                        }
                        if(itemX - drag.source.width < drag.x){
                            dropId = repeater.count - (repeater.count - 1 == dropId ? 1 : 0)
                        }
                    }
                }
                onDropped: {
                    active = false
                    if(dropId >= 0){
                        dosearch.main.dragType = ""
                        var drag = dosearch.main.drag //qml magic. order is important
                        dosearch.main.drag = null
                        navigation.movePage(drag, group ,dropId)
                        dropId = -1
                    }
                }
            }


            Popup {
                x: Math.max(parent.width - width, 0)
                y: parent.mapFromItem(others.parent, 0, others.y).y + others.height + 1
                width: flickableContainer.contentWidth + 4
                height: Math.min(flickableContainer.contentHeight + 4, dosearch.main.height - 300)
                id: popup
                clip: true
                modal: false
                focus: false
                padding: 2
                closePolicy: Popup.CloseOnEscape | Popup.CloseOnPressOutside

                onVisibleChanged: {
                    if (!visible) {
                        others.closedTime = new Date().getTime()
                    }
                }

                Rectangle {
                    color: Palette.navigationColor
                    anchors.fill: parent
                    Flickable {
                        id: flickableContainer

                        anchors.fill: parent
                        flickableDirection: Flickable.VerticalFlick
                        topMargin: 0
                        rightMargin: 0
                        bottomMargin: 0
                        leftMargin: 0

                        contentHeight: 24 * (/*self.activePages.length*/ + self.closedPages.length) + (closedLabel.visible ? closedLabel.height : 0)
                        contentWidth: {
                            var result = 0
                            for(var i in foldedList.children) {
                                var child = foldedList.children[i]
                                if (!child.visible)
                                    continue
                                result = Math.max(child.implicitWidth, result)
                            }
                            return result
                        }

                        Column {
                            id: foldedList
                            anchors.fill: parent
                            spacing: 0
                            Text {
                                id: closedLabel
                                text: qsTr("Закрытые:")
                                color: Palette.idleTextColor
                                visible: closedPages.length > 0
                            }
                            Repeater {
                                delegate: NavigationTab {
                                    page: modelData
                                    width: flickableContainer.contentWidth
                                    height: 24
                                    state: hover ? "active" : "idle"
                                    textColor: hover ? Palette.selectedTextColor : Palette.idleTextColor
                                    closeEnabled: false
                                }
                                model: self.closedPages
                            }
                        }
                    }
                }
            }

        }
    }
}

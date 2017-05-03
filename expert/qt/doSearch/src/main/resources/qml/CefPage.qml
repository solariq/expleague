import QtQuick 2.0
import ExpLeague 1.0


Rectangle{
    property var url: owner.url
    anchors.fill: parent;

    onUrlChanged:{
        if(webView.url != url){
            webView.url = url
        }
    }

    CefItem {
        id: webView;
        anchors.fill: parent;
        onRequestPage: {
            if(url != this.url){
                owner.open(url, newTab, false)
            }
        }
    }
}

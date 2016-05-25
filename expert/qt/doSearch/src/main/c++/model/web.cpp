#include "web/webfolder.h"
#include "web/websearch.h"
#include "web/webscreen.h"

#include "context.h"

#include <QFile>

namespace expleague {

bool WebFolder::handleOmniboxInput(const QString &text, bool newTab)  {
    QString finalText;
    if (text.startsWith("qrc:") || text.startsWith("file:") || text.startsWith("http:") || text.startsWith("https:") || text.startsWith("ftp:") || text.startsWith("ftps:") || text.startsWith("about:")) { // network protocols
        QUrl url(text);

        if (url.isValid()) {
            openUrl(url, newTab);
            return true;
        }
    }
    QString domain = text.split("/")[0];
    m_text = text;
    m_newTab = newTab;
    m_lookup.setName(domain);
    m_lookup.lookup();
    return true;
}

bool WebScreen::handleOmniboxInput(const QString &text) {
    QUrl url(text);
    m_url.clear();
    if (!url.isValid())
        return false;
    if (url.scheme() == "qrc") {
        m_url = text;
        QFile file(":" + url.path());
        file.open(QFile::ReadOnly);
        QString content = file.readAll();
        QUrlQuery query(url.query());
        QString script;
        script = "<script type=\"text/javascript\">\n";
        for (int i = 0; i < query.queryItems().length(); i++) {
            script += query.queryItems()[i].first + "=" + query.queryItems()[i].second + "\n";
        }
        script += "</script>";
        int bodyIndex = content.indexOf("<body>");
        if (bodyIndex >= 0)
            content.insert(bodyIndex, script);
        else
            content = script + content;
        QVariant returnedValue;
        webView->setProperty("html", content);
    }
    else webView->setProperty("url", url);
    return true;
}

void WebScreen::urlChanged() {
    QString url = webView->property("url").toString();
    if (m_url == url)
        return;
    m_url = url;
    locationChanged(m_url);
    owner()->parent()->visitedUrl(QUrl(url));
}

QQuickItem* WebSearch::landing() {
    WebScreen* screen = owner()->createWebTab();
    m_queries.last()->registerClick(screen);
    return screen->webEngine();
}

QQuickItem* WebScreen::landing() {
    WebScreen* screen = owner()->createWebTab(this);
    return screen->webEngine();
}

QList<SearchRequest*> WebFolder::requests() const {
    WebSearch* search;
    if ((search = qobject_cast<WebSearch*>(at(0)))) {
        return *reinterpret_cast<QList<SearchRequest*>*>(search->queries().data);
    }
    return QList<SearchRequest*>();
}
}

#include "web/webfolder.h"
#include "web/websearch.h"
#include "web/webscreen.h"

#include "context.h"
#include "../dosearch.h"

#include <QFile>
#include <QQuickWindow>

namespace expleague {

bool WebFolder::handleOmniboxInput(const QString &text, bool newTab)  {
    QString finalText;
    if (text.startsWith("site: ")) {
        WebScreen* wscreen = qobject_cast<WebScreen*>(screen());
        if (wscreen) {
            QVariant returnedValue;
            QQuickItem* omnibox = doSearch::instance()->main()->property("omnibox").value<QQuickItem*>();
            QMetaObject::invokeMethod(omnibox, "select",
                Q_RETURN_ARG(QVariant, returnedValue),
                Q_ARG(QVariant, "internet"));
            return handleOmniboxInput(text.mid(strlen("site: ")) + " site:" + QUrl(wscreen->location()).host(), false);
        }
    }
    if (!newTab && screen() && screen()->handleOmniboxInput(text))
        return true;

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

void WebFolder::dnsRequestFinished() {
    if (m_lookup.error() == QDnsLookup::NoError) { // seems to be domain!
        openUrl(QUrl("http://" + m_text), m_newTab);
        return;
    }
    // search fallback
    WebSearch* search;
    if (!(search = dynamic_cast<WebSearch*>(at(0)))) {
        search = new WebSearch(this);
        insert(0, search);
    }

    search->search(m_text);
    search->setActive(true);
}


bool WebScreen::handleOmniboxInput(const QString &text) {
    if (text.startsWith("page: ")) { // search on the page
        QVariant returnedValue;
        QMetaObject::invokeMethod(m_web_view, "find",
            Q_RETURN_ARG(QVariant, returnedValue),
            Q_ARG(QVariant, text.mid(strlen("page: "))));
        return true;
    }

    QUrl url(text);
    if (!url.isValid() || url.scheme().isEmpty())
        return false;
    m_url.clear();
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
        m_web_view->setProperty("html", content);
    }
    else m_web_view->setProperty("url", url);
    return true;
}

void WebScreen::urlChanged() {
    QString url = m_web_view->property("url").toString();
    if (m_url == url)
        return;
    m_url = url;
    locationChanged(m_url);
    owner()->parent()->visitedUrl(QUrl(url));
}

QQuickItem* WebSearch::landing(bool activate) {
    WebScreen* screen = owner()->createWebTab(activate);
    m_queries.last()->registerClick(screen);
    return screen->webView();
}

QQuickItem* WebScreen::landing(bool activate) {
    WebScreen* screen = owner()->createWebTab(activate, this);
    return screen->webView();
}

QList<SearchRequest*> WebFolder::requests() const {
    WebSearch* search;
    if ((search = qobject_cast<WebSearch*>(at(0)))) {
        return *reinterpret_cast<QList<SearchRequest*>*>(search->queries().data);
    }
    return QList<SearchRequest*>();
}
}

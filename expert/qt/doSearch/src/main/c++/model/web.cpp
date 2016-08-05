#include "web.h"

#include "context.h"
#include "../dosearch.h"

#include <QFile>
#include <QQuickWindow>
//#include <QtQuick/private/qquickevents_p_p.h>
//#include <QtWebEngine/private/qquickwebengineview_p.h>
#include <QKeyEvent>
#include <QApplication>

namespace expleague {

QString WebPage::icon() const {
    QVariant var = value("web.favicon");
    return var.isNull() ? site()->icon() : var.toString();
}

void WebPage::setIcon(const QString& icon) {
    store("web.favicon", icon);
    save();
    if (site()->icon().isEmpty())
        site()->setIcon(icon);
    iconChanged(icon);
}

WebSite* WebPage::site() const {
    QUrl siteUrl;
    siteUrl.setHost(m_url.host());
    siteUrl.setPort(m_url.port());
    siteUrl.setScheme(m_url.scheme());
    return static_cast<WebSite*>(parent()->web(siteUrl));
}

QUrl WebPage::url() const {
    return m_redirect ? m_redirect->url() : m_url;
}

void WebPage::setRedirect(WebPage* target) {
    if (m_redirect == target || target == this)
        return;
    store("web.redirect", target ? target->id() : QVariant());
    transition(target, FOLLOW_LINK);
    m_redirect = target;
    save();
    emit redirectChanged();
}

bool WebPage::forwardToWebView(int key,
                               Qt::KeyboardModifiers modifiers,
                               const QString& text,
                               bool autoRepeat,
                               ushort count,
                               QQuickItem *view) {
    QKeyEvent event(QKeyEvent::KeyPress, key, modifiers, text, autoRepeat, count);
    QQuickItem* target = view;
    while (target->isFocusScope()
           && target->scopedFocusItem()
           && target->scopedFocusItem()->isEnabled()) {
        target = target->scopedFocusItem();
    }

    bool rc = QCoreApplication::sendEvent(target, &event);
    return rc;
}

void WebPage::interconnect() {
    Page::interconnect();
    QVariant redirect = value("web.redirect");
    m_redirect = redirect.isNull() ? 0 : dynamic_cast<WebPage*>(parent()->page(redirect.toString()));
}

WebPage::WebPage(const QString& id, const QUrl& url, doSearch* parent): Page(id, "qrc:/WebScreenView.qml", "", parent), m_url(url) {
    store("web.url", m_url.toString());
}

WebPage::WebPage(const QString& id, doSearch* parent): Page(id, "qrc:/WebScreenView.qml", "", parent), m_url(value("web.url").toString()) {
    QVariant redirect = value("web.redirect");
}

//bool WebFolder::handleOmniboxInput(const QString &text, bool newTab)  {
//    QString finalText;
//    if (text.startsWith("site: ")) {
//        WebScreen* wscreen = qobject_cast<WebScreen*>(screen());
//        if (wscreen) {
//            QVariant returnedValue;
//            QQuickItem* omnibox = doSearch::instance()->main()->property("omnibox").value<QQuickItem*>();
//            QMetaObject::invokeMethod(omnibox, "select",
//                Q_RETURN_ARG(QVariant, returnedValue),
//                Q_ARG(QVariant, "internet"));
//            return handleOmniboxInput(text.mid(strlen("site: ")) + " site:" + QUrl(wscreen->location()).host(), false);
//        }
//    }
//    if (!newTab && screen() && screen()->handleOmniboxInput(text))
//        return true;

//    if (text.startsWith("qrc:") || text.startsWith("file:") || text.startsWith("http:") || text.startsWith("https:") || text.startsWith("ftp:") || text.startsWith("ftps:") || text.startsWith("about:")) { // network protocols
//        QUrl url(text);

//        if (url.isValid()) {
//            openUrl(url, newTab);
//            return true;
//        }
//    }
//    QString domain = text.split("/")[0];
//    m_text = text;
//    m_newTab = newTab;
//    m_lookup.setName(domain);
//    m_lookup.lookup();
//    return true;
//}

//void WebFolder::dnsRequestFinished() {
//    if (m_lookup.error() == QDnsLookup::NoError) { // seems to be domain!
//        openUrl(QUrl("http://" + m_text), m_newTab);
//        return;
//    }
//    // search fallback
//    WebSearch* search;
//    if (!(search = dynamic_cast<WebSearch*>(at(0)))) {
//        search = new WebSearch(this);
//        insert(0, search);
//    }

//    search->search(m_text);
//    search->setActive(true);
//}

//bool WebScreen::handleOmniboxInput(const QString &text) {
//    if (text.startsWith("page: ")) { // search on the page
//        QVariant returnedValue;
//        QMetaObject::invokeMethod(m_web_view, "find",
//            Q_RETURN_ARG(QVariant, returnedValue),
//            Q_ARG(QVariant, text.mid(strlen("page: "))));
//        return true;
//    }

//    QUrl url(text);
//    if (!url.isValid() || url.scheme().isEmpty())
//        return false;
//    m_url.clear();
//    if (url.scheme() == "qrc") {
//        m_url = text;
//        QFile file(":" + url.path());
//        file.open(QFile::ReadOnly);
//        QString content = file.readAll();
//        QUrlQuery query(url.query());
//        QString script;
//        script = "<script type=\"text/javascript\">\n";
//        for (int i = 0; i < query.queryItems().length(); i++) {
//            script += query.queryItems()[i].first + "=" + query.queryItems()[i].second + "\n";
//        }
//        script += "</script>";
//        int bodyIndex = content.indexOf("<body>");
//        if (bodyIndex >= 0)
//            content.insert(bodyIndex, script);
//        else
//            content = script + content;
//        QVariant returnedValue;
//        m_web_view->setProperty("html", content);
//    }
//    else m_web_view->setProperty("url", url);
//    return true;
//}

//void WebScreen::urlChanged() {
//    QString url = m_web_view->property("url").toString();
//    if (m_url == url)
//        return;
//    m_url = url;
//    locationChanged(m_url);
//    emit owner()->parent()->visitedUrl(QUrl(url));
//}

//QQuickItem* WebSearch::landing(bool activate) {
//    WebScreen* screen = owner()->createWebTab(activate);
////    m_queries.last()->registerClick(screen);
//    return screen->webView();
//}

//QQuickItem* WebScreen::landing(bool activate) {
//    WebScreen* screen = owner()->createWebTab(activate, this);
//    return screen->webView();
//}

//void WebSearch::googleUrlChanged() {
//    QUrl url = m_google_web_view->property("url").toUrl();
//    locationChanged(url.toString());
//    QUrlQuery query(url.hasFragment() ? url.fragment() : url.query());
//    QString queryText = query.queryItemValue("q");
//    queryText.replace("+", " ");
//    if (queryText.isEmpty() || queryText == m_queries.last()->query())
//        return;
//    search(queryText, 0);
//}

//void WebSearch::yandexUrlChanged() {
//    QUrl url = m_yandex_web_view->property("url").toUrl();
//    locationChanged(url.toString());
//    QUrlQuery query(url.hasFragment() ? url.fragment() : url.query());
//    QString queryText = query.queryItemValue("text");
//    queryText.replace("+", " ");
//    search(queryText, 1);
//}

//void WebSearch::search(const QString &text, int searchIndex) {
//    if (text.isEmpty() || (!m_queries.empty() && text == m_queries.last()->query()))
//        return;
//    SearchRequest* request = 0;
//    SearchRequest* last = 0;
//    if (!m_queries.empty() && m_queries.last()->clicks() == 0) {
//        last = m_queries.last();
//        m_queries.removeLast();
//    }
//    for (int i = 0; i < m_queries.size(); i++) {
//        if (m_queries.at(i)->query() == text) {
//            request = m_queries.at(i);
//            m_queries.removeOne(request);
//        }
//    }

////    m_queries.append(request ? request : new SearchRequest(text, 0, searchIndex, this));
//    queriesChanged();
//    { // update google
//        QUrl googleQuery("https://www.google.ru/search?q=" + QUrl::toPercentEncoding(text));
//        if (m_google_web_view->property("url").toUrl() != googleQuery) {
//            m_google_web_view->setProperty("url", googleQuery);
//        }
//    }
//    { // update yandex
//        QUrl yandexQuery("https://www.yandex.ru/search/?text=" + QUrl::toPercentEncoding(text));
//        if (m_yandex_web_view->property("url").toUrl() != yandexQuery) {
//            m_yandex_web_view->setProperty("url", yandexQuery);
//        }
//    }
//    setSearchIndex(searchIndex);
//    if (last)
//        last->deleteLater();
//    locationChanged(location());
//}

//QList<SearchRequest*> WebFolder::requests() const {
//    WebSearch* search;
//    if ((search = qobject_cast<WebSearch*>(at(0)))) {
//        return *reinterpret_cast<QList<SearchRequest*>*>(search->queries().data);
//    }
//    return QList<SearchRequest*>();
//}
}

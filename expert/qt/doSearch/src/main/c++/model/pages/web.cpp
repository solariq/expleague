#include "web.h"

#include "../context.h"
#include "../../dosearch.h"

#include <QFile>
#include <QQuickWindow>
#include <QtQuick/private/qquickdroparea_p.h>
//#include <QtWebEngine/private/qquickwebengineview_p.h>
#include <QKeyEvent>
#include <QShortcut>
#include <QDropEvent>
#include <QApplication>

namespace expleague {

QString WebPage::icon() const {
    QVariant var = value("web.favicon");
    return var.isNull() ? site()->icon() : var.toString();
}

QString WebPage::title() const {
    if (m_redirect)
        return m_redirect->title();
    QVariant var = value("web.title");
    return var.isNull() ? url().toString() : var.toString();
}

void WebPage::setTitle(const QString& title) {
    if (title.isEmpty())
        return;
    QUrl url(title, QUrl::StrictMode);
    QString currentTitle = this->title();
    QUrl currentTitleUrl(title);
    if (url.isValid() && !(currentTitle.isEmpty() || currentTitleUrl.isValid()))
        return;
    store("web.title", title);
    save();
    titleChanged(title);
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
    else if (m_redirect)
        QObject::disconnect(this, SLOT(onRedirectUrlChanged(QUrl)));
    store("web.redirect", target ? target->id() : QVariant());

    if (target) {
        transition(target, FOLLOW_LINK);
        QObject::connect(target, SIGNAL(urlChanged(QUrl)), this, SLOT(onRedirectUrlChanged(QUrl)));
    }
    m_redirect = target;
    save();
    rebuildRedirects();
    emit redirectChanged();
    emit urlChanged(url());
    emit titleChanged(title());
    if (target)
        site()->addMirror(target->site());
}

bool WebPage::forwardShortcutToWebView(const QString& shortcut, QQuickItem* view) {
    QKeySequence seq(shortcut);
    QQuickItem* target = view;
    while (target->isFocusScope()
           && target->scopedFocusItem()
           && target->scopedFocusItem()->isEnabled()) {
        target = target->scopedFocusItem();
    }
    bool accepted = false;
    for (int i = 0; i < seq.count(); i++) {
        QKeyEvent event(QKeyEvent::KeyPress, seq[i] & 0x00FFFFFF, (Qt::KeyboardModifiers)(seq[i] & 0xFF000000), "", false, 1);
        QCoreApplication::sendEvent(target, &event);
        accepted |= event.isAccepted();
    }
    return accepted;
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

    QCoreApplication::sendEvent(target, &event);
    return event.isAccepted();
}

class QQuickDropEventOpen: public QObject {
    Q_OBJECT
public:
    QQuickDropAreaPrivate *d;
    QDropEvent *event;
};

bool WebPage::dropToWebView(QObject* drop, QQuickItem* view) {
//    QDropEvent event;
    QQuickDropEventOpen* quickDrop = static_cast<QQuickDropEventOpen*>(drop);
    QQuickItem* target = view;
    while (target->isFocusScope()
           && target->scopedFocusItem()
           && target->scopedFocusItem()->isEnabled()) {
        target = target->scopedFocusItem();
    }
    QCoreApplication::sendEvent(view, quickDrop->event);
    return true;
}

void WebPage::setUrl(const QUrl& url) {
    if (url == m_url)
        return;
    m_url = url;
    store("web.url", m_url.toString());
    save();
    emit urlChanged(url);
}

bool WebPage::accept(const QUrl& url) const {
    if (url.scheme().isEmpty())
        return true;
    if (url.scheme() != m_url.scheme())
        return false;
    if (url.host().toLower() != m_url.host().toLower())
        return false;
    if (url.path() != m_url.path())
        return false;
    if (url.query() != m_url.query())
        return false;
    return true;
}

void WebPage::onRedirectUrlChanged(const QUrl& url) {
    rebuildRedirects();
    emit urlChanged(url);
}

void WebPage::interconnect() {
    Page::interconnect();
    QVariant redirect = value("web.redirect");
    if (!redirect.isNull())
        m_redirect = qobject_cast<WebPage*>(parent()->page(redirect.toString()));
    rebuildRedirects();
}

void WebPage::rebuildRedirects() {
    m_redirects.clear();
    WebPage* current = this;
    while (current) {
        m_redirects.insert(0, current);
        current = current->redirect();
    }
}

WebPage::WebPage(const QString& id, const QUrl& url, doSearch* parent):
    Page(id, "qrc:/WebScreenView.qml", parent), m_url(url), m_redirect(0)
{
    store("web.url", m_url.toString());
    save();
}

WebPage::WebPage(const QString& id, doSearch* parent): Page(id, "qrc:/WebScreenView.qml", parent), m_url(value("web.url").toString()) {
}

void WebSite::onMirrorsChanged() {
    WebSite* sender = static_cast<WebSite*>(this->sender());
    QSet<WebSite*> mirrors = sender->mirrors();
    QSet<WebSite*>::iterator iter = mirrors.begin();
    while (iter != mirrors.end()) {
        if (!m_mirrors.contains(*iter))
            addMirror(*iter);
        iter++;
    }
}

void WebSite::interconnect() {
    WebPage::interconnect();
    visitAll("web.site.mirrors", [this](const QVariant& var) {
        WebSite* mirror = qobject_cast<WebSite*>(parent()->page(var.toString()));
        if (mirror) {
            m_mirrors += mirror;
            QObject::connect(mirror, SIGNAL(mirrorsChanged()), this, SLOT(onMirrorsChanged()));
        }
    });
}
}

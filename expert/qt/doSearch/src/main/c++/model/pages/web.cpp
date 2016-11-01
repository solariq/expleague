#include "web.h"

#include "../context.h"
#include "../../dosearch.h"
#include "../../ir/dictionary.h"
#include "../../util/filethrottle.h"

#include <QFile>
#include <QQuickWindow>
#include <QtQuick/private/qquickdroparea_p.h>
#include <QtWebEngine/private/qquickwebenginenewviewrequest_p.h>
#include <QKeyEvent>
#include <QShortcut>
#include <QDropEvent>
#include <QApplication>

#include <QRegularExpression>

namespace expleague {

QUrl WebResource::url() const {
    return redirect() ? redirect()->url() : originalUrl();
}

bool WebResource::rootUrl(const QUrl& url) {
    static QRegularExpression rootUrlRE("^(?:/(?:index\\.\\w+)?)?$");
    bool result = rootUrlRE.match(url.path()).hasMatch();
    return result;
}

bool WebResource::isRoot() const {
    return rootUrl(originalUrl());
}

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

void WebPage::setProfile(const BoW& profile) {
    BoW templates = site()->templates();
    float pagesCount = templates.freq(CollectionDictionary::DocumentBreak);
    QVector<int> indices(profile.size());
    QVector<float> freqs(profile.size());
    for (int i = 0; i < profile.size(); i++) {
        indices[i] = profile.idAt(i);
        freqs[i] = profile.freqAt(i) * (1. - (templates.freq(profile.idAt(i)) + 1.) / (pagesCount + 2.));
    }
    ContentPage::setProfile(BoW(indices, freqs, profile.terms()));
}

Page* WebPage::container() const {
    return isRoot() ? static_cast<Page*>(site()) : static_cast<Page*>(const_cast<WebPage*>(this));
}

Page* WebPage::parentPage() const { return site(); }

WebSite* WebPage::site() const {
    return parent()->webSite(m_url.host());
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
    emit redirectChanged(target);
    emit urlChanged(url());
    emit titleChanged(title());
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
        event.setAccepted(false);
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

void WebPage::setOriginalUrl(const QUrl& url) {
    if (url == this->url())
        return;
    m_url = url;
    store("web.url", url.toString());
    save();
    emit originalUrlChanged(url);
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
//    if (url.query() != m_url.query())
//        return false;
    return true;
}

void WebPage::onRedirectUrlChanged(const QUrl& url) {
    rebuildRedirects();
    emit urlChanged(url);
}

void WebPage::rebuildRedirects() {
    m_redirects.clear();
    WebPage* current = this;
    while (current) {
        m_redirects.insert(0, current);
        current = current->redirect();
    }
}

void WebPage::open(const QUrl& url, bool newTab, bool transferUI) {
    parent()->navigation()->open(url, this, newTab, transferUI);
}

void WebPage::open(QObject* request, bool newTab) {
    qDebug() << request;

    QQuickWebEngineNewViewRequest* nvreq = static_cast<QQuickWebEngineNewViewRequest*>(request);
    if (newTab) {
        nvreq->openIn(0);
        qDebug() << request;
    }
}

WebPage::WebPage(const QString& id, const QUrl& url, doSearch* parent):
    ContentPage(id, "qrc:/WebPageView.qml", parent), m_url(url), m_redirect(0)
{
    store("web.url", m_url.toString());
    save();
}

WebPage::WebPage(const QString& id, doSearch* parent):
    ContentPage(id, "qrc:/WebPageView.qml", parent), m_url(value("web.url").toString())
{}

void WebPage::interconnect() {
    ContentPage::interconnect();
    QVariant redirect = value("web.redirect");
    if (!redirect.isNull())
        m_redirect = qobject_cast<WebPage*>(parent()->page(redirect.toString()));
    rebuildRedirects();
}

void WebSite::onPageLoaded(Page* child) {
    ContentPage* contentPage = qobject_cast<ContentPage*>(child);
    if (contentPage)
        appendPart(contentPage);
    WebPage* webPage = qobject_cast<WebPage*>(child);
    if (webPage)
        connect(webPage, SIGNAL(redirectChanged(WebPage*)), SLOT(onChildRedirectChanged(WebPage*)));
}

void WebSite::onPartProfileChanged(const BoW& oldOne, const BoW& newOne) {
    m_templates = updateSumComponent(m_templates, oldOne.binarize(), newOne.binarize());
    FileWriteThrottle::enqueue(storage().absoluteFilePath("templates.txt"), m_templates.toString());

    CompositeContentPage::onPartProfileChanged(oldOne, newOne);
}

void WebSite::addMirror(WebSite* site) {
    if (m_mirrors.contains(site))
        return;
    m_mirrors += site;
    append("web.site.mirrors", site->id());
    QObject::connect(site, SIGNAL(mirrorsChanged()), this, SLOT(onMirrorsChanged()));
    emit mirrorsChanged();
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

WebSite::WebSite(const QString& id, const QString& /*domain*/, const QUrl& rootUrl, doSearch *parent):
    CompositeContentPage(id, "qrc:/WebSiteView.qml", parent),
    m_root(0)
{
    store("site.root", rootUrl);
}

WebSite::WebSite(const QString &id, doSearch *parent):
    CompositeContentPage(id, "qrc:/WebSiteView.qml", parent),
    m_root(0)
{}

void WebSite::interconnect() {
    QFile templatesFile(storage().absoluteFilePath("templates.txt"));
    if (templatesFile.exists()) {
        templatesFile.open(QFile::ReadOnly);
        m_templates = BoW::fromString(QString::fromUtf8(templatesFile.readAll()), parent()->dictionary());
    }

    CompositeContentPage::interconnect();

    m_root = parent()->webPage(value("site.root").toString());
    visitKeys("web.site.mirrors", [this](const QVariant& var) {
        WebSite* mirror = qobject_cast<WebSite*>(parent()->page(var.toString()));
        if (mirror) {
            m_mirrors += mirror;
            QObject::connect(mirror, SIGNAL(mirrorsChanged()), this, SLOT(onMirrorsChanged()));
        }
    });
    foreach(ContentPage* part, parts()) {
        WebPage* wpage = qobject_cast<WebPage*>(part);
        if (wpage)
            connect(wpage, SIGNAL(redirectChanged(WebPage*)), SLOT(onChildRedirectChanged(WebPage*)));
    }
}
}

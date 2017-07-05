#include "web.h"

#include "../context.h"
#include "../../dosearch.h"
#include "../../ir/dictionary.h"
#include "../../util/filethrottle.h"

#include <QFile>
#include <QQuickWindow>
//#include <QtQuick/private/qquickdroparea_p.h>
//#include <QtWebEngine/private/qquickwebenginenewviewrequest_p.h>
#include <QKeyEvent>
#include <QShortcut>
#include <QDropEvent>
#include <QMimeData>
#include <QApplication>
#include <QClipboard>

#include <QRegularExpression>

namespace expleague {

QUrl WebResource::url() const {
    return redirect() ? redirect()->url() : originalUrl();
}

bool WebResource::rootUrl(const QUrl &url) {
    static QRegularExpression rootUrlRE("^(?:/(?:index\\.\\w+)?)?$");
    bool result = rootUrlRE.match(url.path()).hasMatch();
    return result;
}

bool WebResource::isRoot() const {
    return rootUrl(originalUrl());
}

QString WebPage::icon() const {
    //    QVariant var = value("web.favicon");
    //    return var.isNull() ? site()->icon() : var.toString();
    if (m_redirect) {
        return m_redirect->icon();
    }
    return site()->icon();
}

QString WebPage::title() const {
    if (m_redirect)
        return m_redirect->title();
    QVariant var = value("web.title");
    return var.isNull() ? url().toString() : var.toString();
}

void WebPage::setTitle(const QString &title) {
    if (m_redirect) {
        m_redirect->setTitle(title);
        emit titleChanged(title);
        return;
    }
    if (title.isEmpty())
        return;
    QUrl url(title, QUrl::StrictMode);
    QString currentTitle = this->title();
    QUrl currentTitleUrl(title);
    if (url.isValid() && !(currentTitle.isEmpty() || currentTitleUrl.isValid()))
        return;
    store("web.title", title);
    save();
    emit titleChanged(title);
}

void WebPage::setIcon(const QString& icon) {
    if(m_redirect){
        m_redirect->setIcon(icon);
        emit iconChanged(icon);
        return;
    }
    QUrl url(icon);
    if(!url.isValid()){
        return;
    }
    store("web.favicon", icon);
    save();
    if (site()->icon().isEmpty())
        site()->setIcon(icon);
    iconChanged(icon);
}


Page *WebPage::container() const {
    //return isRoot() ? static_cast<Page*>(site()) : static_cast<Page*>(const_cast<WebPage*>(this)); ???
    return isRoot() ? site() : const_cast<Page *>((Page *) this);
}

bool WebPage::transferUI(UIOwner *other) {
    WebPage *wp = qobject_cast<WebPage *>(other);
    if (!wp)
        return false;
    UIOwner::transferUI(wp);
    wp->containerChanged();
    return true;
}

Page *WebPage::parentPage() const { return site(); }

WebSite *WebPage::site() const {
    return parent()->webSite(m_url.host());
}

void WebPage::setRedirect(WebPage *target) {
    if (m_redirect == target || target == this)
        return;
    else if (m_redirect)
        QObject::disconnect(m_redirect, SIGNAL(urlChanged(QUrl)), this, SLOT(onRedirectUrlChanged(QUrl)));

    m_redirect = target;
    if (target) {
        transition(target, FOLLOW_LINK);
        QObject::connect(target, SIGNAL(urlChanged(QUrl)), this, SLOT(onRedirectUrlChanged(QUrl)));
    }
    rebuildRedirects();
    store("web.redirect", target->id());
    save();
    emit redirectChanged(target);
    emit titleChanged(title());
    emit urlChanged(url());
}

//bool WebPage::forwardShortcutToWebView(const QString& shortcut, QQuickItem* view) {
//    QKeySequence seq(shortcut);
//    QQuickItem* target = view;
//    while (target->isFocusScope()
//           && target->scopedFocusItem()
//           && target->scopedFocusItem()->isEnabled()) {
//        target = target->scopedFocusItem();
//    }
//    bool accepted = false;
//    for (int i = 0; i < seq.count(); i++) {
//        QKeyEvent event(QKeyEvent::KeyPress, seq[i] & 0x00FFFFFF, (Qt::KeyboardModifiers)(seq[i] & 0xFF000000), "", false, 1);
//        event.setAccepted(false);
//        QCoreApplication::sendEvent(target, &event);
//        accepted |= event.isAccepted();
//    }
//    return accepted;
//}
//
//bool WebPage::forwardToWebView(int key,
//                               Qt::KeyboardModifiers modifiers,
//                               const QString& text,
//                               bool autoRepeat,
//                               ushort count,
//                               QQuickItem *view) {
//    QKeyEvent event(QKeyEvent::KeyPress, key, modifiers, text, autoRepeat, count);
//    QQuickItem* target = view;
//    while (target->isFocusScope()
//           && target->scopedFocusItem()
//           && target->scopedFocusItem()->isEnabled()) {
//        target = target->scopedFocusItem();
//    }
//    QCoreApplication::sendEvent(target, &event);
//    return event.isAccepted();
//}
//
//void WebPage::copyToClipboard(const QString& text) const {
//    QApplication::clipboard()->setText(text);
//}
//
//class QQuickDropEventOpen: public QObject {
//    Q_OBJECT
//public:
//    void *d;
//    QDropEvent *event;
//};
//
//bool WebPage::dragToWebView(QQuickDropEvent* dropClosed, QQuickItem* view) const {
//    QQuickDropEventOpen* quickDrop = reinterpret_cast<QQuickDropEventOpen*>(dropClosed);
//    if (quickDrop) {
//        QDropEvent event = *quickDrop->event;
//        QCoreApplication::sendEvent(view, &event);
//    }
//    else QCoreApplication::sendEvent(view, new QDragLeaveEvent());
//    return true;
//}

void WebPage::setOriginalUrl(const QUrl &url) {
    if (url == this->url())
        return;
    m_url = url;
    store("web.url", url.toString());
    save();
    emit originalUrlChanged(url);
    emit urlChanged(url);
}

bool WebPage::accept(const QUrl &url) const {
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

void WebPage::onRedirectUrlChanged(const QUrl &url) {
    rebuildRedirects();
    emit urlChanged(url);
}

void WebPage::rebuildRedirects() {
    m_redirects.clear();
    WebPage *current = this;
    while (current) {
        if (m_redirects.contains(current)) {
            foreach(WebPage *page, m_redirects) {
                page->setRedirect(NULL);
            }
            break;
        }
        m_redirects.insert(0, current);
        current = current->redirect();
    }
}

void WebPage::open(const QUrl &url, bool newTab, bool transferUI) {
    qDebug() << url << "new tab " << newTab;
    parent()->navigation()->open(url, this, newTab, transferUI);
}

void WebPage::open(QObject *request, bool /*newTab*/) {
    qDebug() << request;

    //    QQuickWebEngineNewViewRequest* nvreq = static_cast<QQuickWebEngineNewViewRequest*>(request);
    //    if (newTab) {
    //        nvreq->openIn(0);
    //        qDebug() << request;
    //    }
}

WebPage::WebPage(const QString &id, const QUrl &url, doSearch *parent) :
    #ifdef CEF
    ContentPage(id, "qrc:/CefPage.qml", parent), m_url(url)
  #else
    ContentPage(id, "qrc:/WebPageView.qml", parent), m_url(url)
  #endif
{
    store("web.url", m_url.toString());
    save();
}

WebPage::WebPage(const QString &id, doSearch *parent) :
    #ifdef CEF
    ContentPage(id, "qrc:/CefPage.qml", parent), m_url(value("web.url").toString())
  #else
    ContentPage(id, "qrc:/WebPageView.qml", parent), m_url(value("web.url").toString())
  #endif
{}

void WebPage::interconnect() {
    ContentPage::interconnect();
    QString redirectId = value("web.redirect").toString();
    if(redirectId != ""){
        m_redirect = qobject_cast<WebPage *>(parent()->page(redirectId));
    }
    rebuildRedirects();
}

void WebSite::onPageLoaded(Page *child) {
    ContentPage *contentPage = qobject_cast<ContentPage *>(child);
    if (contentPage)
        appendPart(contentPage);
    WebPage *webPage = qobject_cast<WebPage *>(child);
    if (webPage)
        connect(webPage, SIGNAL(redirectChanged(WebPage * )), SLOT(onChildRedirectChanged(WebPage * )));
}

void WebSite::onPartProfileChanged(const BoW &oldOne, const BoW &newOne) {
    m_templates = updateSumComponent(m_templates, oldOne.binarize(), newOne.binarize());
    FileWriteThrottle::enqueue(storage().absoluteFilePath("templates.txt"), m_templates.toString());

    CompositeContentPage::onPartProfileChanged(oldOne, newOne);
}

void WebSite::addMirror(WebSite *site) {
    if (m_mirrors.contains(site))
        return;
    m_mirrors += site;
    append("web.site.mirrors", site->id());
    QObject::connect(site, SIGNAL(mirrorsChanged()), this, SLOT(onMirrorsChanged()));
    emit mirrorsChanged();
}

void WebSite::onMirrorsChanged() {
    WebSite *sender = static_cast<WebSite *>(this->sender());
    QSet<WebSite *> mirrors = sender->mirrors();
    QSet<WebSite *>::iterator iter = mirrors.begin();
    while (iter != mirrors.end()) {
        if (!m_mirrors.contains(*iter))
            addMirror(*iter);
        iter++;
    }
}

void WebSite::setProfile(const BoW &profile) {
    ContentPage::setProfile(removeTemplates(profile));
}

BoW WebSite::removeTemplates(const BoW &profile) const {
    BoW templates = m_templates;
    float pagesCount = templates.freq(CollectionDictionary::DocumentBreak);
    QVector<int> indices(profile.size());
    QVector<float> freqs(profile.size());
    for (int i = 0; i < profile.size(); i++) {
        indices[i] = profile.idAt(i);
        if (indices[i] >= 0)
            freqs[i] = profile.freqAt(i) * (1. - (templates.freq(profile.idAt(i)) + 1.) / (pagesCount + 2.));
        else
            freqs[i] = profile.freqAt(i);
    }
    return BoW(indices, freqs, profile.terms());
}

WebSite::WebSite(const QString &id, const QString & /*domain*/, const QUrl &rootUrl, doSearch *parent) :
    CompositeContentPage(id, "qrc:/WebSiteView.qml", parent) {
    store("site.root", rootUrl);
    save();
}

WebSite::WebSite(const QString &id, doSearch *parent) :
    CompositeContentPage(id, "qrc:/WebSiteView.qml", parent) {}

void WebSite::interconnect() {
    QFile templatesFile(storage().absoluteFilePath("templates.txt"));
    if (templatesFile.exists()) {
        templatesFile.open(QFile::ReadOnly);
        m_templates = BoW::fromString(QString::fromUtf8(templatesFile.readAll()), parent()->dictionary());
    }

    CompositeContentPage::interconnect();

    m_root = parent()->webPage(value("site.root").toString());
    QObject::connect(m_root, SIGNAL(urlChanged(QUrl)), this, SLOT(onRootUrlChanged(QUrl)));
    visitValues("web.site.mirrors", [this](const QVariant &var) {
        WebSite *mirror = qobject_cast<WebSite *>(parent()->page(var.toString()));
        if (mirror) {
            m_mirrors += mirror;
            QObject::connect(mirror, SIGNAL(mirrorsChanged()), this, SLOT(onMirrorsChanged()));
        }
    });
    foreach(ContentPage *part, parts()) {
        WebPage *wpage = qobject_cast<WebPage *>(part);
        if (wpage)
            connect(wpage, SIGNAL(redirectChanged(WebPage * )), SLOT(onChildRedirectChanged(WebPage * )));
    }
}
}

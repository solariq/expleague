#include "context.h"
#include "../task.h"

#include "search.h"
#include "vault.h"
#include "../dosearch.h"

#include <QImage>
#include <QPainter>
#include <QStyle>

#include <QApplication>
#include <QQmlEngine>
#include <QQmlComponent>

#include <QQuickImageProvider>
#include <QQmlExtensionPlugin>

namespace expleague {

bool isSearch(const QUrl& url) {
    QString host = url.host();
    return host == "www.google.com" || host == "yandex.ru";
}

SearchRequest* Context::lastRequest() const {
    return m_requests.isEmpty() ? &SearchRequest::EMPTY : m_requests.last();
}

QString Context::icon() const {
    if (!m_icon_cache.isEmpty())
        return m_icon_cache;
     m_icon_cache = m_task ? "qrc:/icons/owl.png" : "qrc:/icons/chrome.png";
     foreach(Page* out, outgoing()) {
         if (qobject_cast<SearchRequest*>(out))
             continue;
         if (!out->icon().isEmpty()) {
             m_icon_cache = out->icon();
             break;
         }
     }

     return m_icon_cache;
}

QString Context::title() const { return ""; }

bool Context::hasTask() const {
    return m_task != 0 || !value("context.task").isNull();
}

void Context::setTask(Task *task) {
    QObject::connect(task, SIGNAL(finished()), SLOT(onTaskFinished()));
    QObject::connect(task, SIGNAL(cancelled()), SLOT(onTaskFinished()));
    task->setContext(this);
    store("context.task", task->id());
    m_task = task;
    m_icon_cache = "";
    iconChanged(icon());
}

void Context::transition(Page *from, TransitionType type) {
    Page::transition(from, type);
    if (type == Page::CHILD_GROUP_OPEN) {
        SearchRequest* request = qobject_cast<SearchRequest*>(from);
        if (request) {
            m_requests.append(request);
            requestsChanged();
        }
    }
}

Context::Context(const QString& id, doSearch* parent): Page(id, "qrc:/ContextView.qml", parent) {
    connect(parent->navigation(), SIGNAL(activeScreenChanged()), this, SLOT(onActiveScreenChanged()));
}

void Context::interconnect() {
    Page::interconnect();
    m_vault = new Vault(this);
    QList<Page*> pages;
    for (int i = pages.size() - 1; i >= 0; i--) {
        SearchRequest* request = qobject_cast<SearchRequest*>(pages[i]);
        if (request)
            m_requests.append(request);
    }
}

Context::~Context() {
    if (m_task)
        m_task->setContext(0);
}

void Context::onTaskFinished() {
    parent()->remove(this);
}

void Context::onActiveScreenChanged() {
    NavigationManager* navigation = qobject_cast<NavigationManager*>(sender());
    if (navigation->context() != this)
        return;
    WebPage* webPage;
    if ((webPage = qobject_cast<WebPage*>(navigation->activePage()))) {
        visitedUrl(webPage->url());
    }
}
}

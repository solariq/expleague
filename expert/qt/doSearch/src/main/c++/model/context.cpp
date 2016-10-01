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

SearchSession* Context::match(SearchRequest* request) {
    for (int i = m_sessions.size() - 1; i >= 0; i--) {
        if (m_sessions[i]->check(request)) {
            m_sessions[i]->append(request);
            return m_sessions[i];
        }
    }

    SearchSession* newSession = parent()->session(request, this);
    m_sessions.append(newSession);
    connect(newSession, SIGNAL(queriesChanged()), this, SLOT(onQueriesChanged()));
    return newSession;
}

void Context::transition(Page* to, TransitionType type) {
    Page::transition(to, type);
    SearchSession* session = qobject_cast<SearchSession*>(to);
    if (type == Page::CHILD_GROUP_OPEN && session) {
        emit requestsChanged();
    }
}

Context::Context(const QString& id, doSearch* parent): Page(id, "qrc:/ContextView.qml", parent) {
    connect(parent->navigation(), SIGNAL(activeScreenChanged()), this, SLOT(onActiveScreenChanged()));
}

SearchRequest* Context::lastRequest() const {
    time_t maxTime = 0;
    SearchRequest* last = 0;
    foreach(SearchSession* session, m_sessions) {
        if (session->lastVisitTs() > maxTime) {
            last = session->queries().last();
        }
    }
    return last;
}

void Context::interconnect() {
    Page::interconnect();
    m_vault = new Vault(this);
    visitAll("search.session", [this](Page* session) {
        m_sessions.append(static_cast<SearchSession*>(session));
    });

    foreach(SearchSession* session, m_sessions) {
        connect(session, SIGNAL(queriesChanged()), this, SLOT(onQueriesChanged()));
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

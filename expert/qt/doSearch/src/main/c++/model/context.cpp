#include "context.h"
#include "../task.h"
#include "../dosearch.h"

#include "search.h"
#include "vault.h"
#include "pages/editor.h"
#include "../util/filethrottle.h"

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

bool Context::hasTask() const {
    return m_task != 0 || !value("context.task").isNull();
}

void Context::setName(const QString& name) {
    m_name = name;
    store("context.name", name);
    save();
    emit titleChanged(m_name);
}

void Context::setTask(Task *task) {
    QObject::connect(task, SIGNAL(finished()), SLOT(onTaskFinished()));
    QObject::connect(task, SIGNAL(cancelled()), SLOT(onTaskFinished()));
    task->setContext(this);
    store("context.task", task->id());
    save();
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

QString Context::title() const {
    qDebug() << "Context " << id() << " title: " << m_name;
    return m_name;
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

MarkdownEditorPage* Context::createDocument() {
    Member* self = parent()->league()->findMember(parent()->league()->id());
    QString prefix = "document/" + (self ? self->id() : "local");
    MarkdownEditorPage* document = parent()->document(this, "Документ " + QString::number(children(prefix).size()), self, true);
    appendDocument(document);
    return document;
}


void Context::appendDocument(MarkdownEditorPage* document) {
    if (m_documents.contains(document))
        return;
    m_documents.append(document);
    Page::append("context.document", document->id());
    save();
    emit documentsChanged();
}

void Context::removeDocument(MarkdownEditorPage* document) {
    int index = m_documents.indexOf(document);
    if (document == this->document())
        setActiveDocument(m_documents.size() > 1 ? m_documents[std::max(0, index - 1)] : 0);
    m_documents.removeAt(index);
    if (m_active_document_index >= index)
        m_active_document_index--;
    Page::remove("context.document");
    for (int i = 0; i < m_documents.size(); i++) {
        Page::append("context.document", m_documents[i]->id());
    }
    save();
    emit documentsChanged();
}

void Context::setActiveDocument(MarkdownEditorPage* active) {
    m_active_document_index = m_documents.indexOf(active);
    store("context.active", m_active_document_index >= 0 ? QVariant(active->id()) : QVariant());
    save();
    emit activeDocumentChanged();
}

void Context::onTaskFinished() {
    parent()->remove(this);
}

void Context::onActiveScreenChanged() {
    NavigationManager* navigation = qobject_cast<NavigationManager*>(sender());
    if (navigation->context() != this)
        return;

    WebResource* webPage;
    if ((webPage = dynamic_cast<WebResource*>(navigation->activePage())))
        emit visitedUrl(webPage->url());
    appendPart(qobject_cast<ContentPage*>(navigation->activePage()));
}

Context::Context(const QString& id, const QString& name, doSearch* parent): CompositeContentPage(id, "qrc:/ContextView.qml", parent) {
    connect(parent->navigation(), SIGNAL(activeScreenChanged()), this, SLOT(onActiveScreenChanged()));
    store("context.name", m_name = name);
    save();
}

Context::Context(const QString& id, doSearch* parent): CompositeContentPage(id, "qrc:/ContextView.qml", parent) {
    connect(parent->navigation(), SIGNAL(activeScreenChanged()), this, SLOT(onActiveScreenChanged()));
    m_name = value("context.name").toString();
}

void Context::interconnect() {
    CompositeContentPage::interconnect();
    m_vault = new Vault(this);
    visitChildren("search.session", [this](Page* session) {
        m_sessions.append(static_cast<SearchSession*>(session));
    });

    visitKeys("context.document", [this](const QVariant& value) {
        m_documents.append(static_cast<MarkdownEditorPage*>(parent()->page(value.toString())));
    });

    visitKeys("context.composite", [this](const QVariant& value) {
        auto composite = static_cast<CompositeContentPage*>(parent()->page(value.toString()));
        m_composite_parts.append(composite);
        connect(composite, SIGNAL(partAppended(ContentPage*)), SLOT(onPartAppend(ContentPage*)));
        connect(composite, SIGNAL(partRemoved(ContentPage*)), SLOT(onPartRemoved(ContentPage*)));
        connect(composite, SIGNAL(stateChanged(Page::State)), SLOT(onPartStateChanged(Page::State)));
    });

    QVariant active = value("context.active");
    m_active_document_index = active.isNull() ? -1 : m_documents.indexOf(static_cast<MarkdownEditorPage*>(parent()->page(active.toString())));

    foreach(SearchSession* session, m_sessions) {
        connect(session, SIGNAL(queriesChanged()), this, SLOT(onQueriesChanged()));
    }
}

Context::~Context() {
    if (m_task)
        m_task->setContext(0);
}

}

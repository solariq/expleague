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
     foreach(ContentPage* part, parts()) {
         if (!part || qobject_cast<SearchRequest*>(part) || qobject_cast<MarkdownEditorPage*>(part))
             continue;
         if (!part->icon().isEmpty()) {
             m_icon_cache = part->icon();
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
    iconChanged(icon());
}

SearchSession* Context::match(SearchRequest* request) {
    for (int i = m_sessions.size() - 1; i >= 0; i--) {
        if (m_sessions[i]->check(request)) {
            m_sessions[i]->append(request);
            return m_sessions[i];
        }
    }

    SearchSession* newSession = parent()->session(request);
    m_sessions.append(newSession);
    connect(newSession, SIGNAL(queriesChanged()), this, SLOT(onQueriesChanged()));
    return newSession;
}

void Context::transition(Page* to, TransitionType type) {
    Page::transition(to, type);
    if (type == Page::CHILD_GROUP_OPEN) {
        ContentPage* cpage = qobject_cast<ContentPage*>(to);
        if (cpage)
            appendPart(cpage);
        SearchSession* session = qobject_cast<SearchSession*>(to);
        if (session) {
            if (!m_sessions.contains(session))
                m_sessions.append(session);
            emit requestsChanged();
        }
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
    MarkdownEditorPage* document = parent()->document("Документ " + QString::number(children(prefix).size()), self, true);
    appendDocument(document);
    return document;
}

PagesGroup* Context::associated(Page* page, bool create){
    if (!page)
        return 0;
    auto ptr = m_associations.find(page->id());
    if (ptr != m_associations.end())
        return ptr.value();
    if (!create)
        return 0;
    PagesGroup* result = new PagesGroup(page, PagesGroup::NORMAL, this);
    connect(result, SIGNAL(pagesChanged()), this, SLOT(onGroupPagesChanged()));
    return m_associations[page->id()] = result;
}

PagesGroup* Context::suggest(Page* root) const {
    QList<Page*> pages;
    if (root == this) {
        QList<ContentPage*> parts = this->parts();
        pages = QList<Page*>(*reinterpret_cast<const QList<Page*>*>(&parts));
    }
    else pages = root->outgoing();

    { // build redirects closure
        QSet<Page*> known;
        known += pages.toSet();

        WebResource* webRoot = dynamic_cast<WebResource*>(root);
        while (webRoot) {
            pages.removeOne(webRoot->redirect());
            if (webRoot->isRoot()) {
                foreach(Page* page, webRoot->site()->outgoing()) {
                    if (!known.contains(page)) {
                        pages += page;
                        known += page;
                    }
                }
            }
            else {
                foreach(Page* page, webRoot->page()->outgoing()) {
                    if (!known.contains(page)) {
                        pages += page;
                        known += page;
                    }
                }
            }
            webRoot = webRoot->redirect();
        }
    }
    { // cleanup invisible links
        QList<Page*>::iterator iter = pages.begin();
        while (iter != pages.end()) {
            Page* const page = *iter;
            if (qobject_cast<Context*>(page) || qobject_cast<MarkdownEditorPage*>(page) || qobject_cast<Knugget*>(page))
                iter = pages.erase(iter);
            else iter++;
        }
    }
    PagesGroup* suggest = new PagesGroup(root, PagesGroup::SUGGEST, const_cast<Context*>(this));
    PagesGroup* associated = const_cast<Context*>(this)->associated(root, false);
    if (associated) {
        foreach (Page* page, associated->pages()) {
            if (!associated->closed(page))
                suggest->insert(page);
        }
    }
    for (int i = 0; i < pages.size(); i++) {
        Page* const page = pages[i];
        if (parent()->history()->recent(page) != this)
            continue;
        suggest->insert(page);
    }
    if (associated) {
        foreach (Page* page, associated->pages()) {
            if (associated->closed(page))
                suggest->remove(page);
        }
    }
    return suggest;
}

int depth(PagesGroup* group) {
    PagesGroup* parent = group->parentGroup();
    int depth = 0;
    while (parent) {
        group = parent;
        parent = group->parentGroup();
        depth++;
    }
    return qobject_cast<Context*>(group->root()) ? depth : -1;
}

Context::GroupMatchType Context::match(Page *page, PagesGroup **match) const {
    int minDepth = std::numeric_limits<int>::max();
    WebResource* const web = dynamic_cast<WebResource*>(page);
    GroupMatchType matchType = NONE;
    foreach(PagesGroup* currentGroup, m_associations.values()) {
        WebResource* const webRoot = page ? dynamic_cast<WebResource*>(currentGroup->root()) : 0;
        GroupMatchType currentMatchType = NONE;
        if (currentGroup->pages().contains(page))
            currentMatchType = EXACT;
        else if (webRoot && web && webRoot->site()->mirrorTo(web->site()) && matchType > EXACT)
            currentMatchType = SITE;
        else continue;
        const int currentDepth = depth(currentGroup);
        if (currentDepth >= 0 && (currentMatchType < matchType || currentDepth < minDepth)) {
            *match = currentGroup;
            matchType = currentMatchType;
            minDepth = currentDepth;
        }
    }

    return matchType;
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
    removePart(document);
    emit documentsChanged();
}

void Context::setActiveDocument(MarkdownEditorPage* active) {
    m_active_document_index = m_documents.indexOf(active);
    store("context.active", m_active_document_index >= 0 ? QVariant(active->id()) : QVariant());
    save();
    emit activeDocumentChanged();
}

void Context::onTaskFinished() {
    parent()->remove(this, false);
}

void Context::onGroupPagesChanged() {
    PagesGroup* source = qobject_cast<PagesGroup*>(sender());
    foreach(Page* page, source->pages()) {
        ContentPage* cpage = qobject_cast<ContentPage*>(page);
        if (cpage)
            appendPart(cpage);
    }
}

void Context::onActiveScreenChanged() { // TODO append site acquisition logic
    NavigationManager* navigation = qobject_cast<NavigationManager*>(sender());
    if (navigation->context() != this)
        return;

    WebResource* webPage;
    if ((webPage = dynamic_cast<WebResource*>(navigation->activePage()))) {
//        foreach (ContentPage* part, parts()) {
//            WebResource* webPart = dynamic_cast<WebResource*>(part)
//        }

//        appendPart(qobject_cast<ContentPage*>(webPage->container()));
        emit visitedUrl(webPage->url());
    }
//    else appendPart(qobject_cast<ContentPage*>(navigation->activePage()->container()));
}

void Context::onPartRemoved(ContentPage* part) {
    SearchSession* session = qobject_cast<SearchSession*>(part);
    if (session) {
        m_sessions.removeOne(session);
        emit requestsChanged();
    }
    MarkdownEditorPage* document = qobject_cast<MarkdownEditorPage*>(part);
    if (document) {
        m_documents.removeOne(document);
        emit documentsChanged();
    }
}

void Context::onPartProfileChanged(const BoW& from, const BoW& to) {
    CompositeContentPage::onPartProfileChanged(from, to);
    WebResource* resource = dynamic_cast<WebResource*>(sender());
    if (resource && cos(profile(), to) < m_min_cos) {
        m_icon_cache = resource->site()->icon();
        m_min_cos = cos(profile(), to);
        emit iconChanged(m_icon_cache);
    }
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
    foreach (ContentPage* part, parts()) {
        SearchSession* session = qobject_cast<SearchSession*>(part);
        MarkdownEditorPage* document = qobject_cast<MarkdownEditorPage*>(part);
        Knugget* knugget = qobject_cast<Knugget*>(part);
        WebSite* site = qobject_cast<WebSite*>(part);
        if (session)
            m_sessions.append(session);
        else if (document)
            m_documents.append(document);
        else if (knugget && !knugget->group())
            m_vault->append(knugget);
        else if (site && cos(profile(), site->profile()) < m_min_cos) {
            m_icon_cache = site->icon();
            m_min_cos = cos(profile(), site->profile());
        }
    }

    QVariant active = value("context.active");
    m_active_document_index = active.isNull() ? -1 : m_documents.indexOf(static_cast<MarkdownEditorPage*>(parent()->page(active.toString())));

    foreach(SearchSession* session, m_sessions) {
        connect(session, SIGNAL(queriesChanged()), this, SLOT(onQueriesChanged()));
    }
    connect(this, SIGNAL(partRemoved(ContentPage*)), this, SLOT(onPartRemoved(ContentPage*)));

    {
        QDir storage = this->storage();
        storage.cd("groups");
        foreach(QFileInfo info, storage.entryInfoList()) {
            if (info.isDir() && !info.fileName().startsWith(".") && QFile(info.absoluteFilePath() + "/group.xml").exists()) {
                PagesGroup* group = new PagesGroup(info.fileName(), this);
                m_associations[group->root()->id()] = group;
            }
        }
    }
}

Context::~Context() {
    if (m_task)
        m_task->setContext(0);
}
}

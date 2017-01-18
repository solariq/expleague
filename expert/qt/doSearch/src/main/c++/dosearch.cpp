#include "expleague.h"

#include "ir/dictionary.h"
#include "util/mmath.h"

#include "model/pages/admins.h"

#include <assert.h>

#include <QDir>
#include <QUrl>
#include <QTimer>

#include <QCoreApplication>
#include <QQuickWindow>

expleague::doSearch* root;
namespace expleague {

doSearch::doSearch(QObject* parent) : QObject(parent) {
    QCoreApplication::setOrganizationName("Experts League");

    QCoreApplication::setOrganizationDomain("expleague.com");

#ifdef QT_DEBUG
    QCoreApplication::setApplicationName("doSearch-debug");
#else
    QCoreApplication::setApplicationName("doSearch");
#endif

    QCoreApplication::setApplicationVersion(EL_DOSEARCH_VERSION);

    m_dictionary = new CollectionDictionary(
                QStandardPaths::writableLocation(QStandardPaths::AppLocalDataLocation) + "/dictionary",
                [](const QString& word) { return word; },
                this
    );
    m_saver = new StateSaver(this);
    m_league = new League(this);
    connect(m_league, SIGNAL(roleChanged(League::Role)), this, SLOT(onRoleChanged(League::Role)));
    m_navigation = new NavigationManager(this);
    connect(m_navigation, SIGNAL(activeScreenChanged()), this, SLOT(onActiveScreenChanged()));
    m_history = new History(this);
    QTimer::singleShot(10, m_history, &History::interconnect);
}

void doSearch::setMain(QQuickWindow* main) {
    m_main = main;
    m_navigation->setWindow(main);
    main->setProperty("navigation", QVariant::fromValue(m_navigation));
    emit mainChanged(main);
}

doSearch* doSearch::instance() {
    return &*root;
}

void doSearch::restoreState() {
    QDir contextsDir(pageResource("context"));
    foreach(QString contextFileName, contextsDir.entryList()) {
        if (contextFileName.startsWith("."))
            continue;
        Context* ctxt = qobject_cast<Context*>(page("context/" + contextFileName));
        if (!ctxt->hasTask())
            m_contexts.append(ctxt);
    }
    if (m_contexts.empty())
        createContext("Новый контекст");

    std::sort(m_contexts.begin(), m_contexts.end(), [](const Context* lhs, const Context* rhs){
       return lhs->lastVisitTs() > rhs->lastVisitTs();
    });
    contextsChanged();
    m_saver->restoreState(this);
}

QString doSearch::pageResource(const QString &id) const {
//    qDebug() << "page " << id << " location " << QStandardPaths::writableLocation(QStandardPaths::AppLocalDataLocation) + "/pages/" + id;
    return QStandardPaths::writableLocation(QStandardPaths::AppLocalDataLocation) + "/pages/" + id;
}

class EmptyPage: public Page {
public:
    EmptyPage(const QString& id, doSearch* parent): Page(id, "qrc:/EmptyView.qml", parent){}
protected:
    void interconnect() {}
};

Page* doSearch::empty() const {
    return page("empty", [](const QString& id, doSearch* parent){
        return new EmptyPage(id, parent);
    });
}

Page* doSearch::web(const QUrl& url) const {
    QString query = url.query().isEmpty() ? "" : "/" + md5(url.query());
    if (WebResource::rootUrl(url)) { // site
        WebSite* result = webSite(url.host());
        result->page()->setOriginalUrl(url);
        return result;
    }
    else if (url.host().contains("google.") && url.path() == "/search") {
        return page("search/" + GoogleSERPage::parseQuery(url) + "/google", [&url](const QString& id, doSearch* parent) {
            return new GoogleSERPage(id, url, parent);
        });
    }
    else if (url.host().contains("yandex.") && (url.path() == "/search/" || url.path() == "/yandsearch")) {
        return page("search/" + YandexSERPage::parseQuery(url) + "/yandex", [&url](const QString& id, doSearch* parent) {
            return new YandexSERPage(id, url, parent);
        });
    }
    else return webPage(url);
}

WebSite* doSearch::webSite(const QString& domain) const {
    QString edomain = domain;
    if (edomain.startsWith("www."))
        edomain = edomain.mid(4);
    QString id = "web/" + edomain + "/site";
    return qobject_cast<WebSite*>(page(id, [&edomain, domain](const QString& id, doSearch* parent){
        return new WebSite(id, edomain, QUrl("http://" + domain), parent);
    }));
}

WebPage* doSearch::webPage(const QUrl& url) const {
    QString query = url.query().isEmpty() ? "" : "/" + md5(url.query());
    QString domain = url.host();
    if (domain.startsWith("www."))
        domain = domain.mid(4);
    QString id = "web/" + domain + "/" + url.scheme() + (url.path().isEmpty() || url.path() == "/" ? "/index.html" : url.path()) + query;
    if (id.endsWith('/'))
        id = id.mid(0, -2);
    WebPage* result = qobject_cast<WebPage*>(page(id, [&url](const QString& id, doSearch* parent) {
        return new WebPage(id, url, parent);
    }));
    result->setOriginalUrl(url);
    return result;
}


QString doSearch::nextId(const QString& prefix) const {
    QString id;

    do {
        id = prefix;
        id += (prefix.endsWith('/') ? "" : "/") + randString(10);
    }
    while (QFile(pageResource(id)).exists());
    return id;
}

SearchSession* doSearch::session(SearchRequest* seed) const {
    return static_cast<SearchSession*>(page(nextId("search/session"), [seed](const QString& id, doSearch* parent){
        return new SearchSession(id, seed, parent);
    }));
}

Context* doSearch::context(const QString& id, const QString& name) const {
    return static_cast<Context*>(page(id, [name](const QString& id, doSearch* parent){
        return new Context(id, name, parent);
    }));
}

SearchRequest* doSearch::search(const QString& query, int searchIndex) const {
    QString id = "search/" + query;
    SearchRequest* request = static_cast<SearchRequest*>(page(id, [query, searchIndex, this](const QString& id, doSearch* parent){
        SearchRequest* instance = new SearchRequest(id, query, parent);
        return instance;
    }));
    if (searchIndex >= 0)
        request->select(searchIndex);
    return request;
}

MarkdownEditorPage* doSearch::document(Context* context, const QString& title, Member* author, bool editable, const QString& explicitId) const {
    QString id = "document/" + (author ? author->id() : "local") + "/";
    if (explicitId.isEmpty())
        id = nextId(id);
    else
        id += explicitId;
    return static_cast<MarkdownEditorPage*>(page(id, [title, author, context, editable](const QString& id, doSearch* parent){
        return new MarkdownEditorPage(id, context, author, title, editable, parent);
    }));
}

Context* doSearch::createContext(const QString& name) {
    Context* instance = context(nextId("context/"), name);
    append(instance);
    emit contextsChanged();
    return instance;
}

Page* doSearch::page(const QString &id) const {
    if (id.isEmpty())
        return 0;
    return page(id, [this](const QString& id, doSearch* parent) -> Page* {
        if (id.startsWith("context/"))
            return new Context(id, parent);
        else if (id == AdminContext::ID)
            return new AdminContext(parent);
        else if (id == GlobalChat::ID)
            return new GlobalChat(qobject_cast<AdminContext*>(page(AdminContext::ID)));
        else if (id.startsWith("web/") && id.endsWith("site"))
            return new WebSite(id, parent);
        else if (id.startsWith("web/"))
            return new WebPage(id, parent);
        else if (id.startsWith("search/session"))
            return new SearchSession(id, parent);
        else if (id.startsWith("search/") && id.endsWith("/google"))
            return new GoogleSERPage(id, parent);
        else if (id.startsWith("search/") && id.endsWith("/yandex"))
            return new YandexSERPage(id, parent);
        else if (id.startsWith("search/"))
            return new SearchRequest(id, parent);
        else if (id.startsWith("document/"))
            return new MarkdownEditorPage(id, parent);
        else if (id.startsWith("knugget/text"))
            return new TextKnugget(id, parent);
        else if (id.startsWith("knugget/image"))
            return new ImageKnugget(id, parent);
        else if (id.startsWith("knugget/link"))
            return new LinkKnugget(id, parent);
        else if (id.startsWith("knugget/group"))
            return new GroupKnugget(id, parent);
        else if (id == "empty")
            return empty();
        else {
            qWarning() << "Unknown page type, or corrupted page id: " << id;
            return 0;
        }
    });
}

Page* doSearch::page(const QString& id, std::function<Page* (const QString& id, doSearch* parent)> factory) const {
    if (m_pages.contains(id))
        return m_pages[id];
    Page* page = factory(id, const_cast<doSearch*>(this));
    m_pages[id] = page;
    page->interconnect();
    return page;
}

void doSearch::onActiveScreenChanged() {
    Page* active = m_navigation->activePage();
    if (m_main)
        m_main->setTitle(active->title());
    m_history->onVisited(active, m_navigation->context());
}

void doSearch::onRoleChanged(League::Role role) {
    switch(role) {
    case League::ADMIN: {
        Context* adminContext = qobject_cast<Context*>(page(AdminContext::ID));
        append(adminContext, 0);
        navigation()->activate(adminContext);
        break;
    }
    default: {
        foreach (Context* ctxt, m_contexts) {
            if(qobject_cast<AdminContext*>(ctxt)) {
                remove(ctxt);
                break;
            }
        }
    }
    }
}

void doSearch::append(Context* context, int index) {
    assert(context->parent() == this);
    if (index < 0)
        m_contexts.append(context);
    else
        m_contexts.insert(index, context);
    emit contextsChanged();
}

void doSearch::remove(Context* context, bool erase) {
    assert(context->parent() == this);
    if (m_contexts.size() == 1) // unable to remove the last context
        return;
    if (m_navigation->context() == context) {
        Context* const next = m_contexts[std::max(0, m_contexts.indexOf(context))];
        m_navigation->activate(next);
    }
    if (erase) {
        QDir contextDir(pageResource(context->id()));
        contextDir.removeRecursively();
    }
    m_contexts.removeOne(context);
    m_pages.remove(context->id());
    emit contextsChanged();
}

}

#include "expleague.h"

#include "model/history.h"
#include "util/math.h"

#include <assert.h>

#include <QDir>
#include <QUrl>

#include <QCoreApplication>
#include <QQuickWindow>

expleague::doSearch* root;
namespace expleague {

doSearch::doSearch(QObject* parent) : QObject(parent) {
    QCoreApplication::setOrganizationName("Experts League");

    QCoreApplication::setOrganizationDomain("expleague.com");
    QCoreApplication::setApplicationName("doSearch");
    QApplication::setApplicationVersion(EL_DOSEARCH_VERSION);
    m_saver = new StateSaver(this);
    m_league = new League(this);
    m_navigation = new NavigationManager(this);
    connect(m_navigation, SIGNAL(activeScreenChanged()), this, SLOT(onActiveScreenChanged()));
}

void doSearch::setMain(QQuickWindow* main) {
    m_main = main;
    mainChanged(main);
    m_navigation->setWindow(main);
    main->setProperty("navigation", QVariant::fromValue(m_navigation));
}

doSearch* doSearch::instance() {
    return &*root;
}

void doSearch::restoreState() {
    QDir contextsDir(pageResource("context"));
    foreach(QString contextFileName, contextsDir.entryList()) {
        if (contextFileName.startsWith("."))
            continue;
        Context* ctxt = context(contextFileName);
        if (!ctxt->hasTask())
            m_contexts.append(ctxt);
    }
    if (m_contexts.empty())
        m_contexts.append(context("0"));

    std::sort(m_contexts.begin(), m_contexts.end(), [](const Context* lhs, const Context* rhs){
       return lhs->lastVisitTs() > rhs->lastVisitTs();
    });
    contextsChanged();
    m_saver->restoreState(this);
}

QString doSearch::pageResource(const QString &id) const {
    return QStandardPaths::writableLocation(QStandardPaths::AppLocalDataLocation) + "/pages/" + id;
}

class EmptyPage: public Page {
public:
    EmptyPage(const QString& id, doSearch* parent): Page(id, "qrc:/EmptyView.qml", "", parent){}
protected:
    void interconnect() {}
};

Page* doSearch::empty() const {
    return page("empty", [](const QString& id, doSearch* parent){
        return new EmptyPage(id, parent);
    });
}

WebPage* doSearch::web(const QUrl& url) const {
    QString query = url.query().isEmpty() ? "" : "/" + md5(url.query());
    QString domain = url.host();
    if (domain.startsWith("www."))
        domain = domain.mid(4);
    if (url.query().isEmpty() && url.path().isEmpty()) { // site
        QString id = "web/" + domain + "/site";
        return static_cast<WebPage*>(page(id, [&url](const QString& id, doSearch* parent){
            return new WebSite(id, url, parent);
        }));
    }
    else {
        QString id = "web/" + domain + "/" + url.scheme() + (url.path().isEmpty() || url.path() == "/" ? "/index.html" : url.path()) + query;
        return static_cast<WebPage*>(page(id, [&url](const QString& id, doSearch* parent){
            return new WebPage(id, url, parent);
        }));
    }
}

Context* doSearch::context(const QString& name) const {
    QString id = "context/" + name;
    return static_cast<Context*>(page(id, [](const QString& id, doSearch* parent){
        return new Context(id, parent);
    }));
}

SearchRequest* doSearch::search(const QString& query, int searchIndex) const {
    QString id = "search/" + query;
    SearchRequest* request = static_cast<SearchRequest*>(page(id, [query, searchIndex, this](const QString& id, doSearch* parent){
        SearchRequest* instance = new SearchRequest(id, query, parent);
        instance->setSearchIndex(searchIndex >= 0 ? searchIndex : navigation()->context()->lastRequest()->searchIndex());
        return instance;
    }));
    if (searchIndex >= 0)
        request->setSearchIndex(searchIndex);
    return request;
}

MarkdownEditorPage* doSearch::document(Context* context, const QString& title, Member* author) const {
    QString id = "document/" + context->id() + "/" + (author ? author->id() : "local") + "/" + md5(title);
    return static_cast<MarkdownEditorPage*>(page(id, [&title, &author](const QString& id, doSearch* parent){
        return new MarkdownEditorPage(id, author, title, parent);
    }));
}

Context* doSearch::createContext() {
    Context* instance = context(QString::number(m_contexts.size()));
    append(instance);
    contextsChanged();
    return instance;
}

Page* doSearch::page(const QString &id) const {
    return page(id, [this](const QString& id, doSearch* parent) -> Page*{
        if (id.startsWith("context/"))
            return new Context(id, parent);
        else if (id.startsWith("web/") && id.endsWith("site"))
            return new WebSite(id, parent);
        else if (id.startsWith("web/"))
            return new WebPage(id, parent);
        else if (id.startsWith("search/"))
            return new SearchRequest(id, parent);
        else if (id.startsWith("document/"))
            return new MarkdownEditorPage(id, parent);
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
    if (m_main) {
        Page* active = m_navigation->activePage();
        m_main->setTitle(active->title());
    }
}

void doSearch::append(Context* context) {
    assert(context->parent() == this);
    m_contexts.append(context);
    contextsChanged();
}

void doSearch::remove(Context* context) {
    assert(context->parent() == this);
    if (m_navigation->context() == context) {
        Context* const next = static_cast<Context*>(m_navigation->contextsGroup()->activePages().first());
        m_navigation->activate(next);
    }
    m_contexts.removeOne(context);
    contextsChanged();
}

}

#include "search.h"
#include "../../dosearch.h"
#include "../../ir/dictionary.h"
#include "../../util/filethrottle.h"
#include "../../util/mmath.h"

#include <QRegExp>
#include <QUrlQuery>

namespace expleague {

SearchRequest* SERPage::request() const {
    return parent()->search(query());
}

Page* SERPage::container() const {
    return request()->session();
}

Page* SERPage::parentPage() const {
    return request();
}

void SERPage::interconnect() {
    connect(request(), SIGNAL(sessionChanged()), SLOT(onSessionChanged()));
}

QString SERPage::customJavaScript(){
  return R"(
  elems = document.getElementsByTagName("A")
  for (var i = 0; i < elems.length; ++i) {
      elems[i].removeAttribute("onmousedown")
  })";
}

//SearchRequest SearchRequest::EMPTY("");

QString YandexSERPage::parseQuery(const QUrl& request) {
    QString path = request.path();
    if (path != "/search/" && path != "/yandsearch")
        return "";
    QUrlQuery query(request.hasFragment() ? request.fragment() : request.query());
    QString queryText = query.queryItemValue("text", QUrl::PrettyDecoded);
    queryText.replace("+", " ");
    queryText.replace("%2B", "+");
    static QRegExp site("host:(\\S+)");
    int index;
    if ((index = site.indexIn(queryText)) >= 0)
        queryText = queryText.mid(0, index) + "#site(" + site.cap(1) + ")" + queryText.mid(index + site.matchedLength());
    return queryText.trimmed();
}

bool YandexSERPage::isSearchUrl(const QUrl &url){
    return url.host().contains("yandex.") && (url.path() == "/search/" || url.path() == "/yandsearch");
}

void YandexSERPage::removeTimeStamps(QUrl& url){
    if(!url.host().contains("yandex.") || !(url.path().contains("clck"))){
        return;
    }
    QUrlQuery query(url);
    query.removeQueryItem("cts");
    query.removeQueryItem("mc");
    url.setQuery(query);
}

YandexSERPage::YandexSERPage(const QString& id, const QUrl& url, doSearch* parent): SERPage(id, parseQuery(url), url, parent)
{}

YandexSERPage::YandexSERPage(const QString& id, doSearch* parent): SERPage(id, parent)
{}

QString GoogleSERPage::parseQuery(const QUrl& request) {
    if (request.path() != "/search")
        return "";
    QUrlQuery query(request.hasFragment() ? request.fragment() : request.query());
    QString queryText = query.queryItemValue("q", QUrl::PrettyDecoded);
    queryText.replace("+", " ");
    queryText.replace("%2B", "+");
    static QRegExp site("site:(\\S+)");
    int index;
    if ((index = site.indexIn(queryText)) >= 0)
        queryText = queryText.mid(0, index) + "#site(" + site.cap(1) + ")" + queryText.mid(index + site.matchedLength());
    return queryText.trimmed();
}

bool GoogleSERPage::isSearchUrl(const QUrl &url){
    return url.host().contains("google.") && url.path() == "/search" && QUrlQuery(url.query()).queryItemValue("tbm") == "";
}

GoogleSERPage::GoogleSERPage(const QString& id, const QUrl& url, doSearch* parent): SERPage(id, parseQuery(url), url, parent)
{}

GoogleSERPage::GoogleSERPage(const QString& id, doSearch* parent): SERPage(id, parent)
{}

QUrl googleUrl(const QString& q) {
    QString queryText = q;
    queryText.replace("+", "%2B");
    static QRegExp site("#site\\((\\S+)\\)");
    int index;
    if ((index = site.indexIn(queryText)) >= 0)
        queryText = queryText.mid(0, index) + "site:" + site.cap(1) + queryText.mid(index + site.matchedLength());

    QUrl result;
    result.setScheme("https");
    result.setHost("google.ru");
    result.setPath("/search");
    QUrlQuery query;
    query.addQueryItem("q", queryText);
    result.setQuery(query);
    return result;
}

QUrl yandexUrl(const QString& q) {
    QString queryText = q;
    queryText.replace("+", "%2B");
    static QRegExp site("#site\\((\\S+)\\)");
    int index;
    if ((index = site.indexIn(queryText)) >= 0)
        queryText = queryText.mid(0, index) + "host:" + site.cap(1) + queryText.mid(index + site.matchedLength());

    QUrl result;
    result.setScheme("https");
    result.setHost("yandex.ru");
    result.setPath("/search/");
    QUrlQuery query;
    query.addQueryItem("text", queryText);
    result.setQuery(query);
    return result;
}

void SearchRequest::setSession(SearchSession* session) {
    m_session = session;
    store("search/session", session ? session->id() : QVariant());
    save();
    emit sessionChanged();
}

void SearchRequest::select(int index) {
    m_selected = index;
    store("search.engine", QVariant(index));
    save();
    assert(serp());
    emit selectedChanged();
    emit iconChanged(icon());
}

void SearchRequest::onPartProfileChanged(const BoW& oldOne, const BoW& newOne) {
    SERPage* serp = qobject_cast<SERPage*>(sender());
    if (serp) {
        BoW oldWOTemplates = serp->site()->removeTemplates(oldOne);
        BoW newWOTemplates = serp->site()->removeTemplates(newOne);
        CompositeContentPage::onPartProfileChanged(oldWOTemplates, newWOTemplates);
    }
}

SearchRequest::SearchRequest(const QString& id, const QString& query, doSearch* parent): CompositeContentPage(id, "qrc:/SearchQueryView.qml", parent), m_query(query)
{
    store("search.query", query);
    save();
}

SearchRequest::SearchRequest(const QString& id, doSearch* parent): CompositeContentPage(id, "qrc:/SearchQueryView.qml", parent),
    m_query(value("search.query").toString()),
    m_selected(value("search.engine").toInt())
{}

void SearchRequest::interconnect() {
    CompositeContentPage::interconnect();
    QVariant sessionVar = value("search/session");

    if (sessionVar.isValid())
        setSession(static_cast<SearchSession*>(parent()->page(sessionVar.toString())));
    if (!size()) {
        appendPart(static_cast<SERPage*>(parent()->web(googleUrl(query()))));
        appendPart(static_cast<SERPage*>(parent()->web(yandexUrl(query()))));
    }
    QVariant engine = value("search.engine");
    if (engine.isNull()) { // defauts
        if (parent()->navigation()->context() && parent()->navigation()->context()->lastRequest())
            select(parent()->navigation()->context()->lastRequest()->selected());
        else
            m_selected = 0;
    }
    else m_selected = engine.toInt();
}

class SearchSessionModel {};

bool SearchSession::check(SearchRequest* request) {
    QList<QString> parts = request->query().toLower().split(" ");
    for (int i = 0; i < size(); i++) {
        QStringList currentParts = query(i)->query().toLower().split(" ");
        for (int u = 0; u < parts.size(); u++) {
            for (int v = 0; v < currentParts.size(); v++) {
                if (levenshtein_distance(parts[u], currentParts[v]) <= 1)
                    return true;
            }
        }
    }
    return false;
}

void SearchSession::setRequest(SearchRequest* request) {
    parts()[m_index]->clear();
    const int index = parts().indexOf(request);
    if (index >= 0) {
        m_index = index;
        emit queriesChanged();
    }
    else appendPart(request);
}

void SearchSession::onPartAppended(ContentPage* part) {
    SearchRequest* request = qobject_cast<SearchRequest*>(part);
    if (!request)
        return;
    request->setSession(this);
    connect(request, SIGNAL(selectedChanged()), SLOT(onSelectedSEChanged()));
    m_index = parts().size() - 1;
    emit titleChanged(request->title());
    emit iconChanged(request->icon());
    emit queriesChanged();
}

void SearchSession::onSelectedSEChanged() {
    emit iconChanged(icon());
}

SearchSession::SearchSession(const QString& id, SearchRequest* seed, doSearch* parent):
    CompositeContentPage(id, "qrc:/SearchSessionView.qml", parent), m_model(new SearchSessionModel())
{
    connect(this, SIGNAL(partAppended(ContentPage*)), SLOT(onPartAppended(ContentPage*)));
    append(seed);
}

SearchSession::SearchSession(const QString& id, doSearch* parent):
    CompositeContentPage(id, "qrc:/SearchSessionView.qml", parent), m_model(new SearchSessionModel())
{
    connect(this, SIGNAL(partAppended(ContentPage*)), SLOT(onPartAppended(ContentPage*)));
}

SearchSession::~SearchSession() {
    delete m_model;
}
}

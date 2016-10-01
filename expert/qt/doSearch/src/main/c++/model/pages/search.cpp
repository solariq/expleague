#include "search.h"
#include "../../dosearch.h"
#include "../../util/filethrottle.h"
#include "../../util/mmath.h"

#include <QRegExp>
#include <QUrlQuery>

namespace expleague {
SearchRequest SearchRequest::EMPTY("");

QString SearchRequest::googleText() const {
    QFile file(storage().absoluteFilePath("google.txt"));
    if (!file.exists())
        return QString(QString::null);
    file.open(QFile::ReadOnly);
    return QString(file.readAll());
}

void SearchRequest::setGoogleText(const QString& text) {
    QString currentText = googleText();
    if (currentText == text)
        return;

    FileWriteThrottle::enqueue(storage().absoluteFilePath("google.txt"), text, [this, text]() {
        this->googleTextChanged(text);
        QString yandexText = this->yandexText();
        if (!yandexText.isNull())
            this->textContentChanged(text + yandexText);
    });
}

QString SearchRequest::yandexText() const {
    QFile file(storage().absoluteFilePath("yandex.txt"));
    if (!file.exists())
        return QString(QString::null);
    file.open(QFile::ReadOnly);
    return QString(file.readAll());
}

void SearchRequest::setYandexText(const QString& text) {
    QString currentText = yandexText();
    if (currentText == text)
        return;

    FileWriteThrottle::enqueue(storage().absoluteFilePath("yandex.txt"), text, [this, text]() {
        this->googleTextChanged(text);
        QString googleText = this->googleText();
        if (!googleText.isNull())
            this->textContentChanged(googleText + text);
    });
}

QString SearchRequest::parseGoogleQuery(const QUrl& request) const {
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

void SearchRequest::interconnect() {
    Page::interconnect();
    QVariant sessionVar = value("search.session");
    if (sessionVar.isValid())
        m_session = static_cast<SearchSession*>(parent()->page(sessionVar.toString()));
}

QString SearchRequest::parseYandexQuery(const QUrl& request) const {
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

QUrl SearchRequest::googleUrl() const {
    QString queryText = m_query;
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

QUrl SearchRequest::yandexUrl() const {
    QString queryText = m_query;
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

SearchRequest::SearchRequest(const QString& id, const QString& query, doSearch* parent): Page(id, "qrc:/WebSearchView.qml", parent), m_query(query)
{
    store("search.query", query);
    SearchRequest* last = parent->navigation()->context()->lastRequest();
    m_search_index = last ? last->searchIndex() : 0;
    store("search.engine", m_search_index);
    save();
}

SearchRequest::SearchRequest(const QString& id, doSearch* parent): Page(id, "qrc:/WebSearchView.qml", parent),
    m_query(value("search.query").toString()),
    m_search_index(value("search.engine").toInt())
{}

class SearchSessionModel {

};

SearchSession::SearchSession(const QString& id, SearchRequest* seed, doSearch* parent): Page(id, "qrc:/WebSearchView.qml", parent), m_model(new SearchSessionModel())
{
    append(seed);
}

SearchSession::SearchSession(const QString& id, doSearch* parent): Page(id, "qrc:/WebSearchView.qml", parent), m_model(new SearchSessionModel())
{}


bool SearchSession::check(SearchRequest* request) {
    QList<QString> parts = request->query().toLower().split(" ");
    for (int i = 0; i < m_queries.size(); i++) {
        QStringList currentParts = m_queries[i]->query().toLower().split(" ");
        for (int u = 0; u < parts.size(); u++) {
            for (int v = 0; v < currentParts.size(); v++) {
                if (levenshtein_distance(parts[u], currentParts[v]) <= 1)
                    return true;
            }
        }
    }
    return false;
}

void SearchSession::append(SearchRequest* request) {
    m_queries += request;
    connect(request, SIGNAL(textContentChanged(QString)), this, SLOT(onQueryTextContentChanged()));
    Page::append("session.query", request->id());
    save();
    emit textContentChanged(textContent());
    emit titleChanged(request->title());
    emit iconChanged(request->icon());
    emit queriesChanged();
}

void SearchSession::interconnect() {
    visitAll("session.query", [this](const QVariant& var) {
        SearchRequest* request = static_cast<SearchRequest*>(parent()->page(var.toString()));
        m_queries += request;
        connect(request, SIGNAL(textContentChanged(QString)), this, SLOT(onQueryTextContentChanged()));
    });
}

QString SearchSession::textContent() const {
    QString result;
    for (int i = 0; i < m_queries.size(); i++) {
        result += m_queries[i]->textContent();
    }
    return result;
}

SearchSession::~SearchSession() {
    delete m_model;
}
}

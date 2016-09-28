#ifndef SEARCH_H
#define SEARCH_H

#include <QQmlListProperty>

#include "../page.h"

namespace expleague {

class SearchRequest: public Page {
public:
    static SearchRequest EMPTY;

private:
    Q_OBJECT

    Q_PROPERTY(QString query READ query CONSTANT)
    Q_PROPERTY(QUrl googleUrl READ googleUrl CONSTANT)
    Q_PROPERTY(QUrl yandexUrl READ yandexUrl CONSTANT)
    Q_PROPERTY(QString googleText READ googleText WRITE setGoogleText NOTIFY googleTextChanged)
    Q_PROPERTY(QString yandexText READ yandexText WRITE setYandexText NOTIFY yandexTextChanged)

    Q_PROPERTY(int searchIndex READ searchIndex WRITE setSearchIndex NOTIFY searchIndexChanged)

public:
    QString icon() const {
        switch(searchIndex()) {
        case 0:
            return "qrc:/tools/google.png";
        case 1:
            return "qrc:/tools/yandex.png";
        default:
            return "qrc:/tools/search.png";
        }
    }

    QString title() const {
        return m_query;
    }

    QString query() const {
        return m_query;
    }

    QUrl googleUrl() const;
    QUrl yandexUrl() const;

    QString googleText() const;
    QString yandexText() const;

    QString textContent() const { return googleText() + yandexText(); }

    int searchIndex() const {
        return m_search_index;
    }

public:
    void setQuery(const QString& query) {
        if (query == m_query)
            return;
        m_query = query;
        queryChanged(query);
    }

    void setSearchIndex(int index) {
        if (index == m_search_index)
            return;
        m_search_index = index;
        searchIndexChanged(index);
    }

    void setGoogleText(const QString& text);
    void setYandexText(const QString& text);

    Q_INVOKABLE QString parseGoogleQuery(const QUrl& request) const;
    Q_INVOKABLE QString parseYandexQuery(const QUrl& request) const;

public:
    SearchRequest(const QString& id, const QString& query, doSearch* parent);
    SearchRequest(const QString& id = "", doSearch* parent = 0);

signals:
    void queryChanged(const QString&);
    void searchIndexChanged(int index);
    void googleTextChanged(const QString&);
    void yandexTextChanged(const QString&);

private:
    QString m_query;
    int m_search_index;
};

class SearchSessionModel;
class SearchSession: public Page {
    Q_OBJECT

    Q_PROPERTY(QQmlListProperty<expleague::SearchRequest> queries READ queries NOTIFY queriesChanged)

public:
    QQmlListProperty<SearchRequest> queries() const { return QQmlListProperty<SearchRequest>(const_cast<SearchSession*>(this), const_cast<QList<SearchRequest*>&>(m_queries)); }

    Q_INVOKABLE bool accept(SearchRequest* request);

signals:
    void queriesChanged();

public:
    SearchSession(const QString& id, doSearch* parent);
    virtual ~SearchSession();

private:
    QList<SearchRequest*> m_queries;
    SearchSessionModel* m_model;
};
}
#endif // SEARCH_H

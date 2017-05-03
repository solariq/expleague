#ifndef SEARCH_H
#define SEARCH_H

#include <QQmlListProperty>

#include "../page.h"
#include "web.h"

namespace expleague {

class SearchSession;
class SearchRequest;

class SERPage: public WebPage {
    Q_OBJECT

    Q_PROPERTY(QString query READ query CONSTANT)

public:
    SearchRequest* request() const;
    QString query() const { return m_query; }
    QString title() const { return query(); }

    bool accept(const QUrl &url) const = 0;

    Page* parentPage() const;
    Page* container() const;

    virtual int index() const = 0;

protected:
    SERPage(const QString&id, const QString& query, const QUrl& url, doSearch* parent): WebPage(id, url, parent), m_query(query) { store("serp.query", query); save(); }
    SERPage(const QString&id, doSearch* parent): WebPage(id, parent), m_query(value("serp.query").toString()) {}

    void interconnect();

private slots:
    void onSessionChanged() { emit containerChanged(); }

private:
    QString m_query;
};

class SearchRequest: public CompositeContentPage {
public:
    static SearchRequest EMPTY;

private:
    Q_OBJECT

    Q_PROPERTY(QString query READ query CONSTANT)

    Q_PROPERTY(QQmlListProperty<expleague::SERPage> serps READ serpsQml CONSTANT)
    Q_PROPERTY(int selected READ selected WRITE select NOTIFY selectedChanged)

public:
    QQmlListProperty<SERPage> serpsQml() const { return QQmlListProperty<SERPage>(const_cast<SearchRequest*>(this), reinterpret_cast<QList<SERPage*>&>(const_cast<SearchRequest*>(this)->partsRef())); }

    QString icon() const { return serp()->icon(); }
    QString title() const { return m_query; }

    QString query() const { return m_query; }
    SERPage* serp() const { return static_cast<SERPage*>(part(m_selected)); }

    SearchSession* session() const { return m_session; }
    void setSession(SearchSession* session);

    int selected() const { return m_selected; }
    void select(int index);

signals:
    void selectedChanged();
    void sessionChanged();

protected:
    void interconnect();
    void onPartProfileChanged(const BoW& oldOne, const BoW& newOne);

public:
    explicit SearchRequest(const QString& id, const QString& query, doSearch* parent);
    explicit SearchRequest(const QString& id = "", doSearch* parent = 0);

private:
    QString m_query;
    SearchSession* m_session = 0;
    int m_selected = 0;
};

class SearchSessionModel;
class SearchSession: public CompositeContentPage {
    Q_OBJECT

    Q_PROPERTY(QQmlListProperty<expleague::SearchRequest> queries READ queriesQml NOTIFY queriesChanged)
    Q_PROPERTY(expleague::SearchRequest* request READ current WRITE setRequest NOTIFY queriesChanged)

public:
    QString icon() const { return current()->icon(); }
    QString title() const { return current()->title(); }

    QQmlListProperty<SearchRequest> queriesQml() const { return QQmlListProperty<SearchRequest>(const_cast<SearchSession*>(this), reinterpret_cast<QList<SearchRequest*>&>(const_cast<SearchSession*>(this)->partsRef())); }

    Q_INVOKABLE bool check(SearchRequest* request);
    Q_INVOKABLE void append(SearchRequest* request) { appendPart(request); }
    void setRequest(SearchRequest* request);

public:
    QList<SearchRequest*> queries() const { return QList<SearchRequest*>(reinterpret_cast<QList<SearchRequest*>&>(const_cast<SearchSession*>(this)->partsRef())); }
    BoW profile() const { return m_profile; }
    SearchRequest* query(int index) const { return static_cast<SearchRequest*>(part(index)); }

signals:
    void queriesChanged() const;

private slots:
    void onPartAppended(ContentPage* part);
    void onSelectedSEChanged();

protected:
    void initUI(QQuickItem* ui) { CompositeContentPage::initUI(ui); emit queriesChanged(); }
    SearchRequest* current() const { return query(m_index); }

public:
    explicit SearchSession(const QString& id, SearchRequest* seed, doSearch* parent);
    explicit SearchSession(const QString& id, doSearch* parent);
    virtual ~SearchSession();

private:
    int m_index = -1;
    SearchSessionModel* m_model = 0;
    BoW m_profile;
};


class YandexSERPage: public SERPage {
    Q_OBJECT

public:
    static QString parseQuery(const QUrl& url);
    bool accept(const QUrl &url) const { return parseQuery(url) == query(); }

    QString icon() const { return "qrc:/tools/yandex.png"; }
    int index() const { return 1; }

public:
    YandexSERPage(const QString& id, const QUrl& url, doSearch* parent);
    YandexSERPage(const QString& id, doSearch* parent);
};

class GoogleSERPage: public SERPage {
    Q_OBJECT

public:
    static QString parseQuery(const QUrl& url);
    bool accept(const QUrl &url) const { return parseQuery(url) == query(); }

    QString icon() const { return "qrc:/tools/google.png"; }

    int index() const { return 0; }

public:
    GoogleSERPage(const QString& id, const QUrl& url, doSearch* parent);
    GoogleSERPage(const QString& id, doSearch* parent);
};
}
#endif // SEARCH_H

#ifndef DOSEARCH_H
#define DOSEARCH_H

class QQmlApplicationEngine;
class QSystemTrayIcon;
class QQuickWindow;

extern QQmlApplicationEngine* rootEngine;
#ifndef Q_OS_MAC
extern QSystemTrayIcon* trayIcon;
#endif

#include "model/context.h"
#include "model/page.h"
#include "model/history.h"
#include "model/manager.h"
#include "model/vault.h"
#include "model/pages/search.h"
#include "model/pages/web.h"
#include "model/pages/editor.h"

#include "league.h"

namespace expleague {
class StateSaver;
class CollectionDictionary;
class doSearch: public QObject {
    Q_OBJECT

    Q_PROPERTY(expleague::NavigationManager* navigation READ navigation CONSTANT)
    Q_PROPERTY(expleague::League* league READ league CONSTANT)
    Q_PROPERTY(expleague::History* history READ history CONSTANT)
    Q_PROPERTY(QQuickWindow* main READ main WRITE setMain NOTIFY mainChanged)
    Q_PROPERTY(QQmlListProperty<expleague::Context> contexts READ contextsQml NOTIFY contextsChanged)

public:
    League* league() const { return m_league; }

    QQuickWindow* main() const { return m_main; }

    NavigationManager* navigation() const { return m_navigation; }
    History* history() const { return m_history; }

    QQmlListProperty<Context> contextsQml() const { return QQmlListProperty<Context>(const_cast<doSearch*>(this), const_cast<QList<Context*>&>(m_contexts)); }

public:    
    static doSearch* instance();
    void restoreState();

    Q_INVOKABLE void setMain(QQuickWindow* main);

    QList<Context*> contexts() const { return m_contexts; }
    void append(Context* context);
    Q_INVOKABLE void remove(Context* context);

    Q_INVOKABLE Page* empty() const;
    Q_INVOKABLE Context* context(const QString& id, const QString& name) const;
    Q_INVOKABLE Page* web(const QUrl& name) const;
    Q_INVOKABLE WebSite* webSite(const QString& host) const;
    Q_INVOKABLE WebPage* webPage(const QUrl& host) const;
    Q_INVOKABLE SearchRequest* search(const QString& query, int searchIndex = -1) const;
    Q_INVOKABLE SearchSession* session(SearchRequest* seed, Context* owner) const;
    Q_INVOKABLE MarkdownEditorPage* document(Context* context, const QString& title, Member* member, bool editable) const;

    Q_INVOKABLE Context* createContext(const QString& name);

    Q_INVOKABLE Page* page(const QString& id) const;

    QString childId(const Page* parent, const QString& prefix) const;

    CollectionDictionary* dictionary() const { return m_dictionary; }

signals:
    void mainChanged(QQuickWindow*);
    void contextsChanged();

public:
    explicit doSearch(QObject* parent = 0);

private slots:
    void onActiveScreenChanged();

private:
    friend class StateSaver;
    friend class Page;
    QString pageResource(const QString& id) const;
    Page* page(const QString& id, std::function<Page* (const QString& id, doSearch*)> factory) const;

private:
    QList<Context*> m_contexts;
    mutable QHash<QString, Page*> m_pages;
    StateSaver* m_saver;
    League* m_league;
    QQuickWindow* m_main = 0;
    NavigationManager* m_navigation;
    History* m_history;
    CollectionDictionary* m_dictionary;
};
}

Q_DECLARE_METATYPE(expleague::doSearch*)
#endif // DOSEARCH_H

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
#include "model/search.h"
#include "model/web.h"
#include "model/manager.h"
#include "model/editor.h"
#include "model/downloads.h"
#include "model/history.h"

#include "league.h"

namespace expleague {
class StateSaver;
class doSearch: public QObject {
    Q_OBJECT

    Q_PROPERTY(expleague::NavigationManager* navigation READ navigation CONSTANT)
    Q_PROPERTY(expleague::League* league READ league CONSTANT)
    Q_PROPERTY(expleague::History* history READ history CONSTANT)
    Q_PROPERTY(QQuickWindow* main READ main WRITE setMain NOTIFY mainChanged)

public:
    explicit doSearch(QObject* parent = 0);

    League* league() const { return m_league; }

    NavigationManager* navigation() const { return m_navigation; }
    QQuickWindow* main() const { return m_main; }

    void restoreState();

public:    
    static doSearch* instance();
    Q_INVOKABLE void setMain(QQuickWindow* main);

    QList<Context*> contexts() const { return m_contexts; }
    void append(Context* context);
    void remove(Context* context);

    Q_INVOKABLE Page* empty() const;
    Q_INVOKABLE Context* context(const QString& name) const;
    Q_INVOKABLE DownloadsPage* downloads(Context* context) const;
    Q_INVOKABLE WebPage* web(const QUrl& name) const;
    Q_INVOKABLE SearchRequest* search(const QString& query, int searchIndex = -1) const;
    Q_INVOKABLE MarkdownEditorPage* document(Context* context, const QString& title, Member* member) const;
    History* history() const { return m_history; }

    Q_INVOKABLE Context* createContext();

    Page* page(const QString& id) const;

signals:
    void mainChanged(QQuickWindow*);
    void contextsChanged();

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
};
}

Q_DECLARE_METATYPE(expleague::doSearch*)
#endif // DOSEARCH_H

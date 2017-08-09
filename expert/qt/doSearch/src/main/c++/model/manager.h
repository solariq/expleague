#ifndef MANAGER_H
#define MANAGER_H

#include "page.h"
//#include "pages/cefpage.h"
#include "group.h"
#include "context.h"
#include "search.h"

#include <QObject>
#include <QQmlListProperty>

#include <QList>
#include <QSet>
#include <QHash>
#include <QAbstractItemModel>

class QQuickItem;
class QQuickWindow;
class QDnsLookup;

namespace expleague {
class NavigationManager;
class Context;
class PageTreeModel;


class NavigationManager: public QObject {
    static double MAX_TAB_WIDTH;
    Q_OBJECT

    Q_PROPERTY(QQmlListProperty<expleague::PagesGroup> groups READ groups NOTIFY groupsChanged)
    Q_PROPERTY(expleague::PagesGroup* activeGroup READ selectedGroup NOTIFY groupsChanged)

    Q_PROPERTY(QQmlListProperty<QQuickItem> screens READ screens NOTIFY screensChanged)
    Q_PROPERTY(QQmlListProperty<expleague::Page> history READ history NOTIFY historyChanged)
    Q_PROPERTY(expleague::Context* context READ context NOTIFY contextChanged)

    Q_PROPERTY(QQuickItem* activeScreen READ activeScreen NOTIFY activeScreenChanged)
    Q_PROPERTY(expleague::Page* activePage READ activePage NOTIFY activeScreenChanged)

public:
    Q_INVOKABLE void handleOmnibox(const QString& text, int modifier);

    Q_INVOKABLE void select(PagesGroup* context, Page* selected);
    Q_INVOKABLE void close(PagesGroup* context, Page* page);
    Q_INVOKABLE void movePage(Page *page, PagesGroup* target, int index);
    Q_INVOKABLE bool canMovePage(Page *page, PagesGroup* target);

    Q_INVOKABLE void activate(Context* ctxt);
    Q_INVOKABLE QQuickItem* open(const QUrl& url, Page* context, bool newGroup = false, bool transferUI = true);
    Q_INVOKABLE void open(const QUrl& url);
    Q_INVOKABLE void open(Page* page);
    Q_INVOKABLE void select(PagesGroup* group);
    Q_INVOKABLE void moveTo(Page* page, Context* dst);
    Q_INVOKABLE void rebalanceWidth();

public:
    QQmlListProperty<expleague::PagesGroup> groups() const {
        return QQmlListProperty<expleague::PagesGroup>(const_cast<NavigationManager*>(this), const_cast<QList<PagesGroup*>&>(m_groups));
    }

    QQmlListProperty<QQuickItem> screens() const {
        return QQmlListProperty<QQuickItem>(const_cast<NavigationManager*>(this), const_cast<QList<QQuickItem*>&>(m_screens));
    }

    QQmlListProperty<expleague::Page> history() const {
        return QQmlListProperty<expleague::Page>(const_cast<NavigationManager*>(this), const_cast<QList<Page*>&>(m_history));
    }

    QQuickItem* activeScreen() const {
        return m_active_screen;
    }
    Page* activePage() const { return m_selected; }
    Context* context() const { return m_active_context; }

    PagesGroup* selectedGroup() const;
    Context* suggest(ContentPage* page, const BoW& next = BoW());

public:
    void setWindow(QQuickWindow* window);
    doSearch* parent() const;

signals:
    void contextChanged();
    void groupsChanged();
    void screensChanged();
    void historyChanged();
    void activeScreenChanged();
    void suggestAvailable(Context* ctxt);

public slots:
    void onGroupsChanged();

private slots:
    void onContextsChanged();
    void onPagesChanged();
    void onDnsRequestFinished();
    void onScreenWidthChanged(int width) {
        m_screen_width = width;
        onGroupsChanged();
    }
    void onTypeInProfileChange(const BoW& prev, const BoW& next);
    void onActivePageUIChanged();

public:
    explicit NavigationManager(doSearch* parent = 0);

private:
    void removeSuggestGroup();
    void unfold();
    void popTo(const PagesGroup* to, bool clearSelection);
    void appendGroup(PagesGroup* group);
    void typeIn(Page* page, bool suggest = true);

    void printGroups(){
//      qDebug() << "-----------groups------------";
//      for(auto group: m_groups){
//        auto debug = qDebug();
//        debug << group->root()->id() << "||";
//        for(auto page: group->pages()){
//          bool selected = group->selectedPage() == page;
//          if(selected) debug << "[";
//          debug << page->id();
//          if(selected) debug << "]";
//        }
//      }
    }

private:
    QQuickItem* m_screens_handler = 0;
    QQuickItem* m_active_screen_handler = 0;

    double m_screen_width = 0;
    Context* m_active_context = 0;

    Page* m_selected = 0;

    QList<PagesGroup*> m_groups;
    QList<Page*> m_history;

    QList<QQuickItem*> m_screens;
    QSet<Page*> m_prev_known;
    QSet<Page*> m_always_active;
    QQuickItem* m_active_screen = 0;
};
}

#endif // MANAGER_H

#ifndef MANAGER_H
#define MANAGER_H

#include "page.h"
#include "group.h"
#include "context.h"
#include "search.h"

#include <QObject>
#include <QQmlListProperty>

#include <QList>
#include <QStack>

#include <QDebug>

class QQuickItem;
class QQuickWindow;
class QDnsLookup;

namespace expleague {
class NavigationManager;
class Context;

class NavigationManager: public QObject {
    Q_OBJECT

    Q_PROPERTY(QQmlListProperty<expleague::PagesGroup> groups READ groups NOTIFY groupsChanged)
    Q_PROPERTY(expleague::PagesGroup* contextsGroup READ contextsGroup CONSTANT)
    Q_PROPERTY(expleague::PagesGroup* activeGroup READ selectedGroup NOTIFY groupsChanged)

    Q_PROPERTY(QQmlListProperty<QQuickItem> screens READ screens NOTIFY screensChanged)
    Q_PROPERTY(QQmlListProperty<expleague::Page> history READ history NOTIFY historyChanged)
    Q_PROPERTY(expleague::Context* context READ context WRITE activate NOTIFY contextChanged)

    Q_PROPERTY(QQuickItem* activeScreen READ activeScreen NOTIFY activeScreenChanged)
    Q_PROPERTY(expleague::Page* activePage READ activePage NOTIFY activeScreenChanged)

public:
    Q_INVOKABLE void handleOmnibox(const QString& text, int modifier);

    Q_INVOKABLE void select(PagesGroup* context, Page* selected);
    Q_INVOKABLE void close(PagesGroup* context, Page* page);

    Q_INVOKABLE QQuickItem* open(const QUrl& url, Page* context, bool newGroup = false);

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

    Page* activePage() const {
        return m_selected;
    }

    Context* context() const { return m_active_context; }

    PagesGroup* contextsGroup() const;
    PagesGroup* selectedGroup() const;

public:
    void setWindow(QQuickWindow* window);
    Q_INVOKABLE void activate(Context* ctxt);
    Q_INVOKABLE void open(Page* page);
    Q_INVOKABLE void select(PagesGroup* group);
    doSearch* parent() const;

signals:
    void contextChanged();
    void groupsChanged();
    void screensChanged();
    void historyChanged();
    void activeScreenChanged();

private slots:
    void onContextsChanged();
    void onPagesChanged();
    void onGroupsChanged();
    void onDnsRequestFinished();
    void onScreenWidthChanged(int width) {
        qDebug() << "Screen width set to: " << width;
        m_screen_width = width;
        onGroupsChanged();
    }

public:
    NavigationManager(doSearch* parent = 0);

private:
    void rebalanceWidth();
    void unfold();
    void popTo(const PagesGroup*);
    void typeIn(Page*);

private:
    double m_screen_width;
    Context* m_active_context;

    Page* m_selected;

    QList<PagesGroup*> m_groups;
    PagesGroup* m_contexts_group;
    QList<Page*> m_history;

    QList<QQuickItem*> m_screens;
    QQuickItem* m_active_screen;
    QDnsLookup* m_lookup;
};

}

#endif // MANAGER_H

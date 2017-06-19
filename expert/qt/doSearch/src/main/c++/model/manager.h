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
    Q_INVOKABLE void open(Page* page);
    Q_INVOKABLE void select(PagesGroup* group);
    Q_INVOKABLE void moveTo(Page* page, Context* dst);
    Q_INVOKABLE QAbstractItemModel* treeModel(Page* page, Context* context, int depth);

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
    void rebalanceWidth();
    void removeSuggestGroup();
    void unfold();
    void popTo(const PagesGroup* to, bool clearSelection);
    void appendGroup(PagesGroup* group);
    void typeIn(Page* page, bool suggest = true);

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
    QDnsLookup* m_lookup = 0;
    QQuickItem* m_active_screen = 0;

};

class PageTree: public QObject{
    Q_OBJECT
public:
    PageTree(QObject* parent);
    PageTree* parent();
    PageTree* child(int number);
    int size();
    int row();
    Page* data();
    void init(Page* page, Context* context, int depth);
private:
    QList<PageTree*> m_children;
    PageTree* m_parent = nullptr;
    Page* m_data;
    int m_row = 0;
};

class PageTreeModel: public QAbstractItemModel{
public:
    PageTreeModel(QObject* parent, PageTree* root);
    virtual QModelIndex index(int row, int column, const QModelIndex &parent = QModelIndex()) const Q_DECL_OVERRIDE;
    virtual QModelIndex parent(const QModelIndex &index) const Q_DECL_OVERRIDE;
    virtual int rowCount(const QModelIndex &parent = QModelIndex()) const Q_DECL_OVERRIDE;
    virtual int columnCount(const QModelIndex &parent = QModelIndex()) const Q_DECL_OVERRIDE;
    virtual QVariant data(const QModelIndex &index, int role = Qt::DisplayRole) const Q_DECL_OVERRIDE;
    virtual bool hasChildren(const QModelIndex &parent = QModelIndex()) const Q_DECL_OVERRIDE;
private:
    PageTree* indexTree(QModelIndex index) const ;
    PageTree* m_root;
};

}

#endif // MANAGER_H

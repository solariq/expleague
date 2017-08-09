#ifndef GROUP_H
#define GROUP_H

#include <QQmlListProperty>
#include <QAbstractListModel>

#include "page.h"

namespace expleague {
class NavigationManager;
class Context;
class PagesGroup;

class ActivePagesModel: public QAbstractListModel {
  Q_OBJECT
public:
  QVariant data(const QModelIndex &index, int role = Qt::DisplayRole) const override;
  int rowCount(const QModelIndex &parent = QModelIndex()) const override;
  QHash<int, QByteArray> roleNames() const override;

  ActivePagesModel(PagesGroup* group);
private:
  PagesGroup* m_group;
};

class PagesGroup: public QObject, public PersistentPropertyHolder {
    Q_OBJECT

  Q_PROPERTY(expleague::Page* root READ root CONSTANT)
  Q_PROPERTY(bool selected READ selected WRITE setSelected NOTIFY selectedChanged)
  Q_PROPERTY(expleague::PagesGroup* parentGroup READ parentGroup NOTIFY parentGroupChanged)
  Q_PROPERTY(expleague::Context* owner READ owner CONSTANT)

  Q_PROPERTY(QAbstractListModel* activePagesModel READ activePagesModel NOTIFY pagesChanged)
  Q_PROPERTY(QQmlListProperty<expleague::Page> activePages READ activePages NOTIFY visibleStateChanged)
  Q_PROPERTY(QQmlListProperty<expleague::Page> closedPages READ closedPages NOTIFY visibleStateChanged)
  Q_PROPERTY(double width READ width NOTIFY visibleStateChanged)
  Q_PROPERTY(double scroll READ scroll WRITE setScroll) //from 0 to 1

  Q_PROPERTY(expleague::Page* selectedPage READ selectedPage WRITE selectPage NOTIFY selectedPageChanged)
  Q_PROPERTY(expleague::PagesGroup::Type type READ type CONSTANT)

  Q_ENUMS(Type)

public:
    enum Type: int {
        NORMAL,
        SUGGEST,
        CONTEXT,
        CONTEXTS
    };
    Q_ENUM(Type)

    Page* root() const { return m_root; }
    Type type() const { return m_type; }
    bool selected() const { return m_selected; }
    Context* owner() const { return m_owner; }
    bool empty() const { return m_pages.empty() || m_closed_start == 0; }
    bool hasClosedPages() { return m_closed_start < m_pages.size(); }
    int activeCount() { return m_closed_start; }
    bool closed(Page* page) const { return m_pages.indexOf(page) >= m_closed_start; }

    void setSelected(bool selected) {
        if (m_selected == selected) return;
        m_selected = selected;
        emit selectedChanged(selected);
    }

    bool selectPage(Page* page);

    Page* selectedPage() const {
        return m_selected_page_index < 0 || m_selected_page_index >= m_pages.size() ? 0 : m_pages[m_selected_page_index];
    }

    PagesGroup* parentGroup() const { return m_parent; }

    QQmlListProperty<Page> activePages() const {
      return QQmlListProperty<Page>(const_cast<PagesGroup*>(this), const_cast<QList<Page*>&>(m_active_pages));
    }

    QQmlListProperty<Page> closedPages() const {
      return QQmlListProperty<Page>(const_cast<PagesGroup*>(this), const_cast<QList<Page*>&>(m_closed_pages));
    }

    QAbstractListModel* activePagesModel(){
      return &m_active_pages_model;
    }

    double width(){
      return m_width;
    }

    double scroll(){
      return m_scroll;
    }

    void setScroll(double scroll);

public:
    QList<Page*> pages() const { return m_pages; }
    QList<Page*> activePagesList() { return m_active_pages; }
    QList<Page*> closedPagesList() { return m_closed_pages; }

    Q_INVOKABLE void insert(Page* page, int position = -1);
    Q_INVOKABLE void close(Page* page);
    Q_INVOKABLE bool remove(Page* page);

    void setParentGroup(PagesGroup* group);

    void split(const QList<Page*>& active, const QList<Page*>& closed, int visibleStart, int visibleCount);

    void updateVisibleState(double width);

    void loadParent();

signals:
    void selectedChanged(bool selected);
    void pagesChanged();
    void selectedPageChanged(Page*);
    void parentGroupChanged(PagesGroup*);
    void visibleStateChanged();

public:
    explicit PagesGroup(Page* root = 0, Type type = Type::NORMAL, Context* owner = 0);
    explicit PagesGroup(const QString& groupId, Context* owner);

private:
    void updatePages();
    void setClosedStart(int closedStart);
private:
    Context* m_owner = 0;
    Page* m_root = 0;
    PagesGroup* m_parent = 0;
    Type m_type = NORMAL;


    QList<Page*> m_active_pages;
    QList<Page*> m_closed_pages;

    double m_scroll = 0.5;

    QList<Page*> m_pages;
    bool m_selected = false;
    int m_selected_page_index = -1;
    int m_closed_start = 0;

    ActivePagesModel m_active_pages_model;

    double m_width = 0;
};



}

Q_DECLARE_METATYPE(expleague::PagesGroup::Type)

#endif // GROUP_H

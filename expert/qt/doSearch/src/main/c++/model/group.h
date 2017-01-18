#ifndef GROUP_H
#define GROUP_H

#include <QQmlListProperty>

#include "page.h"

namespace expleague {
class NavigationManager;
class Context;

class PagesGroup: public QObject, public PersistentPropertyHolder {
    Q_OBJECT

    Q_PROPERTY(expleague::Page* root READ root CONSTANT)
    Q_PROPERTY(bool selected READ selected WRITE setSelected NOTIFY selectedChanged)
    Q_PROPERTY(expleague::PagesGroup* parentGroup READ parentGroup NOTIFY parentGroupChanged)
    Q_PROPERTY(expleague::Context* owner READ owner CONSTANT)

    Q_PROPERTY(QQmlListProperty<expleague::Page> visiblePages READ visiblePages NOTIFY visiblePagesChanged)
    Q_PROPERTY(QQmlListProperty<expleague::Page> activePages READ activePages NOTIFY visiblePagesChanged)
    Q_PROPERTY(QQmlListProperty<expleague::Page> closedPages READ closedPages NOTIFY visiblePagesChanged)

    Q_PROPERTY(int visibleStart READ visibleStart NOTIFY visiblePagesChanged)
    Q_PROPERTY(int visibleEnd READ visibleEnd NOTIFY visiblePagesChanged)

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

    Page* root() const { return m_root; }
    Type type() const { return m_type; }
    bool selected() const { return m_selected; }
    Context* owner() const { return m_owner; }
    bool empty() const { return m_pages.empty() || m_closed_start == 0; }

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

    QQmlListProperty<Page> visiblePages() const {
        return QQmlListProperty<Page>(const_cast<PagesGroup*>(this), const_cast<QList<Page*>&>(m_visible_pages));
    }

    QQmlListProperty<Page> activePages() const {
        return QQmlListProperty<Page>(const_cast<PagesGroup*>(this), const_cast<QList<Page*>&>(m_active_pages));
    }

    QQmlListProperty<Page> closedPages() const {
        return QQmlListProperty<Page>(const_cast<PagesGroup*>(this), const_cast<QList<Page*>&>(m_closed_pages));
    }

    int visibleStart() const {
        return m_visible_start;
    }

    int visibleEnd() const {
        return m_visible_start + m_visible_count;
    }

public:
    QList<Page*> pages() const { return m_pages; }
    QList<Page*> visiblePagesList() const { return m_visible_pages; }

    void insert(Page* page, int position = -1);
    void close(Page* page);
    bool remove(Page* page);

    void setParentGroup(PagesGroup* group);

    void split(const QList<Page*>& active, const QList<Page*>& closed, int visibleStart, int visibleCount);

signals:
    void selectedChanged(bool selected);
    void pagesChanged();
    void visiblePagesChanged();
    void selectedPageChanged(Page*);
    void parentGroupChanged(PagesGroup*);

public:
    explicit PagesGroup(Page* root = 0, Type type = Type::NORMAL, Context* owner = 0);
    explicit PagesGroup(const QString& groupId, Context* owner);

private:
    void updatePages();

private:
    Context* m_owner = 0;
    Page* m_root = 0;
    PagesGroup* m_parent = 0;
    Type m_type = NORMAL;

    QList<Page*> m_active_pages;
    QList<Page*> m_visible_pages;
    QList<Page*> m_closed_pages;

    int m_visible_start = 0;
    int m_visible_count = 0;

    QList<Page*> m_pages;
    bool m_selected = false;
    int m_selected_page_index = -1;
    int m_closed_start = 0;
};
}

Q_DECLARE_METATYPE(expleague::PagesGroup::Type)

#endif // GROUP_H

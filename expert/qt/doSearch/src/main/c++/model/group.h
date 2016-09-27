#ifndef GROUP_H
#define GROUP_H

#include <QQmlListProperty>

#include "page.h"

namespace expleague {
class NavigationManager;

class PagesGroup: public QObject {
    Q_OBJECT

    Q_PROPERTY(expleague::Page* root READ root CONSTANT)
    Q_PROPERTY(bool selected READ selected WRITE setSelected NOTIFY selectedChanged)
    Q_PROPERTY(expleague::PagesGroup* parentGroup READ parentGroup NOTIFY parentGroupChanged)

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

    void setSelected(bool selected) {
        if (m_selected == selected) return;
        m_selected = selected;
        selectedChanged(selected);
    }

    bool selectPage(Page* page);

    Page* selectedPage() const {
        return m_selected_page_index < 0 ? 0 : m_pages[m_selected_page_index];
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
    bool remove(Page* page);
    void clear() {
        if (m_pages.empty())
            return;
        m_pages.clear();
        emit pagesChanged();
    }

    void split(const QList<Page*>& active, const QList<Page*>& closed, int visibleStart, int visibleCount);
    void setParentGroup(PagesGroup* group);

signals:
    void selectedChanged(bool selected);
    void pagesChanged();
    void visiblePagesChanged();
    void selectedPageChanged(Page*);
    void parentGroupChanged(PagesGroup*);

private slots:
    void onPageStateChanged(Page::State state);

public:
    explicit PagesGroup(Page* root = 0, Type type = Type::NORMAL, NavigationManager* manager = 0);

private:
    NavigationManager* parent() const;

private:
    Page* m_root;
    PagesGroup* m_parent;
    Type m_type;

    QList<Page*> m_active_pages;
    QList<Page*> m_visible_pages;
    QList<Page*> m_closed_pages;

    int m_visible_start;
    int m_visible_count;

    QList<Page*> m_pages;
    bool m_selected = false;
    int m_selected_page_index = -1;
    int m_closed_start;
};
}

Q_DECLARE_METATYPE(expleague::PagesGroup::Type)

#endif // GROUP_H

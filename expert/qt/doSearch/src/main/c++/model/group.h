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
    Q_PROPERTY(QQmlListProperty<expleague::Page> foldedPages READ foldedPages NOTIFY visiblePagesChanged)
    Q_PROPERTY(QQmlListProperty<expleague::Page> closedPages READ closedPages NOTIFY visiblePagesChanged)

    Q_PROPERTY(expleague::Page* selectedPage READ selectedPage WRITE selectPage NOTIFY selectedPageChanged)

public:
    Page* root() const {
        return m_root;
    }

    bool selected() const {
        return m_selected;
    }

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

    QQmlListProperty<Page> foldedPages() const {
        return QQmlListProperty<Page>(const_cast<PagesGroup*>(this), const_cast<QList<Page*>&>(m_folded_pages));
    }

    QQmlListProperty<Page> closedPages() const {
        return QQmlListProperty<Page>(const_cast<PagesGroup*>(this), const_cast<QList<Page*>&>(m_closed_pages));
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

    void split(const QList<Page*>& visible, const QList<Page*>& folded, const QList<Page*>& closed);
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
    explicit PagesGroup(Page* root = 0, NavigationManager* manager = 0);

private:
    NavigationManager* parent() const;

private:
    Page* m_root;
    PagesGroup* m_parent;

    QList<Page*> m_visible_pages;
    QList<Page*> m_folded_pages;
    QList<Page*> m_closed_pages;

    QList<Page*> m_pages;
    bool m_selected = false;
    int m_selected_page_index = -1;
    int m_closed_start;
};
}
#endif // GROUP_H

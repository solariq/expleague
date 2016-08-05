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

    Q_PROPERTY(QQmlListProperty<expleague::Page> pages READ pages NOTIFY pagesChanged)
    Q_PROPERTY(QQmlListProperty<expleague::Page> visiblePages READ visiblePages NOTIFY visiblePagesChanged)
    Q_PROPERTY(QQmlListProperty<expleague::Page> foldedPages READ foldedPages NOTIFY visiblePagesChanged)

    Q_PROPERTY(expleague::PagesGroup* parentGroup READ parentGroup CONSTANT)
    Q_PROPERTY(expleague::Page* selectedPage READ selectedPage WRITE selectPage NOTIFY selectedPageChanged)

public:
    void ensureVisible(Page* page, int position = -1);
    bool remove(Page* page);
    void clear() {
        m_pages.clear();
        m_selected_page_index = -1;
        pagesChanged();
    }

    bool empty() const {
        return m_pages.empty();
    }

    bool selectPage(Page* page);

    void setSelected(bool selected) {
        if (m_selected == selected) return;
        m_selected = selected;
        selectedChanged(selected);
    }

    Page* selectedPage() const {
        return m_selected_page_index < 0 ? 0 : m_pages[m_selected_page_index];
    }

    Page* next(Page* page) const {
        const int index = m_pages.indexOf(page);
        if (index < 0 && m_pages.size())
            return m_pages[0];
        if (m_pages.size() < 2)
            return 0;
        Page* const next = m_pages[index > 0 ? index - 1 : index + 1];

        return next->state() != Page::State::CLOSED ? next : 0;
    }

    int position(Page* page) const {
        return m_pages.indexOf(page);
    }

    PagesGroup* parentGroup() const { return m_parent; }

public:
    Page* root() const {
        return m_root;
    }

    bool selected() const {
        return m_selected;
    }

    QQmlListProperty<Page> pages() const {
        return QQmlListProperty<Page>(const_cast<PagesGroup*>(this), const_cast<QList<Page*>&>(m_pages));
    }

    QQmlListProperty<Page> visiblePages() const {
        return QQmlListProperty<Page>(const_cast<PagesGroup*>(this), const_cast<QList<Page*>&>(m_visible_pages));
    }

    QQmlListProperty<Page> foldedPages() const {
        return QQmlListProperty<Page>(const_cast<PagesGroup*>(this), const_cast<QList<Page*>&>(m_folded_pages));
    }

    QList<Page*> activePages() const {
        QList<Page*> result;
        foreach (Page* page, m_pages) {
            if (page->state() != Page::CLOSED)
                result.append(page);
        }
        return result;
    }

    int selectedPageIndex() const {
        return m_selected_page_index;
    }

    int visibleCount() const {
        return m_visible_pages.size();
    }

    void setVisibleCount(int count);

signals:
    void selectedChanged(bool selected);
    void pagesChanged();
    void visiblePagesChanged();
    void selectedPageChanged(Page*);

private slots:
    void onPageStateChanged(Page::State state);

public:
    explicit PagesGroup(Page* root = 0, PagesGroup* parent = 0, NavigationManager* manager = 0);

private:
    NavigationManager* parent() const;
    int rebuild(int visible = -1);

private:
    Page* m_root;
    PagesGroup* m_parent;
    QList<Page*> m_pages;
    QList<Page*> m_visible_pages;
    QList<Page*> m_folded_pages;
    bool m_selected = false;
    int m_selected_page_index = -1;
};
}
#endif // GROUP_H

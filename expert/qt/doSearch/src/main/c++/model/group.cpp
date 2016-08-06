#include "group.h"
#include "manager.h"
#include "web.h"

#include <QDebug>

namespace expleague {

PagesGroup::PagesGroup(Page* root, PagesGroup* parent, NavigationManager* manager): QObject(manager), m_root(root), m_parent(parent) {
    connect(this, SIGNAL(pagesChanged()), manager, SLOT(onPagesChanged()));
    if (!root)
        return;
    m_pages = root->outgoing();
    WebPage* webRoot = qobject_cast<WebPage*>(root);
    while (webRoot) {
        m_pages.removeOne(webRoot->redirect());
        webRoot = webRoot->redirect();
    }
    while (parent) {
        foreach (Page* page, parent->m_pages) {
            m_pages.removeOne(page);
        }
        parent = parent->m_parent;
    }
    rebuild();
    m_selected_page_index = root->lastVisited() && root->lastVisited()->state() != Page::State::CLOSED ? m_pages.indexOf(const_cast<Page*>(root->lastVisited())) : -1;
    foreach(Page* page, m_pages) {
        connect(page, SIGNAL(stateChanged(Page::State)), this, SLOT(onPageStateChanged(Page::State)));
    }
}

void PagesGroup::ensureVisible(Page* page, int position) {
    if (!page)
        return;
    int visibleCount = this->visibleCount();
    if (position < 0)
        position = visibleCount;
    int index = m_pages.indexOf(page);
    if (index < 0)
        connect(page, SIGNAL(stateChanged(Page::State)), this, SLOT(onPageStateChanged(Page::State)));
    else if (index >= visibleCount)
        m_pages.removeAt(index);
    else
        return;
    visibleCount++;
    m_pages.insert(position, page);
    if (m_selected_page_index == index && index >= 0)
        m_selected_page_index = position;
    else if (m_selected_page_index >= position)
        m_selected_page_index++;
    m_visible_pages = m_pages.mid(0, visibleCount);
    m_folded_pages = m_pages.mid(visibleCount);
    emit pagesChanged();
    emit visiblePagesChanged();
}

bool PagesGroup::remove(Page* page) {
    page->disconnect(this);
    int index = m_pages.indexOf(page);
    if (index < 0)
        return false;
    else if (index == m_selected_page_index)
        m_selected_page_index = -1;
    m_pages.removeAt(index);
    rebuild(visibleCount());
    emit pagesChanged();
    emit visiblePagesChanged();
    return true;
}


int PagesGroup::rebuild(int visibleCount) {
    QList<Page*> visible;
    QList<Page*> folded;
    visibleCount = visibleCount > 0 ? visibleCount : m_pages.size();
    Page* selected = selectedPage();
    for (int i = 0; i < m_pages.size(); i++) {
        Page* const page = m_pages[i];
        if (page->state() == Page::CLOSED)
            continue;
        if (page == selected) {
            if (visible.size() >= visibleCount) {
                folded.append(visible.last());
                visible.removeLast();
            }
            m_selected_page_index = visible.size();
            visible.append(page);
        }
        else if (i < visibleCount) {
            visible.append(page);
        }
        else {
            folded.append(page);
        }
    }
    for (int i = 0; i < m_pages.size(); i++) {
        Page* const page = m_pages[i];
        if (page->state() != Page::CLOSED)
            continue;
        folded.append(page);
    }
    m_pages.clear();
    m_pages += m_visible_pages = visible;
    m_pages += m_folded_pages = folded;
    return visible.size();
}

bool PagesGroup::selectPage(Page* page) {
    const int index = m_pages.indexOf(page);
    if (m_selected_page_index != index) {
        m_selected_page_index = index;
        selectedPageChanged(page);
    }
    return true;
}

void PagesGroup::onPageStateChanged(Page::State state) {
    Page* const page = qobject_cast<Page*>(sender());
    const int pageIndex = m_visible_pages.indexOf(page);
    if ((state == Page::CLOSED && pageIndex >= 0) || (state == Page::ACTIVE && pageIndex < 0)) {
        if (state == Page::CLOSED && m_selected_page_index == pageIndex)
            m_selected_page_index = -1;
        rebuild(m_visible_pages.size() - 1);
        emit pagesChanged();
        emit visiblePagesChanged();
    }
}

void PagesGroup::setVisibleCount(int count) {
    if (m_visible_pages.size() == count)
        return;
    rebuild(count);
    emit visiblePagesChanged();
}
}

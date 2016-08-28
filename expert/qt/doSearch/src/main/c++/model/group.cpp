#include "group.h"
#include "manager.h"
#include "web.h"

#include <QDebug>

#include <assert.h>

namespace expleague {

PagesGroup::PagesGroup(Page* root, NavigationManager* manager): QObject(manager), m_root(root), m_parent(0) {
    if (!root) {
        m_closed_start = 0;
        m_selected_page_index = -1;
        return;
    }
    QSet<Page*> known;
    QList<Page*> pages = root->outgoing();
    known += pages.toSet();

    WebPage* webRoot = qobject_cast<WebPage*>(root);
    while (webRoot) {
        pages.removeOne(webRoot->redirect());
        foreach(Page* page, webRoot->outgoing()) {
            if (!known.contains(page)) {
                pages += page;
                known += page;
            }
        }

        webRoot = webRoot->redirect();
    }
    for (int i = 0; i < pages.size(); i++) {
        Page* const page = pages[i];
        if (page->state() == Page::CLOSED || qobject_cast<Context*>(page))
            continue;
        connect(page, SIGNAL(stateChanged(Page::State)), this, SLOT(onPageStateChanged(Page::State)));
        if (page == root->lastVisited())
            m_selected_page_index = m_pages.size();
        m_pages.append(page);
    }
    m_closed_start = m_pages.size();
    for (int i = 0; i < pages.size(); i++) {
        Page* const page = pages[i];
        if (page->state() != Page::CLOSED)
            continue;
        connect(page, SIGNAL(stateChanged(Page::State)), this, SLOT(onPageStateChanged(Page::State)));
        m_pages.append(page);
    }
}

void PagesGroup::split(const QList<Page *>& visible, const QList<Page *>& folded, const QList<Page*>& closed)  {
    if (m_visible_pages != visible || m_folded_pages != folded) {
        m_visible_pages = visible;
        m_folded_pages = folded;
        m_closed_pages = closed;
        emit visiblePagesChanged();
    }
}


void PagesGroup::setParentGroup(PagesGroup* group) {
    m_parent = group;
    Page* const selected = m_selected_page_index >= 0 ? m_pages[m_selected_page_index] : 0;
    if (selected && !activePages().contains(selected)) {
        m_selected_page_index = -1;
        selectedPageChanged(0);
    }
    emit parentGroupChanged(group);
}

QList<Page*> PagesGroup::activePages() const {
    QList<Page*> result = m_pages.mid(0, m_closed_start);
    PagesGroup* parent = m_parent;
    while (parent) {
        foreach (Page* page, parent->activePages()) {
            result.removeOne(page);
        }
        parent = parent->m_parent;
    }
    return result;
}

void PagesGroup::insert(Page* page, int position) {
    assert(page->state() != Page::CLOSED);
    assert(position <= m_closed_start);
    int index = m_pages.indexOf(page);
    if (index >= 0 && index < m_closed_start)
        return;
    if (index >= m_closed_start)
        m_pages.removeOne(page);
    else
        connect(page, SIGNAL(stateChanged(Page::State)), this, SLOT(onPageStateChanged(Page::State)));
    position = position < 0 ? m_closed_start : position;
    m_pages.insert(position, page);
    m_closed_start++;
    if (position <= m_selected_page_index)
        m_selected_page_index++;
    emit pagesChanged();
}

bool PagesGroup::remove(Page* page) {
    page->disconnect(this);
    int index = m_pages.indexOf(page);
    if (index >=0 && index == m_selected_page_index) {
        m_selected_page_index = -1;
        selectedPageChanged(0);
    }
    if (m_closed_start >= index)
        m_closed_start--;
    m_pages.removeAt(index);
    emit pagesChanged();
    return true;
}

bool PagesGroup::selectPage(Page* page) {
    const int index = m_pages.indexOf(page);
    if (m_selected_page_index != index) {
        m_selected_page_index = index;
        emit selectedPageChanged(page);
    }
    return true;
}

void PagesGroup::onPageStateChanged(Page::State state) {
    Page* const page = qobject_cast<Page*>(sender());
    int index = m_pages.indexOf(page);
    switch(state) {
    case Page::INACTIVE:
    case Page::ACTIVE:
        if (index > m_closed_start) {
            m_pages.removeAt(index);
            m_pages.insert(m_closed_start, page);
            m_closed_start++;
            emit pagesChanged();
        }
        break;
    case Page::CLOSED:
        m_pages.removeAt(index);
        m_pages.append(page);
        m_closed_start--;
        if (m_selected_page_index > index)
            m_selected_page_index--;
        else if (m_selected_page_index == index) {
            m_selected_page_index = -1;
            emit selectedPageChanged(0);
        }
        emit pagesChanged();
        break;
    }
}
}

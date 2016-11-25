#include "group.h"
#include "manager.h"
#include "pages/web.h"
#include "pages/editor.h"
#include "pages/search.h"
#include "vault.h"

#include <QDebug>

#include <assert.h>

namespace expleague {

PagesGroup::PagesGroup(Page* root, Type type, NavigationManager* manager): QObject(manager), m_root(root), m_parent(0), m_type(type) {
    if (!root || type == NORMAL) {
        m_closed_start = 0;
        m_selected_page_index = -1;
        return;
    }
    Context* context = qobject_cast<Context*>(root);
    QList<Page*> pages;
    if (context) {
        QList<ContentPage*> parts = context->parts();
        pages = QList<Page*>(*reinterpret_cast<const QList<Page*>*>(&parts));
    }
    else pages = root->outgoing();

    { // build redirects closure
        QSet<Page*> known;
        known += pages.toSet();

        WebResource* webRoot = dynamic_cast<WebResource*>(root);
        while (webRoot) {
            pages.removeOne(webRoot->redirect());
            if (webRoot->isRoot()) {
                foreach(Page* page, webRoot->site()->outgoing()) {
                    if (!known.contains(page)) {
                        pages += page;
                        known += page;
                    }
                }
            }
            else {
                foreach(Page* page, webRoot->page()->outgoing()) {
                    if (!known.contains(page)) {
                        pages += page;
                        known += page;
                    }
                }
            }

            webRoot = webRoot->redirect();
        }
    }
    { // cleanup invisible links
        QList<Page*>::iterator iter = pages.begin();
        while (iter != pages.end()) {
            Page* const page = *iter;
            if (qobject_cast<Context*>(page) || qobject_cast<MarkdownEditorPage*>(page) || qobject_cast<Knugget*>(page))
                iter = pages.erase(iter);
            else iter++;
        }
    }
    for (int i = 0; i < pages.size(); i++) {
        Page* const page = pages[i];
        if (page->state() == Page::CLOSED)
            continue;
        connect(page, SIGNAL(stateChanged(Page::State)), this, SLOT(onPageStateChanged(Page::State)));
        if (page == root->lastVisited() && type == CONTEXT)
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

void PagesGroup::split(const QList<Page *>& active, const QList<Page *>& closed, int visibleStart, int visibleCount)  {
    if (m_active_pages != active || m_visible_start != visibleStart || m_visible_count != visibleCount || m_closed_pages != closed) {
        m_active_pages = active;
        m_visible_pages = m_active_pages.mid(visibleStart, visibleCount);
        m_visible_start = visibleStart;
        m_visible_count = visibleCount;
        m_closed_pages = closed;
        emit visiblePagesChanged();
    }
}

void PagesGroup::setParentGroup(PagesGroup* group) {
    assert(group->pages().contains(m_root));
    assert(group->type() != PagesGroup::SUGGEST);
    m_parent = group;
    if (m_selected_page_index >= 0) {
        Page* selected = m_pages[m_selected_page_index];
        while (group) {
            if (group->pages().contains(selected)) {
                m_selected_page_index = -1;
                emit selectedPageChanged(0);
                break;
            }
            group = group->m_parent;
        }
    }

    emit parentGroupChanged(group);
}

void PagesGroup::insert(Page* page, int position) {
    assert(position <= m_closed_start || position == 0);
    if (page->state() == Page::CLOSED)
        page->setState(Page::INACTIVE);
    int index = m_pages.indexOf(page);
    position = position < 0 ? m_closed_start : position;
    if (index >= 0 && index <= position)
        return;
    if (index >= position)
        m_pages.removeOne(page);
    else
        connect(page, SIGNAL(stateChanged(Page::State)), this, SLOT(onPageStateChanged(Page::State)));
    m_pages.insert(position, page);
    m_closed_start++;
    if (position <= m_selected_page_index)
        m_selected_page_index++;
    emit pagesChanged();
}

bool PagesGroup::remove(Page* page) {
    const int index = m_pages.indexOf(page);
    m_pages.removeAt(index);
    if (index >=0 && index == m_selected_page_index) {
        m_selected_page_index = -1;
        emit selectedPageChanged(0);
    }
    if (m_closed_start >= index)
        m_closed_start--;
    page->disconnect(this);
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
        bool selectedChanged = false;
        if (m_selected_page_index > index) {
            m_selected_page_index--;
        }
        else if (m_selected_page_index == index) {
            m_selected_page_index = -1;
            selectedChanged = true;
        }
        emit pagesChanged();
        if (selectedChanged)
            emit selectedPageChanged(0);
        break;
    }
}
}

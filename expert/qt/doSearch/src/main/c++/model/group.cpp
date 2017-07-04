#include "group.h"
#include "manager.h"
#include "pages/web.h"
#include "pages/editor.h"
#include "pages/search.h"
#include "vault.h"
#include "context.h"
#include "../dosearch.h"

#include "../util/mmath.h"

#include <QDebug>

#include <assert.h>

namespace expleague {

void PagesGroup::split(const QList<Page *>& active, const QList<Page *>& closed, int visibleStart, int visibleCount)  {
    m_active_pages = active;
    m_visible_pages = m_active_pages.mid(visibleStart, visibleCount);
    m_visible_start = visibleStart;
    m_visible_count = visibleCount;
    m_closed_pages = closed;
    emit visiblePagesChanged();
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
                store("selected", QVariant());
                emit selectedPageChanged(0);
                break;
            }
            group = group->m_parent;
        }
    }
    store("context", group ? QVariant(group->root()->id()) : QVariant());
    save();
    emit parentGroupChanged(group);
}

void PagesGroup::insert(Page* page, int position) {
    assert(position <= m_closed_start || position < 0);
    int index = m_pages.indexOf(page);
    position = position < 0 ? m_closed_start : position;
    if (index >= 0 && index <= position)
        return;
    if(index >= 0){
        m_pages.removeOne(page);
    }
    m_pages.insert(position, page);
    m_closed_start++;
    if (position <= m_selected_page_index)
        m_selected_page_index++;

    updatePages();
    save();
    emit pagesChanged();
}

bool PagesGroup::remove(Page* page) {
    const int index = m_pages.indexOf(page);
    if(index < 0){
        return true;
    }
    m_pages.removeAt(index);
    if (index == m_selected_page_index) {
        m_selected_page_index = -1;
        store("selected", QVariant());
        emit selectedPageChanged(0);
    }
    else if(m_selected_page_index > index){
        m_selected_page_index--;
    }
    if (m_closed_start >= index)
        m_closed_start--;
    page->disconnect(this);
    updatePages();
    save();
    emit pagesChanged();
    return true;
}

void PagesGroup::updatePages() {
    PersistentPropertyHolder::remove("page");
    foreach(Page* page, m_pages) {
        append("page", page->id());
    }
    store("closed-pages-start", m_closed_start);
}


bool PagesGroup::selectPage(Page* page) {
    const int index = m_pages.indexOf(page);
    if (index >= m_closed_start) {
        if (m_closed_start < index)
            m_pages.move(index, m_closed_start);
        m_closed_start++;
        updatePages();
        emit pagesChanged();
    }
    if (m_selected_page_index != index) {
        m_selected_page_index = index;
        store("selected", page ? QVariant(page->id()) : QVariant());
        emit selectedPageChanged(page);
    }
    save();
    return true;
}

void PagesGroup::close(Page* page) {
    int index = m_pages.indexOf(page);
    if (index < 0 || index >= m_closed_start)
        return;
    if (m_selected_page_index == index) {
        m_selected_page_index = -1;
        store("selected", QVariant());
        emit selectedPageChanged(0);
    }
    m_pages.move(index, m_pages.size() - 1);
    m_closed_start--;
    if (m_selected_page_index > index) {
        m_selected_page_index--;
    }
    updatePages();
    save();
    emit pagesChanged();
    emit visiblePagesChanged();
}

PagesGroup::PagesGroup(Page* root, Type type, Context* owner):
    QObject(owner),
    PersistentPropertyHolder( owner->cd(type == SUGGEST ? "suggest" : "group." + md5(root->id())) ),
    m_owner(owner), m_root(root), m_parent(0), m_type(type), m_closed_start(0)
{
    if (type != SUGGEST) {
        store("root", root->id());
        save();
    }
}


PagesGroup::PagesGroup(const QString& groupId, Context* owner):
    QObject(owner),
    PersistentPropertyHolder(owner->cd("group." + groupId)),
    m_owner(owner), m_parent(0)
{
    m_root = owner->parent()->page(value("root").toString());
    m_type = qobject_cast<Context*>(m_root) ? CONTEXT : NORMAL;
    visitValues("page", [this, owner](const QVariant& pageId){
        m_pages.append(owner->parent()->page(pageId.toString()));
    });
    m_pages.removeAll(0);
    m_closed_start = value("closed-pages-start").isNull() ? m_pages.size() : value("closed-pages-start").toInt();
    m_closed_start = (std::min)(m_closed_start, m_pages.size());
    m_selected_page_index = value("selected").isNull() ? -1 : m_pages.indexOf(owner->parent()->page(value("selected").toString()));
    m_parent = m_owner->associated(owner->parent()->page(value("context").toString()));
}
}

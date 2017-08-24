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

void PagesGroup::updateVisibleState(double width){
  m_width = width;
  m_active_pages = m_pages.mid(0, m_closed_start);
  m_closed_pages = m_pages.mid(m_closed_start);
  emit visibleStateChanged();
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
    if(type() == SUGGEST){
      while (group) {
        auto it = m_pages.begin();
        while(it != m_pages.end()){
          if (group->pages().contains(*it)){
            it = m_pages.erase(it);
            m_closed_start--;
          }else{
            it++;
          }
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

  if(index >= 0){
    if(position < 0)
      return;
    m_pages.move(index, position);
    if(index >= m_closed_start || position == m_closed_start){
      m_closed_start++;
    }
    if(index == m_selected_page_index){
      m_selected_page_index = position;
    }
  }else{
    position = position < 0 ? m_closed_start : position;
    m_pages.insert(position, page);
    m_closed_start++;
    if (position <= m_selected_page_index)
      m_selected_page_index++;
  }
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
    int index = m_pages.indexOf(page);
    if (index >= m_closed_start) {
        if (m_closed_start < index){
            m_pages.move(index, m_closed_start);
            index = m_closed_start;
        }
        m_closed_start++;
        updatePages();
        emit pagesChanged();
    }
    if (m_selected_page_index != index ) {
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
}

void PagesGroup::setScroll(double scroll){
  if(scroll > 1){
    scroll = 1;
  }
  else if(scroll < 0){
    scroll = 0;
  }
  m_scroll = scroll;
}

void PagesGroup::loadParent(){
  m_parent = m_owner->associated(m_owner->parent()->page(value("context").toString()));
}

PagesGroup::PagesGroup(Page* root, Type type, Context* owner):
    QObject(owner),
    PersistentPropertyHolder( owner->cd(type == SUGGEST ? "suggest" : "group." + md5(root->id())) ),
    m_owner(owner), m_root(root), m_parent(0), m_type(type), m_closed_start(0), m_active_pages_model(this)
{
  QObject::connect(this, SIGNAL(pagesChanged()), &m_active_pages_model, SIGNAL(layoutChanged()));
  if (type != SUGGEST) {
    store("root", root->id());
    save();
  }
}


PagesGroup::PagesGroup(const QString& groupId, Context* owner):
    QObject(owner),
    PersistentPropertyHolder(owner->cd("group." + groupId)),
    m_owner(owner), m_parent(0), m_active_pages_model(this)
{
  QObject::connect(this, SIGNAL(pagesChanged()), &m_active_pages_model, SIGNAL(layoutChanged()));
  m_root = owner->parent()->page(value("root").toString());
  m_type = qobject_cast<Context*>(m_root) ? CONTEXT : NORMAL;
  visitValues("page", [this, owner](const QVariant& pageId){
      m_pages.append(owner->parent()->page(pageId.toString()));
  });
  m_pages.removeAll(0);
  m_closed_start = value("closed-pages-start").isNull() ? m_pages.size() : value("closed-pages-start").toInt();
  m_closed_start = (std::min)(m_closed_start, m_pages.size());
  m_selected_page_index = value("selected").isNull() ? -1 : m_pages.indexOf(owner->parent()->page(value("selected").toString()));
}


QVariant ActivePagesModel::data(const QModelIndex &index, int role ) const{
  QList<Page*> pages = m_group->pages();
  if(index.row() < 0 || index.row() >= pages.size()){
    return QVariant();
  }
  Page* page = pages.at(index.row());
  return qVariantFromValue(page);
}

int ActivePagesModel::rowCount(const QModelIndex &parent) const{
  return m_group->activeCount();
}

QHash<int, QByteArray> ActivePagesModel::roleNames() const {
    QHash<int, QByteArray> roles;
    roles[Qt::UserRole + 1] = "modelData";
    return roles;
}


ActivePagesModel::ActivePagesModel(PagesGroup* group): m_group(group){
}

}

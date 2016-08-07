#include "manager.h"
#include "../dosearch.h"

#include <assert.h>

#include <QDnsLookup>
#include <QQuickWindow>
#include <QQmlApplicationEngine>

namespace expleague {

void NavigationManager::handleOmnibox(const QString& text, int /*modifier*/) {
    QUrl url(text, QUrl::StrictMode);

    if (url.isValid() && !url.scheme().isEmpty()) {
        typeIn(parent()->web(url));
        return;
    }

    QString domain = text.split("/")[0];
    m_lookup->setObjectName(text);
    m_lookup->setName(domain);
    m_lookup->lookup();
}

void NavigationManager::onDnsRequestFinished() {
    QString text = m_lookup->objectName();
    Page* page;
    if (m_lookup->error() == QDnsLookup::NoError) // seems to be domain!
        page = parent()->web(QUrl("http://" + text));
    else {
        WebPage* web = qobject_cast<WebPage*>(m_selected);
        if (web)
            text = text.replace("#site", "#site(" + web->url().host() + ")");
        page = parent()->search(text.trimmed());
    }
    typeIn(page);
}

void NavigationManager::typeIn(Page* page) {
    context()->transition(page, Page::TransitionType::TYPEIN);
    PagesGroup* group = m_groups.first();
    group->insert(page, 0);
    select(group, page);
}

QQuickItem* NavigationManager::open(const QUrl& url, Page* context, bool newGroup) {
    WebPage* const next = parent()->web(url);
    if (next->state() == Page::State::CLOSED)
        next->setState(Page::State::INACTIVE);
    if (next == context) // redirect
        return context->ui();
    context->transition(next, Page::TransitionType::FOLLOW_LINK);
    if (context != m_selected)
        return next->ui();
    PagesGroup* group = m_groups.last();
    while (group && group->root() != context) {
        group = group->parentGroup();
    }
    assert(group);
    PagesGroup* prevGroup = group->parentGroup();
    WebPage* prevGroupOwnerWeb = prevGroup ? qobject_cast<WebPage*>(prevGroup->root()) : 0;
    if (prevGroupOwnerWeb && prevGroupOwnerWeb->site() == next->site()) // skip one more group to get all pages of the same site in the same group
        group = prevGroup;
//    const int position = group->root() == context ? 0 : group->pages().indexOf(context) + 1;
    const int position = group->pages().indexOf(context) + 1; // equals previous line
    group->insert(next, position);
    if (!newGroup)
        select(group, next);
    return next->ui();

}

void NavigationManager::close(PagesGroup* context, Page* page) {
    bool selected = page == context->selectedPage();
    QList<Page*> pages = context->activePages();
    const int index = pages.indexOf(page);
    Page* next;
    if (index > 0)
        next = pages[index - 1];
    else if (!index && pages.size() > 1)
        next = pages[1];
    else
        next = 0;
    page->setState(Page::CLOSED);
    if (selected) {
        if (next)
            select(context, next);
        else
            select(context->parentGroup(), context->root());
    }
}

void NavigationManager::select(PagesGroup* context, Page* selected) {
    if (selected == m_selected)
        return;
    Context* ctxt = dynamic_cast<Context*>(selected);
    if (ctxt) {
        activate(ctxt);
        return;
    }
    Page* const current = m_selected;
    current->transition(selected, Page::TransitionType::SELECT_TAB);
    context->root()->transition(selected, Page::TransitionType::CHILD_GROUP_OPEN);
    m_selected = selected;
    select(context);
    if (context->selectedPage() != selected) {
        popTo(context);
        context->selectPage(selected);
        unfold();
        current->transition(m_selected, Page::TransitionType::CHANGED_SCREEN);
    }
    else {
        PagesGroup* group = m_groups.last();
        while (group != context) {
            group->selectPage(0);
            group = group->parentGroup();
        }
        current->transition(m_selected, Page::TransitionType::CHANGED_SCREEN);
    }
    emit groupsChanged();
}

void NavigationManager::select(PagesGroup* group) {
    PagesGroup* const selected = selectedGroup();
    if (group != selected) {
        selected->setSelected(false);
        group->setSelected(true);
    }
}

void NavigationManager::open(Page* page) {
    typeIn(page);
}

PagesGroup* NavigationManager::contextsGroup() const {
    return m_contexts_group;
}

PagesGroup* NavigationManager::selectedGroup() const {
    foreach(PagesGroup* group, m_groups) {
        if (group->selected()) {
            return group;
        }
    }
    return m_contexts_group;
}

void NavigationManager::activate(Context *ctxt) {
    if (ctxt == m_active_context)
        return;
    m_selected->transition(ctxt, Page::TransitionType::SELECT_TAB);
    m_contexts_group->remove(ctxt);
    if (m_active_context)
        m_contexts_group->insert(m_active_context, 0);
    m_active_context = ctxt;
    popTo(0);
    m_selected = ctxt;
    PagesGroup* group = new PagesGroup(ctxt, 0, this);
    m_groups.append(group);
    select(group);
    unfold();
    emit contextChanged();
    emit groupsChanged();
}

void NavigationManager::rebalanceWidth() {
    QList<Page*> visible;
    QSet<Page*> result;
    QHash<Page*, double> probabilities;
    foreach (PagesGroup* group, m_groups) {
        visible += group->activePages();

        result.insert(group->selectedPage());
        visible.removeOne(group->selectedPage());
    }
    foreach (Page* item, visible) {
        double discount = 1;
        double denom = 0;
        double p = 0;
        for (int i = m_groups.size() - 1; i >=0; i--) {
            Page* const selected = m_groups[i]->selectedPage();
            p += selected ? selected->pOut(item) : 0;
            denom *= discount;
            discount *= 0.9;
        }
        if (m_active_context) {
            p += m_active_context->pOut(item);
            denom += discount;
        }
        p /= discount;
        probabilities[item] = p;
    }
    std::sort(visible.begin(), visible.end(), [&probabilities](Page* lhs, Page* rhs) {
        return probabilities[lhs] > probabilities[rhs];
    });
    double available_width = m_screen_width - m_groups.size() * 30 - 30 - 30 - 60; // group decorations + context icon + context view
    result.remove(0);
    if (!visible.empty()) {
        double a = m_selected->pOut(visible.first());
        double b = m_selected->pOut(visible.last());
        qDebug() << "PRange from: " << a << " to: " << b;
    }
    foreach(Page* selected, result) {
        available_width -= std::min(240.0, selected->titleWidth() + 40);
    }

    int index = 0;
    while (index < visible.size()) {
        Page* const current = visible[index];
        double width = std::min(240.0, current->titleWidth() + 40);
        qDebug() << "id: "<< current->id() << " probability: " << m_selected->pOut(current) << " width: " << width;
        if (available_width < width) // title + icon
            break;
        result.insert(current);
        available_width -= width;
        index++;
    }
    qDebug() << "Left width: " << available_width;

    QList<Page*> contexts = m_contexts_group->pages();
    if (contexts.size() < 3)
        m_contexts_group->split(contexts, QList<Page*>());
    else
        m_contexts_group->split(contexts.mid(0, 3), contexts.mid(3));
    foreach (PagesGroup* group, m_groups) {
        QList<Page*> visible;
        QList<Page*> folded;
        QList<Page*> pages = group->pages();
        foreach(Page* page, pages) {
            if (result.contains(page))
                visible.append(page);
            else
                folded.append(page);
        }
        group->split(visible, folded);
    }
}

void NavigationManager::popTo(const PagesGroup* target) {
    while (!m_groups.empty() && m_groups.last() != target) {
        PagesGroup* const group = m_groups.last();
        m_groups.removeLast();
        group->deleteLater();
    }
}

void NavigationManager::unfold() {
    PagesGroup* current = m_groups.last();
    Page* next;
    while ((next = current->selectedPage())) {
        m_selected = next;
        current = new PagesGroup(m_selected, current, this);
        select(m_groups.last());
        m_groups.append(current);
    }
}

doSearch* NavigationManager::parent() const {
    return static_cast<doSearch*>(QObject::parent());
}

void NavigationManager::onContextsChanged() {
    QList<Context*> contexts = parent()->contexts();
    std::sort(contexts.begin(), contexts.end(), [](const Context* lhs, const Context* rhs){
       return lhs->lastVisitTs() > rhs->lastVisitTs();
    });
    Context* active = m_active_context;
    if (contexts.indexOf(m_active_context) < 0)
        active = contexts[0];
    m_contexts_group->clear();
    foreach (Context* ctxt, contexts) {
        if (m_active_context != ctxt)
            m_contexts_group->insert(ctxt);
    }
    activate(active);
}

void NavigationManager::onPagesChanged() {
    rebalanceWidth();
}

void NavigationManager::onGroupsChanged() {
    rebalanceWidth();
    QSet<Page*> known;
    QSet<Page*> active;
    QList<QQuickItem*> screens;
    screens.append(m_selected->ui());
    known.insert(m_selected);
    foreach (PagesGroup* group, m_groups) {
        active.unite(group->activePages().toSet());
    }
    foreach (PagesGroup* group, m_groups) {
        foreach(Page* page, group->visiblePagesList()) {
            if (known.contains(page))
                continue;
            if (group->selectedPage() == page) {
                screens += page->ui();
                known.insert(page);
            }
            else {
                Page* current = page;
                QSet<Page*> context = active;
                while (current->lastVisited() && !context.contains(current->lastVisited())) {
                    current = current->lastVisited();
                    context.unite(current->outgoing().toSet());
                }
                if (!known.contains(current)) {
                    screens += current->ui();
                    known.insert(current);
                }
            }
        }
    }
    if (m_active_screen != m_selected->ui()) {
        if (m_active_screen)
            m_active_screen->setVisible(false);
        m_active_screen = m_selected->ui();
        m_active_screen->setVisible(true);
        activeScreenChanged();
    }
    QVariant returnedValue;
    foreach (QQuickItem* screen, m_screens) { // cleanup
        if (!screens.contains(screen)) {
            screen->setParent(0);
            screen->setParentItem(0);
            screen->deleteLater();
        }
    }
    if (screens != m_screens) {
        m_screens = screens;
        screensChanged();
    }
}

void NavigationManager::setWindow(QQuickWindow* window) {
    m_screen_width = window->width();
    connect(window, SIGNAL(widthChanged(int)), this, SLOT(onScreenWidthChanged(int)));
}

NavigationManager::NavigationManager(doSearch* parent): QObject(parent),
    m_screen_width(0),
    m_active_context(0),
    m_selected(parent->empty()),
    m_contexts_group(new PagesGroup(0, 0, this)),
    m_lookup(new QDnsLookup(this)),
    m_active_screen(0)
{
    connect(this, SIGNAL(groupsChanged()), this, SLOT(onGroupsChanged()));
    connect(m_lookup, SIGNAL(finished()), this, SLOT(onDnsRequestFinished()));
    connect(parent, SIGNAL(contextsChanged()), this, SLOT(onContextsChanged()));
}
}

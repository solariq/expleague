#include "manager.h"
#include "../dosearch.h"

#include <assert.h>

#include <limits>

#include <QDnsLookup>
#include <QQuickWindow>
#include <QQmlApplicationEngine>

namespace expleague {
typedef QHash<QString, PagesGroup*> GroupsHash;

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

QQuickItem* NavigationManager::open(const QUrl& url, Page* context, bool newGroup, bool transferUI) {
    WebPage* const next = parent()->web(url);
    WebPage* const contextWeb = qobject_cast<WebPage*>(context);

    if (next->state() == Page::State::CLOSED)
        next->setState(Page::State::INACTIVE);
    if (next == context) // redirect
        return context->ui();
    if (contextWeb && !newGroup && transferUI) // speedup of the link open: it will be opened inplace and the context page will be built from the scratch
        contextWeb->transferUI(next);
    context->transition(next, Page::TransitionType::FOLLOW_LINK);
    PagesGroup* group = 0;
    for (int i = 0; i < m_groups.size() - 1; i++) {
        WebPage* groupOwner = qobject_cast<WebPage*>(m_groups[i]->root());
        if (groupOwner && groupOwner->site()->mirrorTo(next->site())) {
            group = m_groups[i];
            break;
        }
    }
    if (!group) { // creating new group if the site group was not found
        PagesGroup* const contextGroup = this->group(context, m_active_context, true);
        if (!newGroup) {
            group = contextGroup;
            m_groups.removeLast();
            group->setParentGroup(m_groups.last());
            m_groups.append(group);
        }
        else {
            contextGroup->insert(next, 0);
            m_groups.last()->insert(next, 0);
            return m_selected->ui();
        }
    }
    const int selectedIndex = group->pages().indexOf(m_selected);
    group->insert(next, selectedIndex >= 0 ? selectedIndex : 0);
    if (!newGroup)
        select(group, next);
    return m_selected->ui();
}

void NavigationManager::close(PagesGroup* context, Page* page) {
    bool selected = page == context->selectedPage();
    QList<Page*> pages = context->pages();
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
        if (next && next->state() != Page::CLOSED)
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
        if (ctxt == m_active_context) {
            m_selected = selected;
            PagesGroup* selected = selectedGroup();
            if (selected)
                selected->setSelected(false);
            onGroupsChanged();
        }
        else activate(ctxt);
        return;
    }
    Page* const current = m_selected;
    current->transition(selected, Page::TransitionType::SELECT_TAB);
    context->root()->transition(selected, Page::TransitionType::CHILD_GROUP_OPEN);
    m_selected = selected;
    if (context->type() == PagesGroup::SUGGEST) {
        m_groups.removeLast();
        PagesGroup* group = this->group(context->root(), m_active_context, true);
        group->insert(selected);
        group->setParentGroup(m_groups.last());
        m_groups.append(group);
        context->deleteLater();
        context = group;
    }
    popTo(context, context->selectedPage() == selected);
    context->selectPage(selected);
    unfold();
    current->transition(m_selected, Page::TransitionType::CHANGED_SCREEN);
    emit groupsChanged();
}

void NavigationManager::select(PagesGroup* group) {
    PagesGroup* const selected = selectedGroup();
    if (group != selected) {
        selected->setSelected(false);
        group->setSelected(true);
    }
}

enum GroupMatchType {
    EXACT, SITE, NONE
};

int depth(PagesGroup* group) {
    PagesGroup* parent = group->parentGroup();
    int depth = 0;
    while (parent) {
        group = parent;
        parent = group->parentGroup();
        depth++;
    }
    return qobject_cast<Context*>(group->root()) ? depth : -1;
}

void NavigationManager::open(Page* page) {
    if (m_selected == page)
        return;
    if (page->state() == Page::CLOSED)
        page->setState(Page::INACTIVE);

    PagesGroup* group = this->group(page);
    if (!group) { // new group, trying to match existing
        GroupMatchType matchType = NONE;
        int minDepth = std::numeric_limits<int>::max();
        WebPage* const web = qobject_cast<WebPage*>(page);

        foreach (const GroupsHash& val, m_known_groups.values()) {
            foreach(PagesGroup* currentGroup, val.values()) {
                WebPage* const webRoot = page ? qobject_cast<WebPage*>(currentGroup->root()) : 0;
                GroupMatchType currentMatchType = NONE;
                if (currentGroup->pages().contains(page))
                    currentMatchType = EXACT;
                else if (webRoot && web && webRoot->site()->mirrorTo(web->site()) && matchType > EXACT)
                    currentMatchType = SITE;
                else continue;
                const int currentDepth = depth(currentGroup);
                if (currentDepth >= 0 && (currentMatchType < matchType || currentDepth < minDepth)) {
                    group = currentGroup;
                    matchType = currentMatchType;
                    minDepth = currentDepth;
                }
            }
        }
        if (group) {
            group->insert(page);
            if (group->selectedPage() != page) {
                group->selectPage(page);
                group->root()->transition(page, Page::TransitionType::CHILD_GROUP_OPEN);
            }
            qDebug() << "Found matching pages group for page: " << page->id() << " depth: " << minDepth << " match type: " << matchType;
        }
    }
    else group->selectPage(0);

    if (group) {
        PagesGroup* parent = group->parentGroup();
        while (parent) {
            if (parent->selectedPage() != group->root()) {
                parent->insert(group->root());
                parent->selectPage(group->root());
                parent->root()->transition(group->root(), Page::TransitionType::CHILD_GROUP_OPEN);
            }
            group = parent;
            parent = group->parentGroup();
        }
        if (group->root() == m_active_context) {
            Page* const selected = group->selectedPage();
            group->selectPage(0);
            select(group, selected);
        }
        else activate(static_cast<Context*>(group->root()));
    }
    else typeIn(page);
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
//    m_selected->transition(ctxt, Page::TransitionType::SELECT_TAB);
    m_contexts_group->remove(ctxt);
    if (m_active_context && (!m_active_context->task() || m_active_context->task()->active()))
        m_contexts_group->insert(m_active_context, 0);
    m_active_context = ctxt;
    popTo(0, false);
    m_selected = ctxt;
    PagesGroup* group = this->group(ctxt, ctxt, true);
    m_groups.append(group);
    unfold();
    emit contextChanged();
    emit groupsChanged();
}

void NavigationManager::rebalanceWidth() {
    QList<Page*> visible;
    QSet<Page*> result;
    QHash<Page*, double> probabilities;
    foreach (PagesGroup* group, m_groups) {
        visible += group->pages();

        result.insert(group->selectedPage());
        visible.removeOne(group->selectedPage());
    }
    foreach (Page* page, visible) {
        double p = 0;
        double discount = 1;
        double denom = 0;
        for (int i = m_groups.size() - 1; i >=0; i--) {
            Page* const selected = m_groups[i]->selectedPage();
            p += selected ? selected->pOut(page) : 0;
            denom *= discount;
            discount *= 0.9;
        }
        if (m_active_context) {
            p += m_active_context->pOut(page);
            denom += discount;
        }
        p /= discount;
        probabilities[page] = p;
    }
    std::sort(visible.begin(), visible.end(), [&probabilities](Page* lhs, Page* rhs) {
        return probabilities[lhs] > probabilities[rhs];
    });
    double available_width = m_screen_width - m_groups.size() * 30 - 30 - 30 - 100; // group decorations + context icon + context view
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
        Page* const current = visible[index++];
        if (current->state() == Page::CLOSED || result.contains(current))
            continue;
        double width = std::min(240.0, current->titleWidth() + 40);
        qDebug() << "id: "<< current->id() << " probability: " << m_selected->pOut(current) << " width: " << width;
        if (available_width < width) // title + icon
            break;
        result.insert(current);
        available_width -= width;
    }
    qDebug() << "Left width: " << available_width;

    QList<Page*> contexts = m_contexts_group->pages();
    if (contexts.size() < 3)
        m_contexts_group->split(contexts, QList<Page*>(), QList<Page*>());
    else
        m_contexts_group->split(contexts.mid(0, 3), contexts.mid(3), QList<Page*>());
    QSet<Page*> shown;
    for(int i = 0; i < m_groups.size(); i++) {
        PagesGroup* const group = m_groups[i];
        QList<Page*> visible;
        QList<Page*> folded;
        QList<Page*> closed;
        QList<Page*> pages = group->pages();
        foreach(Page* page, pages) {
            if (shown.contains(page))
                continue;
            if (result.contains(page))
                visible.append(page);
            else if (page->state() == Page::CLOSED)
                closed.append(page);
            else
                folded.append(page);
            shown.insert(page);
        }
        group->split(visible, folded, closed);
    }
}

void NavigationManager::popTo(const PagesGroup* target, bool clearSelection) {
    while (!m_groups.empty() && m_groups.last() != target) {
        PagesGroup* const group = m_groups.last();
        if (clearSelection)
            group->selectPage(0);
        disconnect(group, SIGNAL(pagesChanged()), this, SLOT(onPagesChanged()));
        m_groups.removeLast();
    }
}

void NavigationManager::unfold() {
    PagesGroup* current = m_groups.last();
    Page* next;
    while ((next = current->selectedPage())) {
        m_selected = next;
        PagesGroup* nextGroup = group(next, m_active_context);
        if (!nextGroup || m_groups.contains(nextGroup) || !nextGroup->selectedPage())
            break;
        connect(nextGroup, SIGNAL(pagesChanged()), this, SLOT(onPagesChanged()));
        nextGroup->setParentGroup(current);
        m_groups.append(nextGroup);
        current = nextGroup;
    }
    PagesGroup* suggest = new PagesGroup(m_selected, PagesGroup::SUGGEST, this);
    suggest->setParentGroup(m_groups.last());
    connect(suggest, SIGNAL(pagesChanged()), this, SLOT(onPagesChanged()));
    m_groups.append(suggest);
    for (int i = 0; i < m_groups.size(); i++) {
        m_groups[i]->setSelected(false);
    }
    m_groups[m_groups.size() - 2]->setSelected(true);
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
        if (m_active_context != ctxt) {
            m_contexts_group->insert(ctxt);
        }
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
        active.unite(group->visiblePagesList().toSet());
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
            m_active_screen->setZ(0);
        m_active_screen = m_selected->ui();
        m_active_screen->setZ(10);
        m_active_screen->forceActiveFocus();
        emit activeScreenChanged();
    }
    if (screens != m_screens) {
        QList<QQuickItem*> old = m_screens;
        m_screens = screens;
        foreach (QQuickItem* screen, old) { // cleanup
            if (screen->parentItem() && !screens.contains(screen)) {
                screen->setParentItem(0);
                screen->deleteLater();
            }
        }
//        foreach (QQuickItem* screen, screens) { // cleanup
//            screen->setVisible(true);
//            if (screen != m_active_screen)
//                screen->setVisible(false);
//        }
        emit screensChanged();
    }
}

PagesGroup* NavigationManager::group(Page* page, Context* context, bool create) {
    if (context) {
        QHash<QString, QHash<QString, PagesGroup*>>::const_iterator contextIt = m_known_groups.find(context->id());
        if (contextIt != m_known_groups.end()) {
            QHash<QString, PagesGroup*>::const_iterator groupIt = contextIt.value().find(page->id());
            if (groupIt != contextIt.value().end())
                return groupIt.value();
        }
    }
    else {
        foreach (const GroupsHash& hash, m_known_groups.values()) {
            QHash<QString, PagesGroup*>::const_iterator groupIt = hash.find(page->id());
            if (groupIt != hash.end())
                return groupIt.value();
        }
    }
    PagesGroup* group = 0;
    if (create) {
        group = new PagesGroup(page, page == context ? PagesGroup::CONTEXT : PagesGroup::NORMAL, this);
        m_known_groups[context->id()][page->id()] = group;
    }
    return group;
}

void NavigationManager::setWindow(QQuickWindow* window) {
    m_screen_width = window->width();
    connect(window, SIGNAL(widthChanged(int)), this, SLOT(onScreenWidthChanged(int)));
}

NavigationManager::NavigationManager(doSearch* parent): QObject(parent),
    m_screen_width(0),
    m_active_context(0),
    m_selected(parent->empty()),
    m_contexts_group(new PagesGroup(0, PagesGroup::CONTEXTS, this)),
    m_active_screen(0),
    m_lookup(new QDnsLookup(this))
{
    connect(this, SIGNAL(groupsChanged()), this, SLOT(onGroupsChanged()));
    connect(m_lookup, SIGNAL(finished()), this, SLOT(onDnsRequestFinished()));
    connect(parent, SIGNAL(contextsChanged()), this, SLOT(onContextsChanged()));
}
}

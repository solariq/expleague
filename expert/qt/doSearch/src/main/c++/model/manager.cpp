#include "manager.h"
#include "../dosearch.h"
#include "pages/admins.h"
#include "pages/globalchat.h"
#include <assert.h>

#include <limits>

#include <QDnsLookup>
#include <QQmlContext>
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
        page = parent()->web(QUrl("http://" + text + "/"));
    else {
        WebResource* web = dynamic_cast<WebResource*>(m_selected);
        if (web)
            text = text.replace("#site", "#site(" + web->url().host() + ")");
        page = parent()->search(text.trimmed());
    }
    typeIn(page);
}

void NavigationManager::typeIn(Page* page, bool suggest) {
    Context* const context = qobject_cast<Context*>(page);
    if (context) {
        activate(context);
        return;
    }
    MarkdownEditorPage* const editor = qobject_cast<MarkdownEditorPage*>(page);
    if (editor) {
        m_selected = editor;
        select(0);
        editor->ui()->forceActiveFocus();
        onGroupsChanged();
        return;
    }
    SearchRequest* const request = qobject_cast<SearchRequest*>(page);
    if (request) {
        page = m_active_context->match(request);
        PagesGroup* associated = m_active_context->associated(page, false);
        if (associated) // ensure there is no selected page in session
            associated->selectPage(0);
    }
    ContentPage* newContent = qobject_cast<ContentPage*>(page);
    if (newContent && suggest) {
        Context* suggest = this->suggest(newContent);
        if (suggest)
            emit suggestAvailable(suggest);
        else
            connect(newContent, SIGNAL(changingProfile(BoW,BoW)), SLOT(onTypeInProfileChange(BoW,BoW)));
    }

    this->context()->transition(page, Page::TransitionType::TYPEIN);
    PagesGroup* group = m_active_context->associated(m_active_context);
    group->insert(page, 0);
    select(group, page);
}

QQuickItem* NavigationManager::open(const QUrl& url, Page* context, bool newGroup, bool transferUI) {
    Page* const next = parent()->web(url);
    WebResource* const nextWeb = dynamic_cast<WebResource*>(next);
    WebResource* const contextWeb = dynamic_cast<WebResource*>(context);
    SERPage* const serp = qobject_cast<SERPage*>(next);
    if (next == context) // redirect
        return context->ui();
    if (contextWeb && !newGroup && transferUI) // speedup of the link open: it will be opened inplace and the context page will be built from the scratch
        contextWeb->page()->transferUI(nextWeb->page());
    if (serp) {
        open(serp);
        return serp->ui();
    }
    if (!newGroup)
        context->transition(next, Page::TransitionType::FOLLOW_LINK);
    if(!newGroup){
        for(auto it: m_groups){
            for(auto page: it->pages()){
                if(page == next){
                    select(it, next);
                    return m_selected->ui();
                }
            }

        }
    }
    PagesGroup* group = 0;
    for (int i = 0; i < m_groups.size() - 1; i++) {
        WebResource* groupOwner = dynamic_cast<WebResource*>(m_groups[i]->root());
        if (groupOwner && groupOwner->site()->mirrorTo(nextWeb->site())) {
            group = m_groups[i];
            break;
        }
    }

    if (!group) { // creating new group if the site group was not found
        PagesGroup* const contextGroup = m_active_context->associated(context->container());
        if (!newGroup) {
            group = contextGroup;
            removeSuggestGroup();
            appendGroup(group);
        }
        else { // insert page to both associated group and suggest group
            contextGroup->insert(next);
            m_groups.last()->insert(next);
            return m_selected->ui();
        }
    }
    const int selectedIndex = group->pages().indexOf(m_selected);
    group->insert(next, selectedIndex >= 0 ? selectedIndex + 1 : -1);
    m_active_context->associated(next)->setParentGroup(group);
    if (!newGroup)
        select(group, next);
    return m_selected->ui();
}

void NavigationManager::close(PagesGroup* context, Page* page) {
    bool selected = page == context->selectedPage();
    QList<Page*> pages = context->pages();
    const int index = pages.indexOf(page);
    Page* next; // TODO: change this logic to history based
    if (index > 0)
        next = pages[index - 1];
    else if (!index && pages.size() > 1)
        next = pages[1];
    else
        next = 0;
    context->close(page);
    if (selected) {
        if (next && !context->closed(next))
            select(context, next);
        else
            select(context->parentGroup(), context->root());
    }
}


void NavigationManager::movePage(Page *page, PagesGroup *target, int index){
    PagesGroup *source = context()->associated(page, true);
    PagesGroup *sourceParent = source->parentGroup();
    assert(sourceParent);

    bool pageSelected = sourceParent->selectedPage() == page;
    bool groupSelected = sourceParent->selected();

    sourceParent->remove(page);
    target->insert(page, index);
    source->setParentGroup(target);

    popTo(target, false);
    unfold();

    if(pageSelected){
        target->selectPage(page);
        if(groupSelected){
            target->setSelected(true);
        }
    }
    emit groupsChanged();
}

bool NavigationManager::canMovePage(Page *page, PagesGroup *target){
    if(target->type() == PagesGroup::SUGGEST){
        qDebug() << "cant move page, wrong target group, type:" << target->type();
        return false;
    }
    PagesGroup *source = context()->associated(page, true);
    if(target->pages().contains(page)){
        qDebug() << "cant move page inside group" << target->type();
        return false;
    }
//    if(source->type() == PagesGroup::C){
//        qDebug() << "cant move page, wrong source group type:" << source->type();
//        return false;
//    }
    for(PagesGroup *current = target; current; current = current->parentGroup()){
        if(current == source){
            qDebug() << "cant move page, target cant be descendant of source";
            return false;
        }
    }
    return true;
}

void NavigationManager::select(PagesGroup* context, Page* selected) {
    if (selected == m_selected)
        return;
    Context* ctxt = dynamic_cast<Context*>(selected);
    if (ctxt) {
        if (ctxt == m_active_context) {
            m_selected = selected;
            PagesGroup* currentSelection = selectedGroup();
            if (currentSelection)
                currentSelection->setSelected(false);
            emit onGroupsChanged();
        }
        else activate(ctxt);
        return;
    }
    else if (qobject_cast<SERPage*>(selected)) {
        open(qobject_cast<SERPage*>(selected)->request());
        return;
    }

    Page* const current = m_selected;
    current->transition(selected, Page::TransitionType::SELECT_TAB);
    context->root()->transition(selected, Page::TransitionType::CHILD_GROUP_OPEN);
    m_selected = selected;
    if (context->type() == PagesGroup::SUGGEST) {
        WebResource* groupOwner = dynamic_cast<WebResource*>(context->parentGroup()->root());
        WebResource* webSelected = dynamic_cast<WebResource*>(selected);
        if (webSelected && groupOwner && groupOwner->site()->mirrorTo(webSelected->site())) {
            context->remove(selected);
            context = context->parentGroup();
            context->insert(selected);
        }
        else {
            PagesGroup* group = m_active_context->associated(context->root());
            group->insert(selected);
            removeSuggestGroup();
            group->setParentGroup(m_groups.last());
            m_groups.append(group);
            context->deleteLater();
            context = group;
        }
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
        if (group)
            group->setSelected(true);
    }
}

void NavigationManager::open(Page* page) {
    if (m_selected == page)
        return;
    else if (qobject_cast<Context*>(page)) {
        activate(qobject_cast<Context*>(page));
        return;
    }
    else if (qobject_cast<SERPage*>(page)) {
        SERPage* serp = qobject_cast<SERPage*>(page);
        SearchRequest* request = serp->request();
        request->select(serp->index());
        open(request);
        return;
    }
    else if (qobject_cast<GlobalChat*>(page) && League::instance()->role() == League::ADMIN) {
        if (!qobject_cast<AdminContext*>(m_active_context))
            open(parent()->page(AdminContext::ID));
    }

    PagesGroup* group = m_active_context->associated(page, false);
    if (!group) { // new group, trying to match existing
        Context::GroupMatchType matchType = Context::NONE;
        foreach(Context* context, parent()->contexts()) {
            PagesGroup* matchGroup;
            if (matchType > context->match(page, &matchGroup)) {
                group = matchGroup;
            }
        }

        if (group) {
            group->insert(page);
            if (group->selectedPage() != page) {
                group->root()->transition(page, Page::TransitionType::CHILD_GROUP_OPEN);
                group->selectPage(page);
            }
            qDebug() << "Found matching pages group for page: " << page->id() << " match type: " << matchType;
        }
    }
    else group->selectPage(0);

    if (group) {
        {
            QSet<PagesGroup*> known;
            PagesGroup* parent = group->parentGroup();
            while (parent) {
                if (known.contains(group))
                    break;
                known.insert(group);
                if (parent->selectedPage() != group->root()) {
                    parent->insert(group->root());
                    parent->selectPage(group->root());
                    parent->root()->transition(group->root(), Page::TransitionType::CHILD_GROUP_OPEN);
                }
                group = parent;
                parent = group->parentGroup();
            }
        }

        if (qobject_cast<Context*>(group->root()) && group->root() != m_active_context) {
            activate(static_cast<Context*>(group->root()));
        }
        else {
            if (group->root() != m_active_context) {
                PagesGroup* contextGroup = m_active_context->associated(m_active_context, false);
                contextGroup->insert(group->root());
                contextGroup->selectPage(group->root());
                group->setParentGroup(contextGroup);
                group = contextGroup;
            }
            Page* const selected = group->selectedPage();
            m_selected = m_active_context;
            group->selectPage(0);
            select(group, selected);
        }
    }
    else typeIn(page);
}

PagesGroup* NavigationManager::selectedGroup() const {
    foreach(PagesGroup* group, m_groups) {
        if (group->selected()) {
            return group;
        }
    }
    return 0;
}

void NavigationManager::activate(Context *ctxt) {
    if (ctxt == m_active_context)
        return;
    //    m_selected->transition(ctxt, Page::TransitionType::SELECT_TAB);
    m_active_context = ctxt;
    removeSuggestGroup();
    popTo(0, false);
    m_selected = ctxt;
    PagesGroup* group = ctxt->associated(ctxt);
    appendGroup(group);
    unfold();
    emit groupsChanged();
    emit contextChanged();
}

double effectiveWidth(const QVector<QList<Page*>>& pages, const QVector<QList<Page*>>& closed, const QVector<int>& visibleStart, const QVector<int>& visibleCount) {
    double result = 0;
    for (int i = 0; i < pages.size(); i++) {
        if (i > 0 && (!pages[i].empty() || !closed[i].empty()))
            result += 24; // separator
        if (!closed[i].empty() || visibleStart[i] > 0 || visibleCount[i] < pages.size())
            result += 13; // popup button
        if (visibleCount[i] > 0)
            result += 2; // borders
        if (pages[i].empty()) // for trailing groups
            continue;
        for (int j = 0; j < visibleCount[i]; j++) {
            Page* visiblePage = pages[i][visibleStart[i] + j];
            if (visiblePage)
                result += (std::min)(228.0, visiblePage->titleWidth() + 28);
            else
                qWarning() << "Empty visible page!";
        }
    }
    return result;
}

double NavigationManager::MAX_TAB_WIDTH = 228;
void NavigationManager::rebalanceWidth() {
    QVector<QList<Page*>> closedPages(m_groups.size());
    QVector<QList<Page*>> activePages(m_groups.size()); {
        QSet<Page*> known;
        for (int i = 0; i < m_groups.size(); i++) {
            QList<Page*> current = m_groups[i]->pages();
            QList<Page*>::iterator iter = current.begin();
            while (iter != current.end()) {
                if (!known.contains(*iter)) {
                    known.insert(*iter);
                    if (!m_groups[i]->closed(*iter))
                        activePages[i].push_back(*iter);
                    else
                        closedPages[i].push_back(*iter);
                }
                iter++;
            }
        }
    }

    QVector<int> visibleStart(m_groups.size());
    QVector<int> visibleLength(m_groups.size());

    const double available_width = m_screen_width
            - 4 // starting separator
            - 24 // context icon
            - 6 // ci separator
            - 8 // spacing before buttons
            - (27 + 4) * 3 // search button, vault button, editor button
            - (m_active_context->task() ? (27 + 4) * 2 : 0) // preview and chat buttons
            - 8 // separate buttons from avatar
            - 36 // avatar
            - 4 // trailing space
            ;

    for(int i = 0; i < m_groups.size() - 1; i++) {
        PagesGroup* const group = m_groups[i];
        Page* const selected = group->selectedPage();
        visibleStart[i] = (std::max)(0, activePages[i].indexOf(selected));
        visibleLength[i] = selected ? 1 : 0;
    }
    visibleStart[m_groups.size() - 1] = 0;
    visibleLength[m_groups.size() - 1] = 0;

    while(true) { // greedy (by probability of next click) pages selection
        double maxP = -1;
        int bestPageIndex;
        int bestGroupIndex;
        double prior = 1;
        for (int i = m_groups.size() - 1; i >= 0; i--, prior *= 0.8) {
            const QList<Page*> pages = activePages[i];

            int start = visibleStart[i] - 1;
            int end = visibleStart[i] + visibleLength[i];
            if (start >= 0) { // left expansion available
                double pOut = m_selected->pOut(pages[start]) * prior;
                if (pOut > maxP) {
                    maxP = pOut;
                    bestGroupIndex = i;
                    bestPageIndex = start;
                }
            }
            if (end < pages.size()) { // right expansion available
                double pOut = m_selected->pOut(pages[end]) * prior;
                if (pOut > maxP) {
                    maxP = pOut;
                    bestGroupIndex = i;
                    bestPageIndex = end;
                }
            }
        }
        if (maxP < 0)
            break;
        Page* const page = activePages[bestGroupIndex][bestPageIndex];
        if (bestPageIndex < visibleStart[bestGroupIndex])
            visibleStart[bestGroupIndex]--;
        visibleLength[bestGroupIndex]++;
        double width = effectiveWidth(activePages, closedPages, visibleStart, visibleLength);
        if (width > available_width) {
            if (bestPageIndex == visibleStart[bestGroupIndex])
                visibleStart[bestGroupIndex]++;
            visibleLength[bestGroupIndex]--;
            break;
        }
        //qDebug() << "Making visible: " << page->id() << " p: " << maxP;
    }

    //qDebug() << "Available width: " << available_width << " width used: " << effectiveWidth(activePages, closedPages, visibleStart, visibleLength);
    for(int i = 0; i < m_groups.size(); i++) {
        m_groups[i]->split(activePages[i], closedPages[i], visibleStart[i], visibleLength[i]);
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
        PagesGroup* nextGroup = m_active_context->associated(next, false);
        if (!nextGroup)
            break;
        nextGroup->setParentGroup(current); // during setParentGroup the visible contents can be changed if pages are visible at the above level
        if (nextGroup->empty() || m_groups.contains(nextGroup) || !nextGroup->selectedPage())
            break;
        appendGroup(nextGroup);
        current = nextGroup;
    }
    for (int i = 0; i < m_groups.size(); i++) {
        m_groups[i]->setSelected(false);
    }
    if (m_selected != m_active_context) {
        PagesGroup* const suggest = m_active_context->suggest(m_selected);
        appendGroup(suggest);
        m_groups[m_groups.size() - 2]->setSelected(true);
    }
}

void NavigationManager::appendGroup(PagesGroup *group) {
    connect(group, SIGNAL(pagesChanged()), this, SLOT(onPagesChanged()));
    if (!m_groups.empty())
        group->setParentGroup(m_groups.last());
    m_groups.append(group);
}

void NavigationManager::removeSuggestGroup() {
    while (m_groups.size() > 0 && m_groups.last()->type() == PagesGroup::SUGGEST) {
        m_groups.last()->deleteLater();
        m_groups.removeLast();
    }
}

doSearch* NavigationManager::parent() const {
    return static_cast<doSearch*>(QObject::parent());
}

void NavigationManager::onContextsChanged() {
    QList<Context*> contexts = parent()->contexts();
    Context* active = m_active_context;
    if (contexts.indexOf(m_active_context) < 0)
        active = contexts[0];
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
    if (m_active_context) {
        foreach (Page* page, m_active_context->documents()) {
            screens.append(page->ui());
            known.insert(page);
        }
        screens.append(m_active_context->ui());
        known.insert(m_active_context);
    }

    if (!known.contains(m_selected)) {
        screens.append(m_selected->ui());
        known.insert(m_selected);
    }

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
    if (m_prev_known != known) {
        foreach (Page* page, m_prev_known) { // cleanup
            if (known.contains(page))
                continue;
            QString id = page->id();
            QQuickItem* inactiveScreen = page->ui(true);
            if (inactiveScreen) {
                QObject::connect(inactiveScreen, &QObject::destroyed, [id]() {
                    //qDebug() << "Destroying ui of: " << id;
                });
                page->clear();
            }
        }
        m_prev_known = known;
    }

    if(!parent()->main()){
        return;
    }

    if(!m_screens_handler){
        m_screens_handler = parent()->main()->property("screensHolder").value<QQuickItem*>();
        m_active_screen_handler =  parent()->main()->property("activeScreenHolder").value<QQuickItem*>();
    }
    m_active_screen = m_selected->ui();
    screens.removeOne(m_active_screen);
    for(QQuickItem* screen: screens){
        screen->setParentItem(m_screens_handler);
        screen->setVisible(false);
    }
    m_active_screen->setParentItem(m_active_screen_handler);
    m_active_screen->setVisible(true);
    //m_active_screen->forceActiveFocus();
    m_screens = screens;
    emit activeScreenChanged();
    emit screensChanged();
}

void NavigationManager::onActivePageUIChanged() {
    if (sender() == m_selected) {
        m_active_screen = m_selected->ui();
        emit activeScreenChanged();
    }
    else disconnect(qobject_cast<Page*>(sender()), SIGNAL(uiChanged()), this, 0);
}

Context* NavigationManager::suggest(ContentPage* page, const BoW& profile) {
    if (!page)
        return 0;
    BoW currentProfileWOLast = m_active_context->contains(qobject_cast<ContentPage*>(sender())) ? updateSumComponent(m_active_context->profile(), page->profile(), BoW()) : m_active_context->profile();
    BoW next = profile.module() > 0 ? profile : page->profile();
    double cosDistance = 1 - cos(next, currentProfileWOLast);
    double minCosDistance = cosDistance;
    Context* suggest = m_active_context;
    foreach (Context* ctxt, parent()->contexts()) {
        if (ctxt == m_active_context)
            continue;
        double ctxtCosDistance = 1 - cos(next, ctxt->profile());
        if (ctxtCosDistance < minCosDistance) {
            suggest = ctxt;
            minCosDistance = ctxtCosDistance;
        }
    }
    qDebug() << "Current context cos distance: " << cosDistance << " minimal cos distance: " << minCosDistance << " for " << suggest->title();
    return !suggest->hasTask() && !m_active_context->hasTask() && suggest != m_active_context && minCosDistance < cosDistance - 0.01 ? suggest : 0;
}

void NavigationManager::onTypeInProfileChange(const BoW& /*prev*/, const BoW& next) {
    if (next.module() > 0) {
        Context* suggest = this->suggest(qobject_cast<ContentPage*>(sender()), next);
        if (suggest) {
            emit suggestAvailable(suggest);
        }
        QObject::disconnect(static_cast<ContentPage*>(sender()), SIGNAL(changingProfile(BoW,BoW)), this, SLOT(onTypeInProfileChange(BoW,BoW)));
    }
}

//PagesGroup* NavigationManager::group(Page* page, Context* context, bool create) {
//    if (context) {
//        QHash<QString, QHash<QString, PagesGroup*>>::const_iterator contextIt = m_known_groups.find(context->id());
//        if (contextIt != m_known_groups.end()) {
//            QHash<QString, PagesGroup*>::const_iterator groupIt = contextIt.value().find(page->id());
//            if (groupIt != contextIt.value().end())
//                return groupIt.value();
//        }
//    }
//    else {
//        foreach (const GroupsHash& hash, m_known_groups.values()) {
//            QHash<QString, PagesGroup*>::const_iterator groupIt = hash.find(page->id());
//            if (groupIt != hash.end())
//                return groupIt.value();
//        }
//    }
//    PagesGroup* group = 0;
//    if (create) {
//        group = new PagesGroup(page, page == context ? PagesGroup::CONTEXT : PagesGroup::NORMAL, this);
//        m_known_groups[context->id()][page->id()] = group;
//    }
//    return group;
//}

void NavigationManager::moveTo(Page* page, Context *to) {
    ContentPage* cpage = qobject_cast<ContentPage*>(page);
    if (!cpage || to == m_active_context)
        return;
    Context* from = m_active_context;
    PagesGroup* fromGroup;
    foreach(PagesGroup* group, m_groups) {
        if (group->pages().contains(page)) {
            fromGroup = group;
            break;
        }
    }
    if (fromGroup && fromGroup->type() != PagesGroup::SUGGEST)
        fromGroup->remove(page);
    from->removePart(cpage);
    activate(to);
    typeIn(page, false);
}


QAbstractItemModel* NavigationManager::treeModel(Page* page, Context* context, int depth){
    PageTree* root = new PageTree(this);
    root->init(page, context, depth);
    return new PageTreeModel(this, root);
}

void NavigationManager::setWindow(QQuickWindow* window) {
    m_screen_width = window->width();
    connect(window, SIGNAL(widthChanged(int)), this, SLOT(onScreenWidthChanged(int)));
}

NavigationManager::NavigationManager(doSearch* parent): QObject(parent),
    m_screen_width(0),
    m_active_context(0),
    m_selected(parent->empty()),
    m_active_screen(0),
    m_lookup(new QDnsLookup(this))
{
    connect(this, SIGNAL(groupsChanged()), this, SLOT(onGroupsChanged()));
    connect(m_lookup, SIGNAL(finished()), this, SLOT(onDnsRequestFinished()));
    connect(parent, SIGNAL(contextsChanged()), this, SLOT(onContextsChanged()));
}

PageTree::PageTree(QObject* parent): QObject(parent){
}

PageTree* PageTree::parent(){
    return m_parent;
}

PageTree* PageTree::child(int number){
    return m_children.at(number);
}

Page* PageTree::data(){
    return m_data;
}

int PageTree::size(){
    return m_children.size();
}

int PageTree::row(){
    return m_row;
}

void PageTree::init(Page* page, Context* context, int depth){
    m_data = page;
    if(!depth){
        return;
    }
    PagesGroup* group = context->associated(page, false);
    if(!group){
        return;
    }
    QList<Page*> pages = group->pages();
    for(int i = 0; i < pages.size(); i++){
        PageTree* tree = new PageTree(this);
        tree->m_parent = this;
        tree->m_row = i;
        tree->init(pages.at(i), context, depth - 1);
        m_children.push_back(tree);
    }
}

PageTree* PageTreeModel::indexTree(QModelIndex index) const{
    if(!index.isValid()){
        return m_root;
    }else{
        return static_cast<PageTree*>(index.internalPointer());
    }
}

PageTreeModel::PageTreeModel(QObject* parent, PageTree* root): QAbstractItemModel(parent), m_root(root){
}

QModelIndex PageTreeModel::index(int row, int column, const QModelIndex &parent) const{
    PageTree* parentTree = indexTree(parent);
    if(column != 0 || row >= parentTree->size()){
        return QModelIndex();
    }
    return createIndex(row, column, parentTree->child(row));
}

QModelIndex PageTreeModel::parent(const QModelIndex &index) const{
    PageTree* tree = indexTree(index);
    PageTree* parent = tree->parent();
    return parent ? createIndex(parent->row(), 0, parent) : QModelIndex();
}

int PageTreeModel::rowCount(const QModelIndex &parent) const{
    return indexTree(parent)->size();
}

int PageTreeModel::columnCount(const QModelIndex &parent) const{
    return 1;
}

QVariant PageTreeModel::data(const QModelIndex &index, int role) const{
    if (!index.isValid() || role != Qt::DisplayRole){
        return QVariant();
    }
    return QVariant::fromValue(static_cast<PageTree*>(index.internalPointer()));
}

bool PageTreeModel::hasChildren(const QModelIndex &parent) const{
    return indexTree(parent)->size();
}

}

#include "web/webfolder.h"
#include "web/websearch.h"
#include "web/webscreen.h"


namespace expleague {
QQuickItem* WebSearch::landing() {
    WebScreen* screen = owner()->createWebTab();
    m_queries.last()->registerClick(screen);
    return screen->webEngine();
}

QQuickItem* WebScreen::landing() {
    WebScreen* screen = owner()->createWebTab(this);
    return screen->webEngine();
}

QList<SearchRequest*> WebFolder::requests() const {
    WebSearch* search;
    if ((search = qobject_cast<WebSearch*>(at(0)))) {
        return *reinterpret_cast<QList<SearchRequest*>*>(search->queries().data);
    }
    return QList<SearchRequest*>();
}
}

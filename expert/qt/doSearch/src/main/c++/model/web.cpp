#include "web/webfolder.h"
#include "web/websearch.h"
#include "web/webscreen.h"


namespace expleague {
QQuickItem* WebSearch::landing() {
    WebScreen* screen = owner()->createWebTab();
    m_queries.last()->registerClick(screen);
    return screen->webEngine();
}
}

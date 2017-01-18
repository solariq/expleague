#include "globalchat.h"

#include "../../dosearch.h"

namespace expleague {

QString GlobalChat::ID = "league/global-chat";

void GlobalChat::interconnect() {
    ContentPage::interconnect();
    connect(League::instance(), SIGNAL(roomsChanged()), SLOT(onRoomsChanged()));
    if (!m_owner)
        m_owner = qobject_cast<AdminContext*>(parent()->page(ID));
}

void GlobalChat::onRoomsChanged() {
    m_rooms = parent()->league()->rooms();
    emit roomsChanged();
}
}

#include "globalchat.h"

#include "../../dosearch.h"

namespace expleague {

QString GlobalChat::ID = "league/global-chat";

void GlobalChat::enter(RoomState* room) const {
    room->enter();
    m_owner->setActiveDocument(room->task()->answer());
}

int GlobalChat::openCount() {
    if (parent()->league()->role() != League::ADMIN)
        return 0;
    int result = 0;
    foreach(RoomState* room, m_rooms) {
        if (room->status() == Task::OPEN)
            result++;
    }
    if (result > m_open)
        League::instance()->notifyIfNeeded("", tr("Открылось новое задание"), true);
    m_open = result;
    return result;
}

void GlobalChat::interconnect() {
    ContentPage::interconnect();
    connect(League::instance(), SIGNAL(roomsChanged()), SLOT(onRoomsChanged()));
    if (!m_owner)
        m_owner = qobject_cast<AdminContext*>(parent()->page(ID));
}

void GlobalChat::onRoomStatusChanged(Task::Status /*status*/) {
    emit openCountChanged();
}

void GlobalChat::onRoomsChanged() {
    m_rooms = parent()->league()->rooms();
    foreach(RoomState* room, m_rooms) {
        connect(room, SIGNAL(statusChanged(expleague::Task::Status)), this, SLOT(onRoomStatusChanged(expleague::Task::Status)));
    }

    emit roomsChanged();
    emit openCountChanged();
}
}

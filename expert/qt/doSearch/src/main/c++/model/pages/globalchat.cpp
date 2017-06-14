#include "globalchat.h"

#include "../../dosearch.h"

namespace expleague {

QString GlobalChat::ID = "league/global-chat";

void GlobalChat::enter(RoomStatus* room) {
    if (m_focus) {
        Task* task = m_focus->task();
        m_owner->removeDocument(task->answer());
        task->exit();
    }
    m_focus = room;
    if (!room)
        return;
    room->clearUnread();
    Task* task = room->task();
    if(!task){
        return;
    }
    task->enter();
    m_owner->appendDocument(task->answer());
    m_owner->setActiveDocument(task->answer());
}

int GlobalChat::openCount() {
    if (parent()->league()->role() != League::ADMIN)
        return 0;
    int result = 0;
    foreach(RoomStatus* room, m_rooms) {
        if (room->status() == Task::OPEN || room->status() == Task::VERIFY)
            result++;
    }
    if (result > m_open)
        League::instance()->notifyIfNeeded("", tr("Открылось новое задание"), true);
    m_open = result;
    return result;
}

RoomStatus* GlobalChat::state(const QString& id) const {
    foreach(RoomStatus* state, m_rooms) {
        if (xmpp::user(state->jid()) == id)
            return state;
    }
    return 0;
}

RoomStatus* GlobalChat::registerRoom(Offer* offer) {
    foreach (RoomStatus* state, m_rooms) {
        if (state->roomId() == offer->room()) {
            state->setOffer(offer);
            return state;
        }
    }
    RoomStatus* room = new RoomStatus(offer, this);
    m_rooms.append(room);
    connect(room, SIGNAL(statusChanged(expleague::Task::Status)), this, SLOT(onRoomStatusChanged(expleague::Task::Status)));
    connect(room, SIGNAL(offerChanged()), this, SLOT(onRoomsChanged()));
    room->connectTo(League::instance()->connection());
    onRoomsChanged();
    return room;
}

void GlobalChat::interconnect() {
    ContentPage::interconnect();
    connect(League::instance(), SIGNAL(connectionChanged()), SLOT(onConnectionChanged()));
    m_owner = qobject_cast<AdminContext*>(parent()->page(AdminContext::ID));
}


void GlobalChat::onConnectionChanged() {
    QList<RoomStatus*> rooms = m_rooms;
    m_rooms.clear();
    m_focus = 0;
    //    QTimer::singleShot(100, this, [rooms](){
    foreach (RoomStatus* state, rooms) {
        state->clearMembers();
        state->deleteLater();
    }
    onRoomsChanged();
    //    });
    if (League::instance()->connection()) {
        restore(xmpp::domain(League::instance()->connection()->jid()));
        onRoomsChanged();
    }
}

void GlobalChat::onRoomStatusChanged(Task::Status /*status*/) {
    emit openCountChanged();
}

void GlobalChat::onRoomsChanged() {
    std::sort(m_rooms.begin(), m_rooms.end(), [this](RoomStatus* left, RoomStatus* right) {
        return right->started().toTime_t() < left->started().toTime_t();
    });
    emit roomsChanged();
    emit openCountChanged();
}

void GlobalChat::restore(const QString& /*id*/) {
}

GlobalChat::GlobalChat(doSearch* parent): ContentPage(ID, "qrc:/GlobalChat.qml", parent) {
}

GlobalChat::GlobalChat(const QString& id, doSearch* parent): ContentPage(id, "qrc:/GlobalChat.qml", parent) {
}

Member* RoomStatus::client() const {
    return League::instance()->findMember(xmpp::user(m_offer ? m_offer->client(): ""));
}

Task* RoomStatus::task() const {
    return League::instance()->task(roomId());
}

QStringList RoomStatus::orderStatuses() const {
    QStringList result;
    foreach(xmpp::Progress::OrderState state, m_orders.values()) {
        switch(state) {
        case xmpp::Progress::OS_NONE:
            result += "none";
            break;
        case xmpp::Progress::OS_OPEN:
            result += "open";
            break;
        case xmpp::Progress::OS_IN_PROGRESS:
            result += "progress";
            break;
        case xmpp::Progress::OS_SUSPENDED:
            result += "suspended";
            break;
        case xmpp::Progress::OS_DONE:
            result += "done";
            break;
        }
    }
    return result;
}

void RoomStatus::onStatus(const QString& id, int status) {
    if (id != roomId())
        return;
    m_state = (Task::Status)status;
    if (m_state == Task::OPEN)
        League::instance()->notifyIfNeeded(id, tr("Комната: ") + offer()->topic() + tr(" перешла в открытое состояние"));
    if (m_state == Task::VERIFY)
        League::instance()->notifyIfNeeded(id, tr("Комната: ") + offer()->topic() + tr(" требует проверки"));
    if (m_state != Task::WORK && m_state != Task::VERIFY) {
        m_orders.clear();
        emit orderStatusesChanged();
    }
    emit statusChanged((Task::Status)status);
}

void RoomStatus::onFeedback(const QString& id, int feedback) {
    if (id != roomId())
        return;
    m_feedback = feedback;
    emit feedbackChanged();
}

void RoomStatus::onMessage(const QString& id, const QString& author, bool expert, int count) {
    if (id != roomId())
        return;
    if (!expert)
        League::instance()->notifyIfNeeded(author, "Новое сообщение в комнате: " + m_offer->topic());
    m_unread = expert ? 0 : m_unread + count;
    emit unreadChanged(m_unread);
}

void RoomStatus::onPresence(const QString& id, const QString& expert, const QString& roleStr, const QString& affiliationStr) {
    if (id != roomId())
        return;
    xmpp::Affiliation::Enum affiliation = (xmpp::Affiliation::Enum)QMetaEnum::fromType<xmpp::Affiliation::Enum>().keyToValue(affiliationStr.toLatin1().constData());
    xmpp::Role::Enum role = (xmpp::Role::Enum)QMetaEnum::fromType<xmpp::Role::Enum>().keyToValue(roleStr.toLatin1().constData());
    if (affiliation == xmpp::Affiliation::owner)
        return;
    Member* const member = League::instance()->findMember(xmpp::user(expert));
    if ((role != xmpp::Role::none) != m_occupied.contains(member)) {
        if (m_occupied.contains(member)) {
            if (affiliation == xmpp::Affiliation::none) {
                m_involved.removeOne(member);
                emit involvedChanged();
            }

            m_occupied.removeOne(member);
        }
        else
            m_occupied.append(member);
        emit occupiedChanged();
    }
    if ((affiliation != xmpp::Affiliation::none || role != xmpp::Role::none) && !m_involved.contains(member)) {
        m_involved.append(member);
        emit involvedChanged();
    }
}

void RoomStatus::onOrderStart(const QString& roomId, const QString& /*order*/, const QString& /*expert*/) {
    if (roomId != xmpp::user(m_jid))
        return;
    //    m_orders[progress.order] = progress.state;
}

void RoomStatus::setOffer(Offer *offer) {
    QObject::disconnect(m_offer, 0, this, 0);
    m_offer = offer;

    emit offerChanged();
}

void RoomStatus::onOfferChanged() {
    Member* client = this->client();
    m_involved.removeAll(client);
    client->append(this);
    emit clientChanged();
    emit topicChanged();
}

void RoomStatus::onProgress(const QString& room, const QString& /*id*/, const QString& /*from*/, const xmpp::Progress& progress) {
    if (room != xmpp::user(m_jid) || progress.state == xmpp::Progress::OS_NONE)
        return;
    if (progress.state != xmpp::Progress::OS_DONE)
        m_orders[progress.order] = progress.state;
    else
        m_orders.remove(progress.order);
    emit orderStatusesChanged();
}

bool RoomStatus::connectTo(xmpp::ExpLeagueConnection* connection) {
    if (xmpp::domain(jid()) != xmpp::domain(connection->jid()))
        return false;
    Member* client = this->client();
    if (client)
        client->append(this);
    connect(connection, SIGNAL(roomStatus(QString,int)), SLOT(onStatus(QString,int)));
    connect(connection, SIGNAL(roomFeedback(QString,int)), SLOT(onFeedback(QString,int)));
    connect(connection, SIGNAL(roomMessage(QString,QString,bool,int)), SLOT(onMessage(QString,QString,bool,int)));
    connect(connection, SIGNAL(roomPresence(QString,QString,QString,QString)), SLOT(onPresence(QString,QString,QString,QString)));
    connect(connection, SIGNAL(roomOrderStart(QString,QString,QString)), SLOT(onOrderStart(QString,QString,QString)));
    connect(connection, SIGNAL(progress(QString,QString,QString,xmpp::Progress)), SLOT(onProgress(QString,QString,QString,xmpp::Progress)));
    return true;
}

void RoomStatus::clearMembers() {
    m_involved.clear();
    m_occupied.clear();
    Member* const client = this->client();
    if (client){
        client->remove(this);
    }
    emit involvedChanged();
    emit occupiedChanged();

}

QString RoomStatus::roomId() const {
    return xmpp::user(jid());
}

GlobalChat* RoomStatus::parent() const {
    return static_cast<GlobalChat*>(QObject::parent());
}

RoomStatus::RoomStatus(Offer* task, GlobalChat* parent):
    QObject(parent), m_jid(task->roomJid()), m_offer(task)
{
//    QObject::connect(m_offer, &Offer::destroyed, [this](){
//        m_offer = nullptr;
//    });
}
}

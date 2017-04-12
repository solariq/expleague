#ifndef GLOBALCHAT_H
#define GLOBALCHAT_H

#include "../page.h"
#include "admins.h"
#include "../../league.h"

namespace expleague {
class GlobalChat;

class RoomStatus: public QObject {
    Q_OBJECT

    Q_PROPERTY(int unread READ unread NOTIFY unreadChanged)
    Q_PROPERTY(expleague::Member* client READ client NOTIFY clientChanged)
    Q_PROPERTY(expleague::Offer* offer READ offer NOTIFY offerChanged)
    Q_PROPERTY(QString topic READ topic NOTIFY topicChanged)
    Q_PROPERTY(QQmlListProperty<expleague::Member> involved READ involvedQml NOTIFY involvedChanged)
    Q_PROPERTY(QQmlListProperty<expleague::Member> occupied READ occupiedQml NOTIFY occupiedChanged)
    Q_PROPERTY(int feedback READ feedback NOTIFY feedbackChanged)
    Q_PROPERTY(expleague::Task::Status status READ status NOTIFY statusChanged)
    Q_PROPERTY(expleague::Task* task READ task CONSTANT)
    Q_PROPERTY(QStringList orderStatuses READ orderStatuses NOTIFY orderStatusesChanged)

public:
    int unread() const { return m_unread; }
    Member* client() const;
    Offer* offer() const { return m_offer; }
    QString topic() const { return m_offer->topic(); }
    QDateTime started() const { return m_offer->started();}
    QQmlListProperty<Member> involvedQml() const { return QQmlListProperty<Member>(const_cast<RoomStatus*>(this), const_cast<QList<Member*>&>(m_involved)); }
    QQmlListProperty<Member> occupiedQml() const { return QQmlListProperty<Member>(const_cast<RoomStatus*>(this), const_cast<QList<Member*>&>(m_occupied)); }
    int feedback() const { return m_feedback; }
    bool occupied() const { return m_admin_active; }
    QStringList orderStatuses() const;

    Task* task() const;
    Task::Status status() const { return m_state; }

    QString roomId() const;
    QString jid() const { return m_jid; }

public:
    GlobalChat* parent() const;
    void clearUnread() { m_unread = 0; emit unreadChanged(0); }
    void clearMembers();
    void setOffer(Offer* offer);

public:
//    explicit RoomState(const QString& jid, GlobalChat* parent);
    explicit RoomStatus(Offer* offer, GlobalChat* parent);
    virtual ~RoomStatus();

    friend class GlobalChat;

signals:
    void unreadChanged(int unread) const;
    void occupiedChanged() const;
    void involvedChanged() const;
    void statusChanged(expleague::Task::Status status) const;
    void feedbackChanged() const;
    void topicChanged() const;
    void clientChanged() const;
    void offerChanged() const;
    void orderStatusesChanged() const;

private slots:
    void onPresence(const QString& roomId, const QString& expert, const QString& role, const QString& affiliation);
    void onStatus(const QString& roomId, int status);
    void onFeedback(const QString& roomId, int feedback);
    void onMessage(const QString& roomId, const QString& author, bool expert, int count);
    void onProgress(const QString& room, const QString& id, const QString& from, const xmpp::Progress&);
    void onOrderStart(const QString& roomId, const QString& order, const QString& expert);

    void onOfferChanged();

protected:
    bool connectTo(xmpp::ExpLeagueConnection* connection);

private:
    QString m_jid;
    Offer* m_offer;
    Task::Status m_state = Task::OPEN;
    int m_unread = 0;

    QList<Member*> m_involved;
    QList<Member*> m_occupied;
    QMap<QString, xmpp::Progress::OrderState> m_orders;
    bool m_admin_active;

    int m_feedback = -1;
};

class GlobalChat: public ContentPage {
    Q_OBJECT

    Q_PROPERTY(QQmlListProperty<expleague::RoomStatus> rooms READ rooms NOTIFY roomsChanged)
    Q_PROPERTY(int openCount READ openCount NOTIFY openCountChanged)
public:
    static QString ID;

    QQmlListProperty<RoomStatus> rooms() const { return QQmlListProperty<RoomStatus>(const_cast<GlobalChat*>(this), const_cast<QList<RoomStatus*>&>(m_rooms)); }

    QString title() const { return tr("Админка Лиги"); }
    AdminContext* owner() const { return m_owner; }

    Q_INVOKABLE void enter(RoomStatus* room);
    RoomStatus* state(const QString& id) const;
    RoomStatus* registerRoom(Offer* offer);
    int openCount();

signals:
    void roomsChanged();
    void openCountChanged();

public:
    explicit GlobalChat(doSearch* parent);
    explicit GlobalChat(const QString& id, doSearch* parent);

private slots:
    void onConnectionChanged();
    void onRoomsChanged();
    void onRoomStatusChanged(expleague::Task::Status);

protected:
    void interconnect();
    void restore(const QString& id);

private:
    AdminContext* m_owner = 0;
    QList<RoomStatus*> m_rooms;
    RoomStatus* m_focus = 0;
    int m_open = 0;
};
}

QML_DECLARE_TYPE(expleague::RoomStatus)

#endif // GLOBALCHAT_H

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
    Q_INVOKABLE QString order(int index) const;

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
    virtual ~RoomStatus() override;

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
    Offer* m_offer = nullptr;
    QString m_client;
    Task::Status m_state = Task::OPEN;
    int m_unread = 0;

    QList<Member*> m_involved;
    QList<Member*> m_occupied;
    QMap<QString, xmpp::Progress::OrderState> m_orders;
    bool m_admin_active;

    int m_feedback = -1;
};


class RoomListModel: public QAbstractListModel{
  Q_OBJECT
public:

  RoomListModel(GlobalChat* owner);
  QVariant data(const QModelIndex &index, int role = Qt::DisplayRole) const override;
  int rowCount(const QModelIndex &parent = QModelIndex()) const override;
  QHash<int, QByteArray> roleNames() const override;

  QList<RoomStatus*> rooms(){ return m_rooms;}
  void clear();
  void insertRoom(RoomStatus* room);
  void sortRooms();

  Q_INVOKABLE int indexOf(RoomStatus* room) {return m_rooms.indexOf(room);}

private:
  RoomStatus* m_selected;
  QList<RoomStatus*> m_rooms;
};

class GlobalChat: public ContentPage {
    Q_OBJECT

  Q_PROPERTY(int openCount READ openCount NOTIFY openCountChanged)
  Q_PROPERTY(RoomListModel* roomsModel READ roomsModel CONSTANT)
public:
    static QString ID;

    QString title() const { return tr("Админка Лиги"); }
    AdminContext* owner() const { return m_owner; }
    RoomListModel* roomsModel(){ return m_rooms_model;}

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
    void onRoomOfferChanged();
    void onRoomStatusChanged(expleague::Task::Status);

protected:
    void interconnect() override;
    void restore(const QString& id);

private:
    AdminContext* m_owner = 0;
    RoomStatus* m_focus = 0;
    int m_open = 0;
    RoomListModel* m_rooms_model;
};


}

QML_DECLARE_TYPE(expleague::RoomStatus)

#endif // GLOBALCHAT_H

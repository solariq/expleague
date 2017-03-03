#ifndef GLOBALCHAT_H
#define GLOBALCHAT_H

#include "../page.h"
#include "admins.h"
#include "../../league.h"

namespace expleague {

class Member;
class GlobalChat: public ContentPage {
    Q_OBJECT

    Q_PROPERTY(QQmlListProperty<expleague::RoomState> rooms READ rooms NOTIFY roomsChanged)
    Q_PROPERTY(int openCount READ openCount NOTIFY openCountChanged)
public:
    static QString ID;

    QQmlListProperty<RoomState> rooms() const { return QQmlListProperty<RoomState>(const_cast<GlobalChat*>(this), const_cast<QList<RoomState*>&>(m_rooms)); }

    QString title() const { return tr("Админка Лиги"); }

    Q_INVOKABLE void enter(RoomState* room) const;
    int openCount();

signals:
    void roomsChanged() const;
    void openCountChanged() const;

public:
    explicit GlobalChat(AdminContext* owner): ContentPage(ID, "qrc:/GlobalChat.qml", owner->parent()), m_owner(owner) {}
    explicit GlobalChat(const QString& id, doSearch* parent): ContentPage(id, "qrc:/GlobalChat.qml", parent) {}

private slots:
    void onRoomsChanged();
    void onRoomStatusChanged(expleague::Task::Status);

protected:
    void interconnect();

private:
    AdminContext* m_owner = 0;
    QList<RoomState*> m_rooms;
    int m_open = 0;
};
}

#endif // GLOBALCHAT_H

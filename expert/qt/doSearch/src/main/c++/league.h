#ifndef LEAGUE_H
#define LEAGUE_H

#include <QObject>

#include <QQmlListProperty>
#include <QQuickImageProvider>

#include "task.h"
#include "profile.h"
#include "protocol.h"

namespace expleague {

class doSearch;
class League: public QObject {
    Q_OBJECT

    Q_PROPERTY(expleague::League::Status status READ status NOTIFY statusChanged)
    Q_PROPERTY(expleague::Task* task READ task NOTIFY taskChanged)
    Q_PROPERTY(expleague::Profile* profile READ active WRITE setActive NOTIFY profileChanged)
    Q_PROPERTY(QQmlListProperty<expleague::Profile> profiles READ profiles NOTIFY profilesChanged)

    Q_ENUMS(Status)

public:
    enum Status {
        LS_ONLINE,
        LS_OFFLINE,
        LS_CHECK,
        LS_INVITE,
        LS_ON_TASK
    };

    Status status() const {
        return m_status;
    }

    Task* task() const {
        return m_task;
    }

    Profile* active() const {
        return m_connection ? m_connection->profile() : 0;
    }

    QQmlListProperty<Profile> profiles() {
        return QQmlListProperty<Profile>(this, Profile::list());
    }

    Q_INVOKABLE void connect() {
        if (m_connection)
            m_connection->connect();
    }

    Q_INVOKABLE void disconnect() {
        if (m_connection)
            m_connection->disconnect();
    }

public:
    void setActive(Profile* profile);

signals:
    void statusChanged(Status status);
    void taskChanged(Task* task);
    void profileChanged(Profile* profile);
    void receivedInvite(Offer* offer);

    Q_INVOKABLE void profilesChanged();

private slots:
    void connected() {
        m_status = LS_ONLINE;
        statusChanged(m_status);
    }

    void disconnected() {
        if (m_task) {
            m_task = 0;
            taskChanged(0);
        }
        m_status = LS_OFFLINE;
        statusChanged(m_status);
    }

    void checkReceived() {
        m_status = LS_CHECK;
        statusChanged(m_status);
    }

    void inviteReceived(Offer* offer) {
        receivedInvite(offer);
        m_status = LS_INVITE;
        statusChanged(m_status);
    }

    void resumeReceived(Offer* task);

    void answerReceived(const QString& answer) {
    }

    void cancelReceived() {
        m_task = 0;
        m_status = LS_ONLINE;
        taskChanged(0);
        statusChanged(m_status);
    }

public:
    League(QObject* parent = 0): QObject(parent) {}

protected:
    doSearch* parent() const;

private:
    Task* m_task = 0;
    Status m_status = LS_OFFLINE;
    xmpp::ExpLeagueConnection* m_connection = 0;
};

class AvatarStore: public QQuickImageProvider {

private:
    QMap<QString, QPixmap> m_avatars_cache;
};

class ImagesStore: public QQuickImageProvider {

};

}

Q_DECLARE_METATYPE(expleague::League*)
Q_DECLARE_METATYPE(expleague::League::Status)

#endif // LEAGUE_H

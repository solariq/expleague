#ifndef PROTOCOL_H
#define PROTOCOL_H

#include <string>
#include <memory>
#include <stdlib.h>

#include <QMap>

#include <QObject>

#include <QDomElement>
#include <QDateTime>
#include <QPixmap>

#include "QXmppClient.h"
#include "QXmppClientExtension.h"

#include "profile.h"
#include "task.h"

QDebug operator<<(QDebug dbg, const QXmppStanza& node);
QDebug operator<<(QDebug dbg, const QDomNode& node);

namespace expleague {

class Member;
class TaskTag;
class AnswerPattern;

namespace xmpp {

inline QString user(const QString& jid) { return jid.contains("@") ? jid.section("@", 0, 0) : QString(); }
inline QString domain(const QString& jid) {
    QString domain = jid.section("@", 1).section("/", 0, 0);
    return domain.startsWith("muc.") ? domain.mid(4) : domain;
}
inline bool isRoom(const QString& jid) {
    QString domain = jid.section("@", 1).section("/", 0, 0);
    return domain.startsWith("muc.");
}

inline QString resource(const QString& jid) { return jid.section("@", 1).section("/", 1); }
QString nextId();

class Affiliation: public QObject {
    Q_OBJECT
public:
    enum Enum {
        owner,
        admin,
        member,
        visitor,
        none,
        outcast
    };
    Q_ENUM(Enum);
};

class Role: public QObject {
    Q_OBJECT
public:
    enum Enum {
        moderator,
        participant,
        visitor,
        none,
    };
    Q_ENUM(Enum);
};

class Progress {
public:
    enum Operation {
      PO_ADD,
      PO_REMOVE,
      PO_VISIT
    };

    enum Target {
      PO_PATTERN,
      PO_TAG,
      PO_PHONE,
      PO_URL,
    };

    Operation operation;
    Target target;
    QString name;

    bool empty() { return name.isEmpty(); }

    QDomElement toXml() const;
    static Progress fromXml(const QDomElement&);
};

class ExpLeagueConnection: public QObject {
    Q_OBJECT

    Q_PROPERTY(QString jid READ jid NOTIFY jidChanged)
    Q_PROPERTY(int tasksAvailable READ tasksAvailable NOTIFY tasksAvailableChanged)

public:
    Profile* profile() const { return m_profile; }
    QString jid() const { return m_jid; }
    bool valid() const { return m_client->isAuthenticated(); }
    int tasksAvailable() const { return m_tasks_available; }

public:
    void connect();
    void disconnect();

    QString id() const { return m_jid.isEmpty() ? m_profile->login().replace('.', '_') : xmpp::user(m_jid); }

    Member* find(const QString& id, bool requestProfile = true);
    QList<Member*> members() const;
    void listExperts() const;

    void sendOk(Offer* offer) {
        sendCommand("ok", offer);
    }

    void sendAccept(Offer *offer) {
        sendCommand("start", offer);
    }

    void sendResume(Offer* offer) {
        sendCommand("resume", offer);
    }

    void sendCancel(Offer *offer) {
        sendCommand("cancel", offer);
    }

    void sendSuspend(Offer *offer, long seconds);

    void sendMessage(const QString& to, const QString&);
    void sendProgress(const QString& to, const Progress& progress);
    void sendAnswer(const QString& roomId, int difficulty, int success, bool extraInfo, const QString& answer);

    void sendUserRequest(const QString&);

    void sendPresence(const QString& room, bool available = true);
    void sendOffer(const Offer& offer);

    void requestHistory(const QString& clientId);

signals:
    // system commands
    void check(const Offer& task);
    void invite(const Offer& task);
    void resume(const Offer& task);
    void cancel(const Offer& offer);

    // task update signals
    void offer(const QString& room, const QString& id, const Offer& offer);
    void message(const QString& room, const QString& id, const QString& from, const QString&);
    void image(const QString& room, const QString& id, const QString& from, const QUrl&);
    void answer(const QString& room, const QString& id, const QString& from, const QString&);
    void progress(const QString& room, const QString& id, const QString& from, const Progress&);

    // admin signals
    void tasksAvailableChanged(int oldValue);

    void roomPresence(const QString& roomId, const QString& expert, const QString& role, const QString& affiliation);
    void roomMessage(const QString& roomId, const QString& from, int count);
    void roomStatus(const QString& roomId, int status);
    void roomOffer(const QString& roomId, const Offer& offer);
    void roomFeedback(const QString& roomId, int stars);

    // system signals
    void connected(int role);
    void disconnected();

    void user(const Member&);
    void tag(TaskTag* tag);
    void pattern(AnswerPattern* pattern);
    void chatTemplate(const QString& type, const QString& pattern);

    void presenceChanged(const QString& user, bool available);

    void xmppError(const QString& error);

    void jidChanged(const QString&);

    void membersChanged();

public slots:
    void onError(QXmppClient::Error err);
    void onPresence(const QXmppPresence& presence);
    void onIQ(const QXmppIq& iq);
    void onMessage(const QXmppMessage& msg, const QString& id = QString());
    void onDisconnected() {
        disconnect();
    }
    void onConnected();

private slots:
    void onRegistered() {
        connect();
    }

public:
    explicit ExpLeagueConnection(Profile* profile, QObject* parent = 0);

private:
    void sendCommand(const QString& cmd, Offer* context = 0, std::function<void (QDomElement* element)> init = 0);

private:
    QXmppClient* m_client = 0;
    Profile* m_profile = 0;
    QString m_jid;
    QMap<QString, Member*> m_members_cache;
    QSet<QString> m_history_requested;
    int m_tasks_available = 0;
};

class Registrator: public QXmppClientExtension {
    Q_OBJECT

public:
    explicit Registrator(const Profile* profile, QObject* parent);

    virtual ~Registrator() {
        qDebug() << "Registrator stopped";
        connection.disconnect();
    }

    void start();

signals:
    void registered(const QString& jid);
    void error(const QString& error);

private slots:
    void disconnected();

protected:
    bool handleStanza(const QDomElement &stanza);

private:
    QXmppConfiguration config;
    QXmppClient connection;
    QString m_registrationId;
    bool m_reconnecting = false;
    const Profile* m_profile = 0;
};
}

QXmppElement parse(const QString& str);
}

QDebug operator<<(QDebug dbg, const QDomNode& node);

#endif // PROTOCOL_H

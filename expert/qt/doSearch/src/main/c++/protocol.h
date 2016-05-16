#ifndef PROTOCOL_H
#define PROTOCOL_H

#include <string>
#include <memory>
#include <stdlib.h>

#include <QMap>

#include <QDomElement>
#include <QApplication>
#include <QPixmap>

#include "QXmppClient.h"
#include "QXmppClientExtension.h"

#include "profile.h"
#include "task.h"

namespace expleague {

class Member;
class TaskTag;
class AnswerPattern;

namespace xmpp {

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
public:
    Profile* profile() {
        return m_profile;
    }

    QString jid() {
        return m_jid;
    }

    bool valid() {
        return client.isAuthenticated();
    }

public:
    void connect();
    void disconnect();

    Member* find(const QString& id);

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

    void sendMessage(const QString& to, const QString&);
    void sendProgress(const QString& to, const Progress& progress);
    void sendAnswer(const QString& roomId, const QString& answer);

    void sendUserRequest(const QString&);

signals:
    void connected();
    void disconnected();

    void receiveCheck(Offer* task);
    void receiveInvite(Offer* task);
    void receiveResume(Offer* task);
    void receiveCancel(Offer* offer);

    void receiveMessage(const QString& room, const QString& from, const QString&);
    void receiveImage(const QString& room, const QString& from, const QUrl&);
    void receiveAnswer(const QString& room, const QString& from, const QString&);
    void receiveProgress(const QString& room, const QString& from, const Progress&);

    void receiveUser(const Member&);
    void receiveTag(TaskTag* tag);
    void receivePattern(AnswerPattern* pattern);

    void xmppError(const QString& error);

    void jidChanged(const QString&);

public slots:
    void error(QXmppClient::Error err);
    void iqReceived(const QXmppIq &iq);
    void messageReceived(const QXmppMessage &msg);
    void disconnectedSlot() {
        disconnected();
    }
    void connectedSlot();
private slots:
    void registered() {
        connect();
    }

    void error(const QString& error) {
        xmppError(error);
    }

public:
    explicit ExpLeagueConnection(Profile* profile, QObject* parent = 0);

private:
    void sendCommand(const QString& cmd, Offer* context = 0);

private:
    QXmppClient client;
    Profile* m_profile;
    QString m_jid;
    QMap<QString, Member*> m_members_cache;
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

protected:
    bool handleStanza(const QDomElement &stanza);

private:
    QXmppConfiguration config;
    QXmppClient connection;
    QString m_registrationId;
    const Profile* m_profile;
};
}

QXmppElement parse(const QString& str);
}

QDebug operator<<(QDebug dbg, const QDomNode& node);

#endif // PROTOCOL_H

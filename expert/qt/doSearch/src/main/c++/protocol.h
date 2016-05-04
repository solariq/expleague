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
namespace xmpp {

class Progress: QObject {
    Q_OBJECT

public:
    enum Operation {
      PO_ADD,
      PO_REMOVE,
      PO_VISIT
    };

    enum Target {
      PO_PATTERNS,
      PO_TAGS,
      PO_PHONE,
      PO_URL,
    };

    Operation operation;
    Target target;
    QString name;
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

public:
    void connect();
    void disconnect();
    void sendOk(Offer*);
    void sendAccept(Offer*);
    void sendCancel(Offer*);
    void sendMessage(const QString&);
    void sendProgress(const Progress&);
    void sendAnswer(const QString&);

signals:
    void connected();
    void disconnected();

    void receiveCheck(Offer* task);

    void receiveInvite(Offer* task);
    void receiveResume(Offer* task);

    void receiveCancel();
    void receiveMessage(const QString&);
    void receiveImage(const QPixmap&);
    void receiveAnswer(const QString&);
    void receiveProgress(const Progress&);

    void jidChanged(const QString&);

public slots:
    void error(QXmppClient::Error err);
    void iqReceived(const QXmppIq &iq);
    void messageReceived(const QXmppMessage &msg);
    void disconnectedSlot() {
        disconnected();
    }
    void connectedSlot() {
        connected();
        qDebug() << "Connected as " << client.configuration().jid();
        m_jid = client.configuration().jid();
        jidChanged(m_jid);
        { // restore configuration for reconnect purposes
            client.configuration().setJid(profile()->deviceJid());
            client.configuration().setResource("doSearchQt-" + QApplication::applicationVersion() + "/expert");
        }
    }
public:
    explicit ExpLeagueConnection(Profile* profile, QObject* parent = 0);

private:
    void sendCommand(const QString& cmd, Offer* context = 0);

private:
    QXmppClient client;
    Profile* m_profile;
    QString m_jid;
};

QXmppElement parse(const QString& str);
}}

QDebug operator<<(QDebug dbg, const QDomNode& node);

#endif // PROTOCOL_H

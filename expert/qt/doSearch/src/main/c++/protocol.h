#ifndef PROTOCOL_H
#define PROTOCOL_H

#include <string>
#include <memory>
#include <stdlib.h>

#include <QDomElement>
#include <QApplication>

#include "QXmppClient.h"
#include "QXmppClientExtension.h"

#include "profile.h"

namespace expleague {
namespace xmpp {

QXmppElement parse(const QString& str);
void registerExpert(QSharedPointer<Profile>);

class ExpLeagueConnection: public QObject {
    Q_OBJECT
public:
    void connect() {
        registrationId.reset(0);
        reconnecting = false;

        QXmppConfiguration config;
        config.setJid("aaab@localhost");
        config.setPassword("aaab");
        config.setHost("localhost");
        config.setPort(5222);
        config.setResource("doSearchQt-" + QApplication::applicationVersion() + "/expert");
        config.setAutoReconnectionEnabled(true);
        config.setKeepAliveInterval(55);
        client.connectToServer(config);
    }

    void disconnect() {
        client.disconnectFromServer();
    }

public:
    explicit ExpLeagueConnection(const Profile& profile, QXmppClient& client);
    virtual ~ExpLeagueConnection();

public slots:

    void error(QXmppClient::Error err);
    void iqReceived(const QXmppIq &iq);
    void messageReceived(const QXmppMessage &msg);
    void disconnected();
//    void connected();

private:
    QXmppClient& client;
    const Profile& profile;
    std::auto_ptr<QString> registrationId;
    bool reconnecting = false;
};

bool connect(const Profile& profile);

class Registrator: public QXmppClientExtension {
    Q_OBJECT
public:
    explicit Registrator(QSharedPointer<Profile> profileRef);

    virtual ~Registrator() {
        qDebug() << "Registrator stopped";
    }

    void start();
protected:
    bool handleStanza(const QDomElement &stanza);

private:
    QSharedPointer<Profile> profile;
    QXmppConfiguration config;
    QXmppClient connection;
    QString m_registrationId;
};

}}

#endif // PROTOCOL_H

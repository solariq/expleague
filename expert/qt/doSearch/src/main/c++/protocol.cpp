#include <memory>

#include <QMutex>
#include <QString>
#include <QSharedDataPointer>
#include <QDomDocument>

#include "QXmppClient.h"
#include "QXmppLogger.h"
#include "protocol.h"

namespace expleague {
namespace xmpp {
static std::auto_ptr<ExpLeagueConnection> connection;
static QXmppClient client;

void registerExpert(QSharedPointer<Profile> profile) {
    Registrator* registrator = new Registrator(profile);
    registrator->start();
}

bool connect(const Profile& profile) {
    connection.reset(new ExpLeagueConnection(profile, client));

    QObject::connect(&client, SIGNAL(error(QXmppClient::Error)), &*connection, SLOT(error(QXmppClient::Error)));
    QObject::connect(&client, SIGNAL(connected()), &*connection, SLOT(connected()));
    QObject::connect(&client, SIGNAL(iqReceived(QXmppIq)), &*connection, SLOT(iqReceived(QXmppIq)));
    QObject::connect(&client, SIGNAL(messageReceived(QXmppMessage)), &*connection, SLOT(messageReceived(QXmppMessage)));

    connection->connect();
    return true;
}

ExpLeagueConnection::ExpLeagueConnection(const Profile &p, QXmppClient& c): client(c), profile(p) {
    client.logger()->setLoggingType(QXmppLogger::StdoutLogging);
    client.logger()->setMessageTypes(QXmppLogger::AnyMessage);
}

ExpLeagueConnection::~ExpLeagueConnection() {}

void ExpLeagueConnection::error(QXmppClient::Error) {
    QXmppIq reg(QXmppIq::Type::Set);
    QXmppElementList parameters;
    parameters += parse(
                "<query xmlns=\"jabber:iq:register\">"
                "  <username>aaab</username>"
                "  <password>aaab</password>"
                "</query>"
                );
    reg.setExtensions(parameters);
    registrationId.reset(new QString(reg.id()));
    client.sendPacket(reg);
}

void ExpLeagueConnection::iqReceived(const QXmppIq &iq) {

}

void ExpLeagueConnection::messageReceived(const QXmppMessage &msg) {

}

void ExpLeagueConnection::disconnected() {
    if (reconnecting) {
        this->connect();
    }
}

QXmppElement parse(const QString& str) {
    QDomDocument document;
    document.setContent(str, true);
    return QXmppElement(document.documentElement());
}


void Registrator::start() {
    qDebug() << "Starting registration of " << profile->deviceJid();
    config.setJid(profile->deviceJid());
    config.setPassword(profile->passwd());
    config.setHost(profile->domain());
    config.setPort(5222);
    config.setResource("expert");
    config.setAutoReconnectionEnabled(false);
    config.setKeepAliveInterval(55);
    connection.addExtension(this);
    connection.connectToServer(config);
    qDebug() << "Connection started";
}

Registrator::Registrator(QSharedPointer<Profile> profileRef): profile(profileRef) {
//    connect(&client, SIGNAL(connected()), SLOT(connected()));
//    connect(&client, SIGNAL(iqReceived(QXmppIq)), SLOT(iqReceived(QXmppIq)));
//    connect(&client, SIGNAL(error(QXmppClient::Error)), SLOT(error(QXmppClient::Error)));
}

QDebug operator<<(QDebug dbg, const QDomNode& node);

bool Registrator::handleStanza(const QDomElement &stanza) {
    client()->configuration().setAutoReconnectionEnabled(false);

    if (stanza.tagName() == "failure") {
        if (!stanza.firstChildElement("not-authorized").isNull()) {
            QDomElement text = stanza.firstChildElement("text");
            if (!text.isNull() && text.text() == "No such user") {
                QXmppIq reg(QXmppIq::Type::Set);
                qDebug() << "No such user found, registering one";
                QXmppElement query = parse("<query xmlns=\"jabber:iq:register\">"
                                           "  <username>" + profile->deviceJid() + "</username>"
                                           "  <password>" + profile->passwd() + "</password>"
                                           "  <misc>" + profile->avatar().toString() + "</misc>"
                                           "  <name>" + profile->name() + "</name>"
                                           "  <email>" + "doSearchQt/" + QApplication::applicationVersion() + "/expert</email>"
                                           "  <nick>" + QString::number(profile->sex()) + "/expert</nick>"
                                           "</query>");
                reg.setExtensions(QXmppElementList() += query);
                m_registrationId = reg.id();
                client()->sendPacket(reg);
                return true;
            }
            else if (!text.isNull() && text.text().contains("Mismatched response")) {
                qDebug() << "Incorrect password";
                profile->error(tr("Неверный пароль") + stanza.text());
                client()->disconnectFromServer();
                return true;
            }
        }
        else {
            profile->error(tr("Не удалось зарегистрировать пользователя: ") + stanza.text());
            client()->disconnectFromServer();
            return true;
        }
    }
    else if (stanza.tagName() == "iq" && !stanza.firstChildElement("bind").isNull()) {
        QDomElement bind = stanza.firstChildElement("bind");
        QString jid = bind.firstChildElement("jid").text();
        profile->jid(jid);
        qDebug() << "Profile profile received name" << jid;
        client()->disconnectFromServer();
        return true;
    }
    else if (stanza.tagName() == "iq" && stanza.attribute("id") == m_registrationId) {
        if (stanza.attribute("type") == "result") {
            qDebug() << "Profile successfully registered. Reconnecting..." << stanza;
            client()->configuration().setAutoReconnectionEnabled(true);
            client()->disconnectFromServer();
        }
        else if (stanza.attribute("type") == "error") {
            profile->error(tr("Не удалось зарегистрировать пользователя: ") + stanza.text());
            client()->disconnectFromServer();
            qDebug() << "Unable to register profile" << stanza;
        }
        return true;
    }
    qDebug() << stanza;
    return false;
}

QDebug operator<<(QDebug dbg, const QDomNode& node)
{
  QString s;
  QTextStream str(&s, QIODevice::WriteOnly);
  node.save(str, 2);
  dbg << qPrintable(s);
  return dbg;
}

}}


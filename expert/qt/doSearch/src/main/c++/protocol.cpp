#include <memory>

#include <QMutex>
#include <QString>
#include <QSharedDataPointer>
#include <QDomDocument>
#include <QDomElement>

#include "QXmppClient.h"
#include "QXmppLogger.h"
#include "QXmppMessage.h"

#include "protocol.h"
#include "league.h"

namespace expleague {
namespace xmpp {
ExpLeagueConnection::ExpLeagueConnection(Profile* p, QObject* parent): QObject(parent), m_profile(p) {
    QObject::connect(&client, SIGNAL(error(QXmppClient::Error)), this, SLOT(error(QXmppClient::Error)));
    QObject::connect(&client, SIGNAL(connected()), this, SLOT(connectedSlot()));
    QObject::connect(&client, SIGNAL(disconnected()), this, SLOT(disconnectedSlot()));
    QObject::connect(&client, SIGNAL(iqReceived(QXmppIq)), this, SLOT(iqReceived(QXmppIq)));
    QObject::connect(&client, SIGNAL(messageReceived(QXmppMessage)), this, SLOT(messageReceived(QXmppMessage)));

    client.logger()->setLoggingType(QXmppLogger::StdoutLogging);
    client.logger()->setMessageTypes(QXmppLogger::AnyMessage);
}

void ExpLeagueConnection::connect() {
    QXmppConfiguration config;
    config.setJid(m_profile->deviceJid());
    config.setPassword(m_profile->passwd());
    config.setHost(m_profile->domain());
    config.setPort(5222);
    config.setResource("doSearchQt-" + QApplication::applicationVersion() + "/expert");
    config.setAutoReconnectionEnabled(true);
    config.setKeepAliveInterval(55);
    client.connectToServer(config);
}

void ExpLeagueConnection::disconnect() {
    client.disconnectFromServer();
}


void ExpLeagueConnection::error(QXmppClient::Error error) {
    qWarning() << "XMPP error: " << error;
}

void ExpLeagueConnection::iqReceived(const QXmppIq &) {

}

enum Command {
    ELC_RESUME,
    ELC_CANCEL,
    ELC_INVITE,
    ELC_CHECK
};

const QString EXP_LEAGUE_NS = "http://expleague.com/scheme";

void ExpLeagueConnection::messageReceived(const QXmppMessage& msg) {
    Command cmd = ELC_CHECK;
    Offer* task;
    foreach (const QXmppElement& element, msg.extensions()) {
        QDomElement xml = element.sourceDomElement();
        if (xml.namespaceURI() == EXP_LEAGUE_NS) {
            if (xml.localName() == "resume") {
                cmd = ELC_RESUME;
            }
            else if (xml.localName() == "cancel") {
                cmd = ELC_CANCEL;
            }
            else if (xml.localName() == "invite") {
                cmd = ELC_INVITE;
            }
            else if (xml.localName() == "offer") {
                task = new Offer(xml, this);
            }
        }
        qDebug() << element.sourceDomElement();
    }
    if (task) {
        switch (cmd) {
        case ELC_CANCEL:
            receiveCancel();
            break;
        case ELC_CHECK:
            receiveCheck(task);
            sendOk(task);
            break;
        case ELC_INVITE:
            receiveInvite(task);
            break;
        case ELC_RESUME:
            receiveResume(task);
            break;
        }
    }
}

void ExpLeagueConnection::sendCommand(const QString& command, Offer* task) {
    QXmppMessage msg("", profile()->domain());
    QDomDocument holder;
    QXmppElementList protocol;

    protocol.append(QXmppElement(holder.createElementNS(EXP_LEAGUE_NS, command)));
    protocol.append(QXmppElement(task->toXml()));
    msg.setExtensions(protocol);
    client.sendPacket(msg);
}

void ExpLeagueConnection::sendOk(Offer* task) {
    sendCommand("ok", task);
}

void ExpLeagueConnection::sendCancel(Offer *task) {
    sendCommand("cancel", task);
}

void ExpLeagueConnection::sendAccept(Offer *task) {
    sendCommand("start", task);
}

void ExpLeagueConnection::sendMessage(const QString& text) {
    Offer* offer = static_cast<const expleague::League*>(parent())->task()->offer();
    if (offer)
        client.sendMessage(offer->roomJid(), text);
    else
        qWarning() << "Unable to send message when no task is active";
}

void ExpLeagueConnection::sendAnswer(const QString& text) {
    QXmppMessage msg("", profile()->domain());
    QDomDocument holder;
    QXmppElementList protocol;

    QDomElement answer = holder.createElementNS(EXP_LEAGUE_NS, "answer");
    answer.setNodeValue(text);
    protocol.append(QXmppElement(answer));
    msg.setExtensions(protocol);
    client.sendPacket(msg);
}

QXmppElement parse(const QString& str) {
    QDomDocument document;
    document.setContent(str, true);
    return QXmppElement(document.documentElement());
}

}

Offer::Offer(QDomElement xml, QObject *parent): QObject(parent) {
    if (xml.attribute("urgency") == "day")
        m_urgency = Offer::Urgency::TU_DAY;
    else if (xml.attribute("urgency") == "asap")
        m_urgency = Offer::Urgency::TU_ASAP;
    m_room = xml.attribute("room");
    m_client = xml.attribute("client");
    m_started = QDateTime::fromTime_t(uint(xml.attribute("started").toDouble()));
    m_local = xml.attribute("local") == "true";
    for(int i = 0; i < xml.childNodes().length(); i++) {
        QDomElement element = xml.childNodes().at(i).toElement();
        if (element.isNull())
            continue;
        if (element.tagName() == "topic") {
            qDebug() << "Topic " << element.text();
            m_topic = element.text();
        }
        else if (element.tagName() == "location") {
            m_location.reset(new QGeoCoordinate(
                                 element.attribute("latitude").toDouble(),
                                 element.attribute("longitude").toDouble()));
            qDebug() << "Location " << *m_location;
        }
        else if (element.tagName() == "image") {
            m_attachments.append(QUrl(element.text()));
        }
        else if (element.tagName() == "experts-filter") {
            QDomElement rejected = element.firstChildElement("reject");
            QDomElement accepted = element.firstChildElement("accept");
            QDomElement prefer = element.firstChildElement("prefer");
            // TODO: finish
//                       if (!rejected.isNull()) {
//                           rejected
//                       }
        }

    }

}

QString urgencyToString(Offer::Urgency u) {
    switch (u) {
    case Offer::Urgency::TU_DAY:
        return "day";
    case Offer::Urgency::TU_ASAP:
        return "asap";
    }
    return "";
}

QDomElement Offer::toXml() {
    QDomDocument holder;
    QDomElement result = holder.createElementNS(xmpp::EXP_LEAGUE_NS, "offer");
    result.setAttribute("room", m_room);
    result.setAttribute("client", m_client);
    result.setAttribute("local", m_local ? "true" : "false");
    result.setAttribute("urhency", urgencyToString(m_urgency));
    QDomElement topic = holder.createElementNS(xmpp::EXP_LEAGUE_NS, "topic");
    topic.appendChild(holder.createTextNode(m_topic));
    result.appendChild(topic);
    if (m_location) {
        QDomElement location = holder.createElementNS(xmpp::EXP_LEAGUE_NS, "location");
        location.setAttribute("latitude", m_location->latitude());
        location.setAttribute("longitude", m_location->longitude());
        result.appendChild(location);
    }
    foreach(const QUrl& url, m_attachments) {
        QDomElement attachment = holder.createElementNS(xmpp::EXP_LEAGUE_NS, "image");
        attachment.appendChild(holder.createTextNode(url.toString()));
        result.appendChild(attachment);
    }

    return QDomElement(result);
}

}

QDebug operator<<(QDebug dbg, const QDomNode& node) {
  QString s;
  QTextStream str(&s, QIODevice::WriteOnly);
  node.save(str, 2);
  dbg << qPrintable(s);
  return dbg;
}

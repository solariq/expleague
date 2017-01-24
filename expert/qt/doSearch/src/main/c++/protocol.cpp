#include <memory>

#include <QMutex>
#include <QString>
#include <QSharedDataPointer>
#include <QTimer>
#include <QApplication>

#include <QDomDocument>
#include <QDomElement>

#include "QXmppClient.h"
#include "QXmppLogger.h"
#include "QXmppMessage.h"

#include "protocol.h"
#include "league.h"
#include "dosearch.h"

namespace expleague {
namespace xmpp {

const QString EXP_LEAGUE_NS = "http://expleague.com/scheme";

ExpLeagueConnection::ExpLeagueConnection(Profile* p, QObject* parent): QObject(parent), m_client(new QXmppClient(this)), m_profile(p) {
    QObject::connect(m_client, SIGNAL(error(QXmppClient::Error)), this, SLOT(onError(QXmppClient::Error)));
    QObject::connect(m_client, SIGNAL(connected()), this, SLOT(onConnected()));
    QObject::connect(m_client, SIGNAL(disconnected()), this, SLOT(onDisconnected()));
    QObject::connect(m_client, SIGNAL(iqReceived(QXmppIq)), this, SLOT(onIQ(QXmppIq)));
    QObject::connect(m_client, SIGNAL(presenceReceived(QXmppPresence)), this, SLOT(onPresence(QXmppPresence)));
    QObject::connect(m_client, SIGNAL(messageReceived(QXmppMessage)), this, SLOT(onMessage(QXmppMessage)));

    m_client->logger()->setLoggingType(QXmppLogger::StdoutLogging);
    m_client->logger()->setMessageTypes(QXmppLogger::WarningMessage);
    {
        QXmppConfiguration config;
        config.setJid(m_profile->deviceJid());
        config.setPassword(m_profile->passwd());
        config.setHost(m_profile->domain());
        config.setPort(5222);
        config.setResource("doSearchQt-" + QApplication::applicationVersion() + "/expert");
        config.setAutoReconnectionEnabled(false);
        config.setKeepAliveInterval(55);
        m_client->configuration() = config;

        foreach(QXmppClientExtension* ext, m_client->extensions()) {
            m_client->removeExtension(ext);
        }
    }
}

void ExpLeagueConnection::connect() {
    m_client->connectToServer(m_client->configuration());
}

void ExpLeagueConnection::disconnect() {
    if (m_client->state() != QXmppClient::DisconnectedState)
        m_client->disconnectFromServer();
    int oldValue = m_tasks_available;
    m_tasks_available = 0;
    emit tasksAvailableChanged(oldValue);
    emit disconnected();
}

void ExpLeagueConnection::onError(QXmppClient::Error error) {
    if (error == QXmppClient::Error::XmppStreamError) {
        qWarning() << "XMPP stream error: " << m_client->xmppStreamError();
        if (m_client->xmppStreamError() == QXmppStanza::Error::NotAuthorized) { // trying to register
            Registrator* reg = new Registrator(profile(), this);
            QObject::connect(reg, SIGNAL(onRegistered(QString)), this, SLOT(onRegistered()));
            QObject::connect(reg, SIGNAL(error(QString)), this, SLOT(error(QString)));
            reg->start();
        }
    }
    else emit disconnected();
}

void ExpLeagueConnection::onPresence(const QXmppPresence& presence) {
    foreach(const QXmppElement& ext, presence.extensions()) {
        QDomElement item = ext.sourceDomElement();
        if (item.namespaceURI() == EXP_LEAGUE_NS && item.nodeName() == "status") {
            int tasks = item.attribute("starving-tasks").toInt();
            if (tasks != m_tasks_available) {
                int oldValue = m_tasks_available;
                m_tasks_available = item.attribute("starving-tasks").toInt();
                emit tasksAvailableChanged(oldValue);
            }
        }
    }
    if (xmpp::user(presence.from()) == "global-chat") {
        QString user = xmpp::resource(presence.from());
        emit presenceChanged(user, presence.type() == QXmppPresence::Available);
    }
}

void ExpLeagueConnection::onIQ(const QXmppIq& iq) {
    foreach(const QXmppElement& ext, iq.extensions()) {
        if (ext.sourceDomElement().namespaceURI() == "jabber:iq:roster") {
            QDomElement xml = ext.sourceDomElement();
            for(int i = 0; i < xml.childNodes().length(); i++) {
                QDomElement element = xml.childNodes().at(i).toElement();
                if (element.isNull())
                    continue;
                if (element.tagName() == "item") {
                    QString id = element.attribute("jid").section('@', 0, 0);
                    Member* member = find(id);
                    if (element.hasAttribute("name")) {
                        member->setName(element.attribute("name"));
                    }
                    QDomElement expert = element.firstChildElement("expert");
                    if (!expert.isNull()) {
                        member->setStatus(expert.attribute("available") == "true" ? Member::ONLINE : Member::OFFLINE);
                        QDomElement avatar = expert.firstChildElement("avatar");
                        if (!avatar.isNull()) {
                            member->setAvatar(avatar.text());
                        }
                    }
                }
            }
        }
        else if (ext.sourceDomElement().namespaceURI() == EXP_LEAGUE_NS + "/tags") {
            QDomElement xml = ext.sourceDomElement();
            for(int i = 0; i < xml.childNodes().length(); i++) {
                QDomElement element = xml.childNodes().at(i).toElement();
                if (element.isNull())
                    continue;
                if (element.tagName() == "tag")
                    tag(new TaskTag(element.text(), element.hasAttribute("icon") ? element.attribute("icon") : "qrc:/unknown_topic.png"));
            }
        }
        else if (ext.sourceDomElement().namespaceURI() == EXP_LEAGUE_NS + "/patterns") {
            QDomElement xml = ext.sourceDomElement();
            for(int i = 0; i < xml.childNodes().length(); i++) {
                QDomElement element = xml.childNodes().at(i).toElement();
                if (element.isNull())
                    continue;
                if (element.tagName() == "pattern") {
                    QDomNodeList icon = element.elementsByTagName("icon");
                    pattern(new AnswerPattern(element.attribute("name"), icon.count() > 0 ? icon.at(0).toElement().text() : "qrc:/unknown_pattern.png", element.elementsByTagName("body").at(0).toElement().text()));
                }
            }
        }
    }
}

enum Command {
    ELC_RESUME,
    ELC_CANCEL,
    ELC_INVITE,
    ELC_CHECK,
    ELC_ENTER,
    ELC_EXIT,
    ELC_ROOM_CREATE,
    ELC_ROOM_STATUS,
    ELC_ROOM_MESSAGE
};

void ExpLeagueConnection::onMessage(const QXmppMessage& msg) {
    Command cmd = ELC_CHECK;
    std::unique_ptr<Offer> offer = 0;
    QString text;
    QString sender;
    Progress progress;
    QUrl image;
    int status;
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
                offer.reset(new Offer(xml));
            }
            else if (xml.localName() == "answer") {
                text = xml.text();
            }
            else if (xml.localName() == "progress") {
                progress = Progress::fromXml(xml);
            }
            else if (xml.localName() == "image") {
                image = QUrl(xml.text());
            }
            else if (xml.localName() == "enter") {
                cmd = ELC_ENTER;
                sender = xml.attribute("expert");
            }
            else if (xml.localName() == "exit") {
                cmd = ELC_EXIT;
                sender = xml.attribute("expert");
            }
            else if (xml.localName() == "room-state-changed") {
                cmd = ELC_ROOM_STATUS;
                status = xml.attribute("state").toInt();
            }
            else if (xml.localName() == "room-created") {
                cmd = ELC_ROOM_CREATE;
                sender = xml.attribute("client");
                text = xml.text();
                status = xml.attribute("state").toInt();
            }
            else if (xml.localName() == "room-message-received") {
                cmd = ELC_ROOM_MESSAGE;
                sender = xml.attribute("from").toInt();
            }

        }
    }

    QString room;
    QString from;
    if (msg.from().indexOf("muc.") >= 0 || msg.from().startsWith("global-chat@")) {
        from = xmpp::resource(msg.from());
        room = xmpp::user(msg.from());
    }
    else {
        from = xmpp::user(msg.from());
        room = xmpp::user(msg.to());
    }

    if (!!offer) {
        if (offer->room() == "")
            offer->m_room = room + "@muc." + xmpp::domain(msg.from());
        if (offer->client() == "")
            offer->m_client = from + "@" + xmpp::domain(msg.from());
    }

    if (!!offer && (!msg.from().contains('@') || cmd != ELC_CHECK)) { // message from system
        switch (cmd) {
        case ELC_CANCEL:
            cancel(*offer);
            break;
        case ELC_CHECK:
            check(*offer);
            break;
        case ELC_INVITE:
            invite(*offer);
            break;
        case ELC_RESUME:
            resume(*offer);
            break;
        default:
            qWarning() << "Unhandeled command: " << cmd << " while offer received";
        }
    }
    else {
        if (room == "global-chat") {
            switch(cmd) {
            case ELC_ENTER:
                assignment(from, sender, League::ADMIN);
                break;
            case ELC_EXIT:
                assignment(from, sender, League::NONE);
                break;
            case ELC_ROOM_STATUS:
                roomStatus(from, status);
                break;
            case ELC_ROOM_CREATE:
                roomStarted(from, text, sender);
                break;
            case ELC_ROOM_MESSAGE:
                messageNotification(from, sender);
                break;
            default:
                qWarning() << "Unhandeled command: " << cmd << " in global chat: " << msg;
            }
        }
        else if (!!offer) {
            this->offer(room, msg.id(), *offer);
        }
        else if (!text.isEmpty()) {
            this->answer(room, msg.id(), from, text);
        }
        else if (!progress.empty()) {
            this->progress(room, msg.id(), from, progress);
        }
        else if (image.isValid()) {
            this->image(room, msg.id(), from, image);
        }
        else if (!msg.body().isEmpty()) {
            this->message(room, msg.id(), from, msg.body());
        }
    }
    if (msg.isReceiptRequested()) {
        QXmppMessage receipt;
        receipt.setType(QXmppMessage::Normal);
        receipt.setReceiptId(msg.id());
        m_client->sendPacket(receipt);
    }
}

void ExpLeagueConnection::onConnected() {
    QString boundJid = m_client->configuration().jid();
    if (boundJid.endsWith("/admin"))
        emit connected(2);
    else if (boundJid.endsWith("/expert"))
        emit connected(1);
    else
        emit connected(1); // TODO: connect as normal client when there will be no compatibility issues
//    qDebug() << "Connected as " << m_client->configuration().jid();
    m_jid = m_client->configuration().jid();
    jidChanged(m_jid);
    { // requesting tags
        QXmppIq iq;
        QDomDocument holder;
        QXmppElementList protocol;
        protocol.append(QXmppElement(holder.createElementNS(EXP_LEAGUE_NS + "/tags", "query")));
        iq.setExtensions(protocol);
        m_client->sendPacket(iq);
    }

    { // requesting patterns
        QXmppIq iq;
        QDomDocument holder;
        QXmppElementList protocol;
        QDomElement query = holder.createElementNS(EXP_LEAGUE_NS + "/patterns", "query");
        query.setAttribute("intent", "work");
        protocol.append(QXmppElement(query));
        iq.setExtensions(protocol);
        m_client->sendPacket(iq);
    }

    { // restore configuration for reconnect purposes
        m_client->configuration().setJid(profile()->deviceJid());
        m_client->configuration().setResource("doSearchQt-" + QApplication::applicationVersion() + "/expert");
    }
}


Member* ExpLeagueConnection::find(const QString &id) {
    QMap<QString, Member*>::iterator found = m_members_cache.find(id);
    if (found != m_members_cache.end())
        return found.value();
    Member* member = new Member(id, this);
    sendUserRequest(id);
    m_members_cache.insert(id, member);
    return member;
}

void ExpLeagueConnection::sendCommand(const QString& command, Offer* task, std::function<void (QDomElement* element)> init) {
    QXmppMessage msg("", profile()->domain());
    QDomDocument holder;
    QXmppElementList protocol;
    QDomElement commandXml = holder.createElementNS(EXP_LEAGUE_NS, command);
    if (init)
        init(&commandXml);
    protocol.append(QXmppElement(commandXml));
    protocol.append(QXmppElement(task->toXml()));
    msg.setExtensions(protocol);
    m_client->sendPacket(msg);
}

void ExpLeagueConnection::sendSuspend(Offer *offer, long seconds) {
    sendCommand("suspend", offer, [this, seconds](QDomElement* command) {
        double now = QDateTime::currentMSecsSinceEpoch() / (double)1000;
        command->setAttribute("start", QString::number(now, 'f', 12));
        command->setAttribute("end", QString::number(now + seconds, 'f', 12));
    });
}

void ExpLeagueConnection::sendMessage(const QString& to, const QString& text) {
    QXmppMessage msg(jid(), to, text);
    msg.setType(QXmppMessage::GroupChat);
    m_client->sendPacket(msg);
    onMessage(msg);
}

void ExpLeagueConnection::sendAnswer(const QString& room, int difficulty, int success, bool extraInfo, const QString& text) {
    QXmppMessage msg("", room);
    msg.setType(QXmppMessage::GroupChat);

    QDomDocument holder;
    QXmppElementList protocol;
    QDomElement answer = holder.createElementNS(EXP_LEAGUE_NS, "answer");
    answer.setAttribute("difficulty", difficulty);
    answer.setAttribute("success", success);
    answer.setAttribute("extra-info", extraInfo);
    answer.appendChild(holder.createTextNode(text));
    protocol.append(QXmppElement(answer));
    msg.setExtensions(protocol);
    m_client->sendPacket(msg);
}

void ExpLeagueConnection::sendProgress(const QString &to, const Progress &progress) {
    QXmppMessage msg("", to);
    msg.setType(QXmppMessage::Normal);

    QDomDocument holder;
    QXmppElementList protocol;
    protocol.append(QXmppElement(progress.toXml()));
    msg.setExtensions(protocol);
    m_client->sendPacket(msg);
}

void ExpLeagueConnection::sendUserRequest(const QString &id) {
    QXmppIq iq;
    QDomDocument holder;
    QXmppElementList protocol;

    QDomElement query = holder.createElementNS("jabber:iq:roster", "query");
    QDomElement item = holder.createElementNS("jabber:iq:roster", "item");
    item.setAttribute("jid", id + "@" + profile()->domain());
    query.appendChild(item);
    protocol.append(QXmppElement(query));
    iq.setExtensions(protocol);
    m_client->sendPacket(iq);
}

void ExpLeagueConnection::sendPresence(const QString& room) {
    QXmppPresence presence(QXmppPresence::Available);
    presence.setTo(xmpp::user(room) + "@muc." + profile()->domain());
    m_client->sendPacket(presence);
}

void ExpLeagueConnection::sendOffer(const Offer& offer) {
    QXmppMessage msg("", to);
    msg.setType(QXmppMessage::Normal);

    QDomDocument holder;
    QXmppElementList protocol;
    protocol.append(QXmppElement(offer.toXml()));
    msg.setExtensions(protocol);
    m_client->sendPacket(msg);
}

Progress Progress::fromXml(const QDomElement& xml) {
    QString name;
    Operation operation = PO_VISIT;
    Target target = PO_URL;

    QDomElement change = xml.elementsByTagName("change").at(0).toElement();
    if (!change.isNull()) {
        name = change.text();
        QString operationStr = change.attribute("operation");
        if (operationStr == "add")
            operation = Progress::PO_ADD;
        else if (operationStr == "remove")
            operation = Progress::PO_REMOVE;
        else if (operationStr == "visit")
            operation = Progress::PO_VISIT;
        QString targetStr = change.attribute("target");
        if (targetStr == "tag")
            target = Progress::PO_TAG;
        else if (targetStr == "url")
            target = Progress::PO_URL;
        else if (targetStr == "pattern")
            target = Progress::PO_PATTERN;
        else if (targetStr == "phone")
            target = Progress::PO_PHONE;
    }
    return {operation, target, name};
}

QDomElement Progress::toXml() const {
    QDomDocument holder;
    QDomElement result = holder.createElementNS(EXP_LEAGUE_NS, "progress");
    QDomElement change = holder.createElementNS(EXP_LEAGUE_NS, "change");
    {
        QString operation;
        switch (this->operation) {
        case Progress::PO_ADD:
            operation = "add";
            break;
        case Progress::PO_REMOVE:
            operation = "remove";
            break;
        case Progress::PO_VISIT:
            operation = "visit";
            break;
        }
        change.setAttribute("operation", operation);
    }
    {
        QString target;
        switch (this->target) {
        case Progress::PO_TAG:
            target = "tag";
            break;
        case Progress::PO_URL:
            target = "url";
            break;
        case Progress::PO_PATTERN:
            target = "pattern";
            break;
        case Progress::PO_PHONE:
            target = "phone";
            break;
        }
        change.setAttribute("target", target);
    }
    change.appendChild(holder.createTextNode(name));
    result.appendChild(change);
    return result;
}

QXmppElement parse(const QString& str) {
    QDomDocument document;
    document.setContent(str, true);
    return QXmppElement(document.documentElement());
}

Registrator::Registrator(const Profile* profile, QObject* parent): m_profile(profile) {
    setParent(parent);
    QObject::connect(&connection, SIGNAL(disconnected()), SLOT(disconnected()));
    connection.addExtension(this);
}

void Registrator::start() {
//    qDebug() << "Starting registration of " << m_profile->login() + "@" + m_profile->domain();
    config.setJid(m_profile->login() + "@" + m_profile->domain());
    config.setPassword(m_profile->passwd());
    config.setHost(m_profile->domain());
    config.setPort(5222);
    config.setResource("expert");
    config.setAutoReconnectionEnabled(false);
    config.setKeepAliveInterval(55);
    connection.connectToServer(config);
//    qDebug() << "Connection started";
}

bool Registrator::handleStanza(const QDomElement &stanza) {
    client()->configuration().setAutoReconnectionEnabled(false);
    if (stanza.tagName() == "failure") {
        if (!stanza.firstChildElement("not-authorized").isNull()) {
            QDomElement text = stanza.firstChildElement("text");
            if (!text.isNull() && text.text() == "No such user") {
                QXmppIq reg(QXmppIq::Type::Set);
//                qDebug() << "No such user found, registering one";
                QXmppElement query = parse("<query xmlns=\"jabber:iq:register\">"
                                           "  <username>" + m_profile->login() + "</username>"
                                           "  <password>" + m_profile->passwd() + "</password>"
                                           "  <misc>" + m_profile->avatar().toString() + "</misc>"
                                           "  <name>" + m_profile->name() + "</name>"
                                           "  <email>" + "doSearchQt/" + QApplication::applicationVersion() + "/expert</email>"
                                           "  <nick>" + QString::number(m_profile->sex()) + "/expert</nick>"
                                           "</query>");
                reg.setExtensions(QXmppElementList() += query);
                m_registrationId = reg.id();
                client()->sendPacket(reg);
                return true;
            }
            else if (!text.isNull() && text.text().contains("Mismatched response")) {
//                qDebug() << "Incorrect password";
                error(tr("Неверный пароль:\n ") + stanza.text());
                client()->disconnectFromServer();
                return true;
            }
        }
        else {
            error(tr("Не удалось зарегистрировать пользователя:\n ") + stanza.text());
            client()->disconnectFromServer();
            return true;
        }
    }
    else if (stanza.tagName() == "iq" && !stanza.firstChildElement("bind").isNull()) {
        QDomElement bind = stanza.firstChildElement("bind");
        QString jid = bind.firstChildElement("jid").text();
        registered(jid);
//        qDebug() << "Profile profile received name" << jid;
        client()->disconnectFromServer();
        return true;
    }
    else if (stanza.tagName() == "iq" && stanza.attribute("id") == m_registrationId) {
        if (stanza.attribute("type") == "result") {
//            qDebug() << "Profile successfully registered. Reconnecting..." << stanza;
            m_reconnecting = true;
            client()->disconnectFromServer();
        }
        else if (stanza.attribute("type") == "error") {
//            qDebug() << "Unable to register profile" << stanza;
            error(tr("Не удалось зарегистрировать пользователя:\n ") + stanza.text());
            client()->disconnectFromServer();
        }
        return true;
    }
    return false;
}

void Registrator::disconnected() {
    if (m_reconnecting) {
        m_reconnecting = false;
        start();
    }
}

}

void Offer::tick() {
    timeTick();
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

QDomElement Offer::toXml() const {
    QDomDocument holder;
    QDomElement result = holder.createElementNS(xmpp::EXP_LEAGUE_NS, "offer");
    result.setAttribute("room", m_room);
    result.setAttribute("client", m_client);
    result.setAttribute("local", m_local ? "true" : "false");
    result.setAttribute("started", m_started.toTime_t());
    result.setAttribute("urgency", urgencyToString(m_urgency));
    QDomElement topic = holder.createElementNS(xmpp::EXP_LEAGUE_NS, "topic");
    topic.appendChild(holder.createTextNode(m_topic));
    result.appendChild(topic);
    QDomElement comment = holder.createElementNS(xmpp::EXP_LEAGUE_NS, "comment");
    comment.appendChild(holder.createTextNode(m_comment));
    result.appendChild(comment);

    if (m_location.isValid()) {
        QDomElement location = holder.createElementNS(xmpp::EXP_LEAGUE_NS, "location");
        location.setAttribute("latitude", QString::number(m_location->latitude()));
        location.setAttribute("longitude", QString::number(m_location->longitude()));
        result.appendChild(location);
    }

    foreach(const QUrl& url, m_images) {
        QDomElement attachment = holder.createElementNS(xmpp::EXP_LEAGUE_NS, "image");
        attachment.appendChild(holder.createTextNode(url.toString()));
        result.appendChild(attachment);
    }

    foreach(AnswerPattern* pattern, m_patterns) {
        QDomElement patternDom = holder.createElementNS(xmpp::EXP_LEAGUE_NS, "pattern");
        patternDom.appendChild(holder.createTextNode(pattern->name()));
        result.appendChild(patternDom);
    }

    foreach(TaskTag* tag, m_patterns) {
        QDomElement tagDom = holder.createElementNS(xmpp::EXP_LEAGUE_NS, "tag");
        tagDom.appendChild(holder.createTextNode(tag->name()));
        result.appendChild(tagnDom);
    }

    if (!m_filter.isEmpty()) {
        QDomElement filter = holder.createElementNS(xmpp::EXP_LEAGUE_NS, "expert-filter");
        for (auto expert = m_filter.begin(); expert != m_filter.end(); expert++) {
            QDomElement item;
            switch (expert.value()) {
            case Offer::TFT_REJECT:
                item = holder.createElementNS(xmpp::EXP_LEAGUE_NS, "reject");
                break;
            case Offer::TFT_ACCEPT:
                item = holder.createElementNS(xmpp::EXP_LEAGUE_NS, "accept");
                break;
            case Offer::TFT_PREFER:
                item = holder.createElementNS(xmpp::EXP_LEAGUE_NS, "prefer");
                break;
            }
            item.appendChild(holder.createTextNode(expert.key()));
            filter.appendChild(item);
        }
        result.appendChild(filter);
    }

    return QDomElement(result);
}

void Offer::start() const {
    m_timer->start();
}

Offer::Offer(const QString& client,
                   const QString& room,
                   const QString& topic,
                   Urgency urgency,
                   bool local,
                   const QStringList& images,
                   const QMap<QString, FilterType>& filter,
                   QGeoCoordinate location,
                   QDateTime started,
                   QList<TaskTag*> tags,
                   QList<AnswerPattern*> patterns,
                   const QString& comment)
: m_room(room), m_client(client), m_topic(topic), m_urgency(urgency), m_local(local),
  m_images(images), m_filter(filter), m_location(location), m_started(started),
  m_tags(tags), m_patterns(patterns), m_comment(comment)
{}

Offer::Offer(QDomElement xml, QObject *parent): QObject(parent) {
//    qDebug() << "Offer: " << xml;
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
//            qDebug() << "Topic " << element.text();
            m_topic = element.text();
        }
        else if (element.tagName() == "location") {
            m_location = QGeoCoordinate(element.attribute("latitude").toDouble(), element.attribute("longitude").toDouble());
//            qDebug() << "Location " << *m_location;
        }
        else if (element.tagName() == "comment") {
            m_comment = element.text();
        }
        else if (element.tagName() == "image") {
            m_images.append(QUrl(element.text()).path().section('/', -1, -1));
        }
        else if (element.tagName() == "experts-filter") {
            QDomElement rejected = element.firstChildElement("reject");
            QDomElement accepted = element.firstChildElement("accept");
            QDomElement prefer = element.firstChildElement("prefer");
            if (!rejected.isNull())
                m_filter[rejected.text()] = Offer::TFT_REJECT;
            if (!accepted.isNull())
                m_filter[accepted.text()] = Offer::TFT_ACCEPT;
            if (!prefer.isNull())
                m_filter[prefer.text()] = Offer::TFT_PREFER;
        }
        else if (element.tagName() == "patterns") {
            AnswerPattern* const parrern = League::instance()->findPattern(element.text());
            if (pattern)
                m_patterns.append(pattern);
        }
        else if (element.tagName() == "tags") {
            TaskTag* const tag = League::instance()->findTag(element.text());
            if (tag)
                m_tags.append(tag);
        }
    }
    m_timer = new QTimer(this);
    m_timer->setSingleShot(false);
    m_timer->setInterval(500);
    m_timer->setTimerType(Qt::TimerType::CoarseTimer);
    QObject::connect(m_timer, SIGNAL(timeout()), SLOT(tick()));
}
}

QDebug operator<<(QDebug dbg, const QDomNode& node) {
  QString s;
  QTextStream str(&s, QIODevice::WriteOnly);
  node.save(str, 2);
  dbg << qPrintable(s);
  return dbg;
}

#include <QXmlStreamWriter>
QDebug operator<<(QDebug dbg, const QXmppStanza& node) {
  QString s;
  QXmlStreamWriter writer(&s);
  node.toXml(&writer);
  dbg << qPrintable(s);
  return dbg;
}

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

#include "util/region.h"
#include "util/mmath.h"

namespace expleague {
namespace xmpp {

const QString EXP_LEAGUE_NS = "http://expleague.com/scheme";
const QString XMPP_START = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><stream:stream xmlns:stream=\"http://etherx.jabber.org/streams\" version=\"1.0\" xmlns=\"jabber:client\" xml:lang=\"en\" xmlns:xml=\"http://www.w3.org/XML/1998/namespace\">";
const QString XMPP_END = "</stream:stream>";

QString nextId() {
    return randString(10) + "-" + QString::number(QDateTime::currentDateTime().toMSecsSinceEpoch());
}

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
    emit disconnected();
}

void ExpLeagueConnection::onError(QXmppClient::Error error) {
    if (error == QXmppClient::Error::XmppStreamError) {
        qWarning() << "XMPP stream error: " << m_client->xmppStreamError();
        if (m_client->xmppStreamError() == QXmppStanza::Error::NotAuthorized) { // trying to register
            Registrator* reg = new Registrator(profile(), this);
            QObject::connect(reg, SIGNAL(registered(QString)), this, SLOT(onRegistered(QString)));
            QObject::connect(reg, SIGNAL(error(QString)), this, SLOT(onError(QString)));
            reg->start();
        }
    }
    else emit disconnected();
}

void ExpLeagueConnection::onPresence(const QXmppPresence& presence) {
    QString from = presence.from();

    if (xmpp::user(from) == "global-chat") {
        QString user = xmpp::resource(from);
        emit presenceChanged(user, presence.type() == QXmppPresence::Available);
    }
    else if (!xmpp::user(from).isEmpty() && !xmpp::isRoom(presence.to())){
        QString user;
        if (xmpp::isRoom(from)) {// room
            user = xmpp::resource(from);
//            emit roomPresence(xmpp::user(from), user);
        }
        else {
            user = xmpp::user(from);
            emit presenceChanged(user, presence.type() == QXmppPresence::Available);
        }
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
                    Member* member = find(id, false);
                    if (element.hasAttribute("name")) {
                        member->setName(element.attribute("name"));
                    }
                    QDomElement expert = element.firstChildElement("expert");
                    if (!expert.isNull()) {
                        QDomElement avatar = expert.firstChildElement("avatar");
                        if (!avatar.isNull()) {
                            member->setAvatar(avatar.text());
                        }
                    }
                }
            }
            emit membersChanged();
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
                    QString type = element.attribute("type");
                    if (type == "answer") {
                        emit pattern(new AnswerPattern(element.attribute("name"), icon.count() > 0 ? icon.at(0).toElement().text() : "qrc:/unknown_pattern.png", element.elementsByTagName("body").at(0).toElement().text()));
                    }
                    else {
                        emit chatTemplate(type, element.attribute("name"), element.elementsByTagName("body").at(0).toElement().text());
                    }
                }
            }
        }
        else if (ext.sourceDomElement().namespaceURI() == EXP_LEAGUE_NS + "/history") {
            QDomElement xml = ext.sourceDomElement();
            QDomElement content;
            for(int i = 0; i < xml.childNodes().length(); i++) {
                QDomElement element = xml.childNodes().at(i).toElement();
                if (element.isNull())
                    continue;
                if (element.tagName() == "content") {
                    content = element;
                }
            }
            for(int i = 0; i < content.childNodes().length(); i++) {
                QDomElement element = content.childNodes().at(i).toElement();
                if (element.isNull())
                    continue;
                if (element.tagName() == "message") {
                    QXmppMessage msg;
                    msg.parse(element);
                    onMessage(msg);
                }
            }
        }
    }
}

enum Command {
    ELC_NONE,
    ELC_RESUME,
    ELC_CANCEL,
    ELC_INVITE,
    ELC_CHECK,
};

void ExpLeagueConnection::onMessage(const QXmppMessage& msg, const QString& idOrig) {
    QString id = idOrig.isNull() ? msg.id() : idOrig;

    QString room;
    QString from;
    if (xmpp::isRoom(msg.from()) || msg.from().startsWith("global-chat@")) {
        from = xmpp::resource(msg.from());
        room = xmpp::user(msg.from());
    }
    else if (!from.isEmpty()){
        from = xmpp::user(msg.from());
        room = xmpp::user(msg.to());
    }
    else {
        from = xmpp::user(jid());
        room = xmpp::user(msg.to());
    }

    Command cmd = ELC_NONE;
    std::unique_ptr<Offer> offer = 0;
    QString answer;
    QString sender;
    Progress progress;
    QUrl image;
//    qDebug() << msg;
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
            else if (xml.localName() == "check") {
                cmd = ELC_CHECK;
            }
            else if (xml.localName() == "answer") {
                answer = xml.text();
            }
            else if (xml.localName() == "progress") {
                progress = Progress::fromXml(xml);
            }
            else if (xml.localName() == "image") {
                image = QUrl(xml.text());
            }
        }
    }

    if (!!offer) { // message from system
        if (offer->room() == "")
            offer->m_room = (room != "global-chat" ? room : from) + "@muc." + xmpp::domain(msg.from());
        if (offer->client() == "")
            offer->m_client = from + "@" + xmpp::domain(msg.from());

        switch (cmd) {
        case ELC_CANCEL:
            emit cancel(*offer);
            break;
        case ELC_CHECK:
            emit check(*offer);
            break;
        case ELC_INVITE:
            emit invite(*offer);
            break;
        case ELC_RESUME:
            emit resume(*offer);
            break;
        case ELC_NONE:
            if (room != "global-chat")
                emit this->offer(room, id, *offer);
            else
                emit roomOffer(from, *offer);
            break;
        default:
            qWarning() << "Unhandeled command: " << cmd << " while offer received";
        }
    }
    if (!answer.isEmpty()) {
        emit this->answer(room, id, from, answer);
    }
    if (!progress.empty()) {
        if (room == "global-chat")
            emit this->progress(from, id, from, progress);
        else
            emit this->progress(room, id, from, progress);
    }
    if (image.isValid()) {
        emit this->image(room, id, from, image);
    }
    if (!msg.body().isEmpty()) {
        emit this->message(room, id, from, msg.body());
    }
    if (room == "global-chat") {
        foreach (const QXmppElement& element, msg.extensions()) {
            QDomElement xml = element.sourceDomElement();
            if (xml.namespaceURI() == EXP_LEAGUE_NS) {
                if (xml.localName() == "room-state-changed") {
                    emit roomStatus(from, xml.attribute("state").toInt());
                }
                else if (xml.localName() == "room-message-received") {
                    emit roomMessage(from, xml.attribute("from"), xml.attribute("expert") == "true", xml.hasAttribute("count") ? xml.attribute("count").toInt() : 1);
                }
                else if (xml.localName() == "room-role-update") {
                    emit roomPresence(from, xml.attribute("expert"), xml.attribute("role", "none"), xml.attribute("affiliation", "none"));
                }
                else if (xml.localName() == "feedback") {
                    emit roomFeedback(from, xml.attribute("stars").toInt());
                }
                else if (xml.localName() == "start") {
                    emit roomOrderStart(from, xml.attribute("order"), xml.attribute("expert"));
                }
            }
        }
    }

    if (msg.isReceiptRequested() && msg.id() == id) {
        QXmppMessage receipt;
        receipt.setType(QXmppMessage::Normal);
        receipt.setReceiptId(msg.id());
        m_client->sendPacket(receipt);
    }

    if (xmpp::isRoom(msg.from()) && idOrig.isNull()) {
        m_last_id[xmpp::user(msg.from())] = msg.id();
    }
}

void ExpLeagueConnection::onConnected() {
    QString boundJid = m_client->configuration().jid();
//    qDebug() << "Connected as " << m_client->configuration().jid();

    m_jid = m_client->configuration().jid();
    m_last_id.clear();
    { // restore configuration for reconnect purposes
        m_client->configuration().setJid(profile()->deviceJid());
        m_client->configuration().setResource("doSearchQt-" + QApplication::applicationVersion() + "/expert");
    }
    emit jidChanged(m_jid);
    if (boundJid.endsWith("/admin"))
        emit connected(2);
    else if (boundJid.endsWith("/expert"))
        emit connected(1);
    else
        emit connected(1); // TODO: connect as normal client when there will be no compatibility issues
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
}

void ExpLeagueConnection::requestHistory(const QString& clientId) {
    if (m_history_requested.contains(clientId))
        return;
    QXmppIq iq;
    QDomDocument holder;
    QXmppElementList protocol;
    QDomElement query = holder.createElementNS(EXP_LEAGUE_NS + "/history", "query");
    query.setAttribute("client", clientId);
    protocol.append(QXmppElement(query));
    iq.setExtensions(protocol);
    m_client->sendPacket(iq);
    m_history_requested.insert(clientId);
}

Member* ExpLeagueConnection::find(const QString &id, bool requestProfile) {
    QMap<QString, Member*>::iterator found = m_members_cache.find(id);
    if (found != m_members_cache.end())
        return found.value();
    Member* const member = new Member(id, this);
    if (member->id() == xmpp::user(jid()))
        member->setStatus(Member::ONLINE);
    if (requestProfile)
        sendUserRequest(id);
    m_members_cache.insert(id, member);
    emit membersChanged();
    return member;
}

QList<Member*> ExpLeagueConnection::members() const {
    return m_members_cache.values();
}

void ExpLeagueConnection::listExperts() const {
    QXmppIq iq;
    iq.setId(xmpp::nextId());

    QDomDocument holder;
    QXmppElementList protocol;

    QDomElement query = holder.createElementNS("jabber:iq:roster", "query");
    QDomElement item = holder.createElementNS("jabber:iq:roster", "item");
    item.setAttribute("jid", "experts@" + profile()->domain());
    query.appendChild(item);
    protocol.append(QXmppElement(query));
    iq.setExtensions(protocol);
    m_client->sendPacket(iq);
}

void ExpLeagueConnection::clear() {
    auto members = m_members_cache.values();
    m_members_cache.clear();
    foreach (Member* member, members) {
        delete member;
    }
}

void ExpLeagueConnection::sendCommand(const QString& command, Offer* task, std::function<void (QDomElement* element)> init) {
    QXmppMessage msg("", profile()->domain());
    msg.setId(xmpp::nextId());
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
    msg.setId(xmpp::nextId());
    msg.setType(QXmppMessage::GroupChat);
    msg.setReceiptRequested(true);
    m_client->sendPacket(msg);
    onMessage(msg, msg.id() + "-" + xmpp::user(jid()));
}

void ExpLeagueConnection::sendAnswer(const QString& room, int difficulty, int success, bool extraInfo, const QString& text) {
    QXmppMessage msg("", room);
    msg.setId(xmpp::nextId());
//    msg.setType(QXmppMessage::GroupChat);

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
    msg.setTo(room);
    onMessage(msg, msg.id() +  "-" + xmpp::user(jid()));
}

void ExpLeagueConnection::sendProgress(const QString& to, const Progress& progress) {
    QXmppMessage msg("", profile()->domain());
    msg.setId(xmpp::nextId());

    msg.setType(QXmppMessage::Normal);

    QDomDocument holder;
    QXmppElementList protocol;
    protocol.append(QXmppElement(progress.toXml()));
    msg.setExtensions(protocol);
    m_client->sendPacket(msg);
    msg.setTo(to);
    onMessage(msg, msg.id() +  "-" + xmpp::user(jid()));
}

void ExpLeagueConnection::sendUserRequest(const QString &id) {
    QXmppIq iq;
    iq.setId(xmpp::nextId());

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

void ExpLeagueConnection::sendPresence(const QString& room, bool available) {
    QXmppPresence presence(available ? QXmppPresence::Available : QXmppPresence::Unavailable);
    QString roomId = xmpp::user(room);
    presence.setTo(roomId + "@muc." + profile()->domain());
    if (available) {
        QDomDocument holder;
        QXmppElementList protocol;
        QDomElement x = holder.createElementNS("http://jabber.org/protocol/muc", "x");
        QDomElement history = holder.createElementNS("http://jabber.org/protocol/muc", "history");
        auto pos = m_last_id.find(roomId);
        if (pos != m_last_id.end())
            history.setAttribute("last-id", *pos);
        x.appendChild(history);
        protocol.append(QXmppElement(x));
        presence.setExtensions(protocol);
    }
    m_client->sendPacket(presence);
}

void ExpLeagueConnection::sendOffer(const Offer& offer) {
    QXmppMessage msg("", offer.roomJid());
    msg.setId(xmpp::nextId());
    msg.setType(QXmppMessage::Normal);

    QDomDocument holder;
    QXmppElementList protocol;
    protocol.append(QXmppElement(offer.toXml()));
    protocol.append(holder.createElementNS(EXP_LEAGUE_NS, "offer-change"));
    msg.setExtensions(protocol);
    m_client->sendPacket(msg);
}

void ExpLeagueConnection::sendVerify(const QString& room) {
    QXmppMessage msg("", room);
    msg.setId(xmpp::nextId());
    msg.setType(QXmppMessage::Normal);

    QDomDocument holder;
    QXmppElementList protocol;
    QDomElement verify = holder.createElementNS(EXP_LEAGUE_NS, "verified");
    verify.setAttribute("authority", jid());
    protocol.append(verify);
    msg.setExtensions(protocol);
    m_client->sendPacket(msg);
}

void ExpLeagueConnection::clearHistory() {
    foreach(Member* member, m_members_cache.values()) {
        member->clear();
    }
}

ExpLeagueConnection::~ExpLeagueConnection() {
    clear();
}

Progress Progress::fromXml(const QDomElement& xml) {
    QString order = xml.attribute("order");
    QString name;
    Operation operation = PO_VISIT;
    Target target = PO_URL;
    OrderState state = OS_NONE;

    QDomElement change = xml.elementsByTagName("change").at(0).toElement();
    QDomElement stateXml = xml.elementsByTagName("state").at(0).toElement();
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
    if (!stateXml.isNull()) {
        QString stateStr = stateXml.text();
        if (stateStr == "none")
            state = OS_NONE;
        else if (stateStr == "open")
            state = OS_OPEN;
        else if (stateStr == "in-progress")
            state = OS_IN_PROGRESS;
        else if (stateStr == "suspended")
            state = OS_SUSPENDED;
        else if (stateStr == "done")
            state = OS_DONE;
    }
    return Progress(order, operation, target, name, state);
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
    {
        QDomElement topic = holder.createElementNS(xmpp::EXP_LEAGUE_NS, "topic");
        topic.appendChild(holder.createTextNode(m_topic));
        result.appendChild(topic);
    }

    if (!m_comment.isEmpty()) {
        QDomElement comment = holder.createElementNS(xmpp::EXP_LEAGUE_NS, "comment");
        comment.appendChild(holder.createTextNode(m_comment));
        result.appendChild(comment);
    }

    if (!m_draft.isEmpty()) {
        QDomElement draft = holder.createElementNS(xmpp::EXP_LEAGUE_NS, "draft");
        draft.appendChild(holder.createTextNode(m_draft));
        result.appendChild(draft);
    }

    if (m_location.isValid()) {
        QDomElement location = holder.createElementNS(xmpp::EXP_LEAGUE_NS, "location");
        location.setAttribute("latitude", QString::number(m_location.latitude()));
        location.setAttribute("longitude", QString::number(m_location.longitude()));
        result.appendChild(location);
    }

    foreach(const QUrl& url, m_images) {
        QDomElement attachment = holder.createElementNS(xmpp::EXP_LEAGUE_NS, "image");
        attachment.appendChild(holder.createTextNode(url.toString()));
        result.appendChild(attachment);
    }

    foreach(AnswerPattern* pattern, m_patterns) {
        if (!pattern)
            continue;
        QDomElement patternDom = holder.createElementNS(xmpp::EXP_LEAGUE_NS, "pattern");
        patternDom.setAttribute("name", pattern->name());
        result.appendChild(patternDom);
    }

    foreach(TaskTag* tag, m_tags) {
        if (!tag)
            continue;
        QDomElement tagDom = holder.createElementNS(xmpp::EXP_LEAGUE_NS, "tag");
        tagDom.appendChild(holder.createTextNode(tag->name()));
        result.appendChild(tagDom);
    }

    if (!m_filter.isEmpty()) {
        QDomElement filter = holder.createElementNS(xmpp::EXP_LEAGUE_NS, "experts-filter");
        for (auto expert = m_filter.begin(); expert != m_filter.end(); ++expert) {
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
            default:
                break;
            }
            item.appendChild(holder.createTextNode(expert.key() + "@" + xmpp::domain(League::instance()->connection()->jid())));
            filter.appendChild(item);
        }
        result.appendChild(filter);
    }

    return QDomElement(result);
}

void Offer::start() {
    m_timer->start();
    RegionResolver::resolve(m_location, [this](const QString& region) {
        setRegion(region);
    });
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
             const QString& comment,
             const QString& draft)
: m_client(client), m_room(room), m_topic(topic), m_urgency(urgency), m_local(local),
  m_images(images), m_filter(filter), m_location(location), m_started(started),
  m_tags(tags), m_patterns(patterns), m_comment(comment), m_draft(draft)
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
        else if (element.tagName() == "draft") {
            m_draft = element.text();
        }
        else if (element.tagName() == "image") {
            m_images.append(QUrl(element.text()).path().section('/', -1, -1));
        }
        else if (element.tagName() == "experts-filter") {
            for(int i = 0; i < element.childNodes().length(); i++) {
                QDomElement filter = element.childNodes().at(i).toElement();
                if (filter.tagName() == "reject")
                    m_filter[xmpp::user(filter.text())] = Offer::TFT_REJECT;
                if (filter.tagName() == "accept")
                    m_filter[xmpp::user(filter.text())] = Offer::TFT_ACCEPT;
                if (filter.tagName() == "prefer")
                    m_filter[xmpp::user(filter.text())] = Offer::TFT_PREFER;
            }
        }
        else if (element.tagName() == "pattern") {
            AnswerPattern* const pattern = League::instance()->findPattern(element.attribute("name"));
            if (pattern)
                m_patterns.append(pattern);
        }
        else if (element.tagName() == "tag") {
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

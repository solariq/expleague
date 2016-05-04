#include <atomic>

#include <QMutex>
#include <QList>

#include <QtNetwork>
#include <QHostInfo>
#include <QJsonDocument>

#include "profile.h"
#include "protocol.h"

const QString VK_API = QStringLiteral("https://api.vk.com/method/");

namespace expleague {

QList<Profile*>& Profile::list() {
    static QList<Profile*> profiles;
    return profiles;
}

namespace xmpp {
class Registrator: public QXmppClientExtension {
public:

    explicit Registrator(ProfileBuilder* builder): builder(builder) {
        setParent(builder);
        QObject::connect(builder, SIGNAL(destroyed(QObject*)), SLOT(builderDestroyed()));
    }

    virtual ~Registrator() {
        qDebug() << "Registrator stopped";
    }

    void start() {
        qDebug() << "Starting registration of " << builder->login() + "@" + builder->domain();
        config.setJid(builder->login() + "@" + builder->domain());
        config.setPassword(builder->password());
        config.setHost(builder->domain());
        config.setPort(5222);
        config.setResource("expert");
        config.setAutoReconnectionEnabled(false);
        config.setKeepAliveInterval(55);
        connection.addExtension(this);
        connection.connectToServer(config);
        qDebug() << "Connection started";
    }

protected:
    bool handleStanza(const QDomElement &stanza);

private slots:
    void builderDestroyed() {
        connection.disconnect();
        delete this;
    }

private:
    ProfileBuilder* builder;
    QXmppConfiguration config;
    QXmppClient connection;
    QString m_registrationId;
};
}

void ProfileBuilder::setVKUser(const QString &vkName) {
    this->m_vkUser = vkName;
    QUrl vkapiGet(VK_API + "users.get?user_ids=" + vkName + "&fields=photo_max,city,country,sex&v=5.45&lang=ru");
    setLogin("vk-" + vkName + "-" + QHostInfo::localHostName());
    m_nam.get(QNetworkRequest(vkapiGet));
}

void ProfileBuilder::finished(QNetworkReply *reply) {
    QJsonParseError error;
    QByteArray data = reply->readAll();
    QJsonDocument json(QJsonDocument::fromJson(data, &error));
    if (error.error != QJsonParseError::NoError) {
        qWarning() << "Unable to parse answer from social net: " << QString::fromUtf8(data);
        return;
    }
    foreach (QJsonValue userV, json.object()["response"].toArray()) {
        QJsonObject user = userV.toObject();
        if (QString::number(user["id"].toInt()) == m_vkUser) {
            QString name = user["first_name"].toString() + " " + user["last_name"].toString();
            QUrl avatar(user["photo_max"].toString());
            Profile::Sex sex = (Profile::Sex)user["sex"].toInt();
            qDebug() << "name: " << name << " ava: " << avatar;
            m_name = name; nameChanged(name);
            m_avatar = avatar; avatarChanged(avatar);
            m_sex = sex; sexChanged(sex);
        }
    }
}

void ProfileBuilder::setJid(const QString& jid) { // registration complete, registration jid received
    m_jid = jid;
    Profile::list().append(m_result = new Profile(m_domain, m_login, m_password, m_name, m_avatar, m_sex));
    resultChanged(m_result);
}

void ProfileBuilder::build() {
    xmpp::Registrator* reg = new xmpp::Registrator(this);
    reg->start();
}

namespace xmpp {
bool Registrator::handleStanza(const QDomElement &stanza) {
    client()->configuration().setAutoReconnectionEnabled(false);

    if (stanza.tagName() == "failure") {
        if (!stanza.firstChildElement("not-authorized").isNull()) {
            QDomElement text = stanza.firstChildElement("text");
            if (!text.isNull() && text.text() == "No such user") {
                QXmppIq reg(QXmppIq::Type::Set);
                qDebug() << "No such user found, registering one";
                QXmppElement query = parse("<query xmlns=\"jabber:iq:register\">"
                                           "  <username>" + builder->login() + "</username>"
                                           "  <password>" + builder->password() + "</password>"
                                           "  <misc>" + builder->avatar().toString() + "</misc>"
                                           "  <name>" + builder->name() + "</name>"
                                           "  <email>" + "doSearchQt/" + QApplication::applicationVersion() + "/expert</email>"
                                           "  <nick>" + QString::number(builder->sex()) + "/expert</nick>"
                                           "</query>");
                reg.setExtensions(QXmppElementList() += query);
                m_registrationId = reg.id();
                client()->sendPacket(reg);
                return true;
            }
            else if (!text.isNull() && text.text().contains("Mismatched response")) {
                qDebug() << "Incorrect password";
                builder->setError(tr("Неверный пароль:\n ") + stanza.text());
                client()->disconnectFromServer();
                return true;
            }
        }
        else {
            builder->setError(tr("Не удалось зарегистрировать пользователя:\n ") + stanza.text());
            client()->disconnectFromServer();
            return true;
        }
    }
    else if (stanza.tagName() == "iq" && !stanza.firstChildElement("bind").isNull()) {
        QDomElement bind = stanza.firstChildElement("bind");
        QString jid = bind.firstChildElement("jid").text();
        builder->setJid(jid);
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
            builder->setError(tr("Не удалось зарегистрировать пользователя:\n ") + stanza.text());
            client()->disconnectFromServer();
            qDebug() << "Unable to register profile" << stanza;
        }
        return true;
    }
    qDebug() << stanza;
    return false;
}
}
}

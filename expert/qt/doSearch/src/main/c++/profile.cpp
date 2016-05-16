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

void ProfileBuilder::registered(const QString& jid) { // registration complete, registration jid received
    m_jid = jid;
    Profile::list().append(m_result = new Profile(m_domain, m_login, m_password, m_name, m_avatar, m_sex));
    resultChanged(m_result);
}

void ProfileBuilder::build() {
    xmpp::Registrator* reg = new xmpp::Registrator(new Profile(m_domain, m_login, m_password, m_name, m_avatar, m_sex, this), this);
    QObject::connect(reg, SIGNAL(registered(QString)), this, SLOT(registered(QString)));
    QObject::connect(reg, SIGNAL(error(QString)), this, SLOT(error(QString)));
    reg->start();
}
}

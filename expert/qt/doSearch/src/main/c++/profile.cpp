#include <QtNetwork>
#include <QHostInfo>
#include <QJsonDocument>

#include "profile.h"
#include "protocol.h"

const QString VK_API = QStringLiteral("https://api.vk.com/method/");

namespace expleague {

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

Profile* ProfileBuilder::build() {
    QString profileName = m_login + "@" + m_domain;
    qDebug() << "Building " << profileName;
    QSettings* settings = new QSettings();
    settings->beginGroup("profiles");
    settings->beginGroup(profileName);
    settings->remove("");
    settings->setValue("domain", m_domain);
    settings->setValue("login", m_login);
    settings->setValue("password", m_password);

    settings->setValue("name", m_name);
    settings->setValue("avatar", m_avatar.toString());
    settings->setValue("sex", QString::number(m_sex));
    result.reset(new Profile(settings));
    expleague::xmpp::registerExpert(result);
    return result.data();
}

}

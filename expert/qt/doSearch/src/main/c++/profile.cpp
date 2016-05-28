#include <atomic>

#include <QMutex>
#include <QList>

#include <QtNetwork>
#include <QHostInfo>
#include <QDomDocument>

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
    QUrl vkapiGet(VK_API + "users.get.xml?user_ids=" + vkName + "&fields=photo_max,city,country,sex&v=5.45&lang=ru");
    setLogin("vk-" + vkName + "-" + QHostInfo::localHostName());
    m_nam.get(QNetworkRequest(vkapiGet));
}

void ProfileBuilder::finished(QNetworkReply *reply) {
    QByteArray data = reply->readAll();
    QDomDocument doc;
    QString error;
    doc.setContent(data, &error);
//    qDebug() << "Parsing vk answer: " << QString::fromUtf8(data);
    if (!error.isEmpty()) {
        qWarning() << "Unable to parse answer from social net: " << error << " data:\n" << QString::fromUtf8(data);
        return;
    }
    for (int i = 0; i < doc.documentElement().childNodes().length(); i++) {
        QDomElement user = doc.documentElement().childNodes().at(i).toElement();
        if (user.firstChildElement("id").text() == m_vkUser) {
            QString name = user.firstChildElement("first_name").text() + " " + user.firstChildElement("last_name").text();
            QUrl avatar(user.firstChildElement("photo_max").text());
            Profile::Sex sex = (Profile::Sex)user.firstChildElement("sex").text().toInt();
//            qDebug() << "name: " << name << " ava: " << avatar;
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

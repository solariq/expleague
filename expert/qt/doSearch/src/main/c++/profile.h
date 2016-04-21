#ifndef PROFILE_H
#define PROFILE_H

#include <memory>

#include <QSharedDataPointer>
#include <QStandardPaths>
#include <QSettings>
#include <QDir>
#include <QUrl>

#include <QNetworkAccessManager>

#include "task.h"

namespace expleague {

//extern QSettings settings;

class Profile: public QObject {
    Q_OBJECT

    Q_PROPERTY(QString name READ name)
    Q_PROPERTY(QUrl avatar READ avatar)
    Q_PROPERTY(expleague::Profile::Sex sex READ sex)

    Q_PROPERTY(QString domain READ domain)
    Q_PROPERTY(QString login READ login)
    Q_PROPERTY(QString passwd READ passwd)

    Q_PROPERTY(QString jid READ jid NOTIFY jidChanged)

    Q_PROPERTY(QString error READ error NOTIFY errorChanged)

    Q_ENUMS(Sex)
public:
    enum Sex: int {
        UNKNOWN = 0,
        FEMALE = 1,
        MALE = 2,
    };

    QString jid() {
        return settings->value("jid", "").toString();
    }

    QString deviceJid() {
        return login() + "@" + domain();
    }

    QString domain() {
        return settings->value("domain", "expleague.com").toString();
    }

    QString login() {
        return settings->value("login", "expert").toString();
    }

    QString passwd() {
        return settings->value("password", "").toString();
    }

    QString name() {
        return settings->value("name", tr("Неизвестный Эксперт")).toString();
    }

    QUrl avatar() {
        return QUrl(settings->value("avatar", "qrc:/avatar.png").toString());
    }

    Sex sex() {
        return (Sex)settings->value("sex", "0").toInt();
    }

    QString error() {
        return m_error;
    }

signals:
    void jidChanged(const QString&);
    void errorChanged(const QString&);

public:
    void jid(const QString& jid) {
        settings->setValue("jid", jid);
        jidChanged(jid);
    }

    void error(const QString& error) {
        m_error = error;
        errorChanged(error);
    }

public:
    explicit Profile(QSettings* settings = 0): settings(settings ? settings : new QSettings()) {
    }

    const Profile& operator =(const Profile& other) {
        settings = other.settings;
        return *this;
    }

    void remove() {
        settings->remove("");
    }

private:    
    QSharedPointer<QSettings> settings;
    QString m_error;
};

class Expert: public Profile {
    Q_OBJECT

public:
    static Profile* active();

    enum State {
        OFFLINE, ONLINE, CHECK, INVITE, IN_FLIGHT
    };

    State state();

    Task* task();

signals:
    void stateChanged(State newState);

public:
    explicit Expert(QSettings* settings);

private:
    State e_state = State::OFFLINE;
};


class ProfileBuilder: public QObject {
    Q_OBJECT

    Q_PROPERTY(QString vkToken READ vkToken WRITE setVKToken)
    Q_PROPERTY(QString vkUser READ vkUser WRITE setVKUser)

    Q_PROPERTY(QString name READ name NOTIFY nameChanged)
    Q_PROPERTY(QUrl avatar READ avatar NOTIFY avatarChanged)
    Q_PROPERTY(expleague::Profile::Sex sex READ sex WRITE setSex NOTIFY sexChanged)

    Q_PROPERTY(QString domain READ domain WRITE setDomain NOTIFY domainChanged)
    Q_PROPERTY(QString login READ login WRITE setLogin NOTIFY loginChanged)
    Q_PROPERTY(QString password READ password WRITE setPassword NOTIFY passwordChanged)

public:
    QString vkUser() {
        return m_vkToken;
    }

    QString vkToken() {
        return m_vkToken;
    }

    QString name() {
        return m_name;
    }

    QUrl avatar() {
        return m_avatar;
    }

    Profile::Sex sex() {
        return m_sex;
    }

    QString login() {
        return m_login;
    }

    QString password() {
        return m_password;
    }

    QString domain() {
        return m_domain;
    }

    Q_INVOKABLE Profile* build();
public: // setters

    void setVKUser(const QString&);

    void setVKToken(const QString& token) {
        m_vkToken = token;
        setPassword(token.length() > 10 ? token.mid(0, 10) : token);
    }

    void setDomain(const QString& domain) {
        m_domain = domain;
        domainChanged(domain);
    }

    void setLogin(const QString& login) {
        m_login = login;
        loginChanged(login);
    }

    void setPassword(const QString& password) {
        m_password = password;
        passwordChanged(password);
    }

    void setSex(const Profile::Sex& sex) {
        m_sex = sex;
        sexChanged(sex);
    }

public: // constructors
    explicit ProfileBuilder(QObject* parent = 0): QObject(parent), m_nam(this) {
        connect(&m_nam, SIGNAL(finished(QNetworkReply*)), this, SLOT(finished(QNetworkReply*)));
    }

signals:
    void nameChanged(const QString&);
    void avatarChanged(const QUrl&);
    void sexChanged(Profile::Sex);
    void domainChanged(const QString&);
    void loginChanged(const QString&);
    void passwordChanged(const QString&);

public slots:
    void finished(QNetworkReply*);

private:
    QNetworkAccessManager m_nam;

    QString m_vkUser;
    QString m_vkToken;

    QString m_name = QStringLiteral("Неизвестный эксперт");
    QUrl m_avatar = QStringLiteral("qrc:/avatar.png");
    Profile::Sex m_sex = Profile::Sex::UNKNOWN;

    QString m_domain;
    QString m_login;
    QString m_password;

    QSharedPointer<Profile> result;
};

}

Q_DECLARE_METATYPE(expleague::Profile::Sex)
Q_DECLARE_METATYPE(expleague::Profile*)

#endif // PROFILE_H

#ifndef PROFILE_H
#define PROFILE_H

#include <memory>

#include <QSharedPointer>
#include <QStandardPaths>
#include <QSettings>
#include <QDir>
#include <QUrl>

#include <QNetworkAccessManager>

#include <QQuickItem>

#include "task.h"

namespace expleague {

class ProfileBuilder;
class StateSaver;
class Profile: public QObject {
    Q_OBJECT

    Q_PROPERTY(QString name READ name CONSTANT)
    Q_PROPERTY(QUrl avatar READ avatar CONSTANT)
    Q_PROPERTY(expleague::Profile::Sex sex READ sex CONSTANT)

    Q_PROPERTY(QString domain READ domain CONSTANT)
    Q_PROPERTY(QString login READ login CONSTANT)
    Q_PROPERTY(QString passwd READ passwd CONSTANT)

    Q_PROPERTY(QString deviceJid READ deviceJid CONSTANT)

    Q_ENUMS(Sex)

public:

    static QList<Profile*>& list();
    static void remove(Profile*);

    enum Sex: int {
        UNKNOWN = 0,
        FEMALE = 1,
        MALE = 2,
    };

    QString deviceJid() const {
        return login() + "@" + domain();
    }

    QString domain() const {
        return m_domain;
    }

    QString login() const {
        return m_login;
    }

    QString passwd() const {
        return m_passwd;
    }

    QString name() const {
        return m_name;
    }

    QUrl avatar() const {
        return m_avatar;
    }

    Sex sex() const {
        return m_sex;
    }

public:
    Profile(const QString& domain, const QString& login, const QString& passwd, const QString& name, const QUrl& avatar, Sex sex, QObject* parent = 0): QObject(parent),
        m_domain(domain), m_login(login), m_passwd(passwd), m_name(name), m_avatar(avatar), m_sex(sex) {}

private:
    QString m_domain;
    QString m_login;
    QString m_passwd;
    QString m_name;
    QUrl m_avatar;
    Sex m_sex;
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

    Q_PROPERTY(QString error READ error NOTIFY errorChanged)
    Q_PROPERTY(QString jid READ jid NOTIFY jidChanged)

    Q_PROPERTY(expleague::Profile* result READ result NOTIFY resultChanged)

public:

    QString jid() {
        return m_jid;
    }

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

    QString error() {
        return m_error;
    }

    Profile* result() {
        return m_result;
    }

    Q_INVOKABLE void build();

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
    explicit ProfileBuilder(QObject* parent = 0);

signals:
    void nameChanged(const QString&);
    void avatarChanged(const QUrl&);
    void sexChanged(Profile::Sex);
    void domainChanged(const QString&);
    void loginChanged(const QString&);
    void passwordChanged(const QString&);

    void errorChanged(const QString&);
    void jidChanged(const QString&);
    void resultChanged(Profile*);

private slots:
    void registered(const QString& jid);

    void error(const QString& error) {
        m_error = error;
        errorChanged(error);
    }

public slots:
    void finished(QNetworkReply*);

private:
    QNetworkAccessManager* m_nam;

    QString m_vkUser;
    QString m_vkToken;

    QString m_name = QStringLiteral("Неизвестный эксперт");
    QUrl m_avatar = QStringLiteral("qrc:/avatar.png");
    Profile::Sex m_sex = Profile::Sex::UNKNOWN;

    QString m_domain;
    QString m_login;
    QString m_password;

    QString m_error;
    QString m_jid;

    Profile* m_result = 0;
};

}

Q_DECLARE_METATYPE(expleague::Profile::Sex)
QML_DECLARE_TYPE(expleague::Profile)

#endif // PROFILE_H

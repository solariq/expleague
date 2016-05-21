#ifndef LEAGUE_H
#define LEAGUE_H

#include <QObject>

#include <QQmlListProperty>
#include <QQuickImageProvider>

#include "task.h"
#include "profile.h"
#include "protocol.h"

namespace expleague {

class doSearch;
class Member: public QObject {
    Q_OBJECT

    Q_PROPERTY(QString id READ id CONSTANT)
    Q_PROPERTY(QString name READ name NOTIFY nameChanged)
    Q_PROPERTY(QUrl avatar READ avatar NOTIFY avatarChanged)
    Q_PROPERTY(expleague::Member::Status status READ status NOTIFY statusChanged)

    Q_ENUMS(Status)
public:
    enum Status {
        ONLINE, OFFLINE
    };

    QString id() const {
        return m_id;
    }

    QString name() const {
        return m_name.isEmpty() ? m_id : m_name;
    }

    QUrl avatar() const {
        return m_avatar;
    }

    Status status() const {
        return m_status;
    }

signals:
    void nameChanged(const QString& name);
    void avatarChanged(const QUrl& avatar);
    void statusChanged(bool status);

public:
    void setStatus(Status status) {
        m_status = status;
        emit statusChanged(status);
    }

    void setName(const QString& name) {
        m_name = name;
        emit nameChanged(name);
    }

    void setAvatar(const QUrl& avatar) {
        m_avatar = avatar;
        emit avatarChanged(avatar);
    }

public:
    Member(const QString& id = "", QObject* parent = 0): QObject(parent), m_id(id) {}

private:
    QString m_id;
    QString m_name;
    QUrl m_avatar = QUrl("qrc:/avatar.png");
    Status m_status;
};

class TaskTag: public QObject {
    Q_OBJECT

    Q_PROPERTY(QString name READ name CONSTANT)
    Q_PROPERTY(QUrl icon READ icon CONSTANT)
public:

    QString name() { return m_name; }
    QUrl icon() { return m_icon; }

public:
    TaskTag(const QString& name = "", const QUrl& icon = QUrl("qrc:/avatar.png"), QObject* parent = 0): QObject(parent), m_name(name), m_icon(icon){}

private:
    QString m_name;
    QUrl m_icon;
};

class AnswerPattern: public QObject {
    Q_OBJECT

    Q_PROPERTY(QString name READ name CONSTANT)
    Q_PROPERTY(QUrl icon READ icon CONSTANT)
    Q_PROPERTY(QString text READ text CONSTANT)
public:

    QString name() { return m_name; }
    QUrl icon() { return m_icon; }
    QString text() { return m_text; }

public:
    AnswerPattern(const QString& name = "", const QUrl& icon = QUrl("qrc:/avatar.png"), const QString& text = "", QObject* parent = 0): QObject(parent), m_name(name), m_icon(icon), m_text(text) {}

private:
    QString m_name;
    QUrl m_icon;
    QString m_text;
};

using xmpp::Progress;
class ImagesStore;
class League: public QObject {
    Q_OBJECT

    Q_PROPERTY(expleague::League::Status status READ status NOTIFY statusChanged)
    Q_PROPERTY(expleague::Profile* profile READ active WRITE setActive NOTIFY profileChanged)
    Q_PROPERTY(QQmlListProperty<expleague::Profile> profiles READ profiles NOTIFY profilesChanged)
    Q_PROPERTY(QQmlListProperty<expleague::TaskTag> tags READ tags NOTIFY tagsChanged)
    Q_PROPERTY(QQmlListProperty<expleague::AnswerPattern> patterns READ patterns NOTIFY patternsChanged)

    Q_ENUMS(Status)

public:
    enum Status {
        LS_ONLINE,
        LS_OFFLINE,
        LS_CHECK,
        LS_INVITE,
        LS_ON_TASK
    };

    Status status() const {
        return m_status;
    }

    Profile* active() const {
        return m_connection ? m_connection->profile() : 0;
    }

    QQmlListProperty<Profile> profiles() {
        return QQmlListProperty<Profile>(this, Profile::list());
    }

    QQmlListProperty<TaskTag> tags() {
        return QQmlListProperty<TaskTag>(this, m_tags);
    }

    QQmlListProperty<AnswerPattern> patterns() {
        return QQmlListProperty<AnswerPattern>(this, m_patterns);
    }

    Q_INVOKABLE void connect() {
        if (m_connection)
            m_connection->connect();
    }

    Q_INVOKABLE void disconnect() {
        if (m_connection)
            m_connection->disconnect();
    }

    Q_INVOKABLE QString id() {
        return m_connection->jid().section('@', 0, 0);
    }

    Q_INVOKABLE QUrl imageUrl(QString imageId) const;

    Q_INVOKABLE Member* findMember(const QString& id) const;
    Q_INVOKABLE TaskTag* findTag(const QString& id) const;
    Q_INVOKABLE AnswerPattern* findPattern(const QString& id) const;

    QUrl uploadImage(const QImage& img) const;

public:
    void setActive(Profile* profile);
    xmpp::ExpLeagueConnection* connection() { return m_connection; }
    ImagesStore* store() { return m_store; }

    static League* instance();

signals:
    void statusChanged(Status status);
    void profileChanged(Profile* profile);
    void receivedInvite(Offer* offer);

    Q_INVOKABLE void profilesChanged();
    void patternsChanged();
    void tagsChanged();

private slots:
    void connected() {
        m_status = LS_ONLINE;
        statusChanged(m_status);
    }

    void disconnected();

    void checkReceived(Offer* offer) {
        offer = normalizeOffer(offer);
        m_status = LS_CHECK;
        statusChanged(m_status);
    }

    void inviteReceived(Offer* offer);

    void resumeReceived(Offer* offer) {
        offer = normalizeOffer(offer);
        startTask(offer);
        m_connection->sendResume(offer);
    }

    void cancelReceived(Offer* offer) {
        offer = normalizeOffer(offer);
        offer->cancelled();
        m_status = LS_ONLINE;
        statusChanged(m_status);
    }

    void acceptInvitation(Offer* offer) {
        offer = normalizeOffer(offer);
        startTask(offer);
        m_connection->sendAccept(offer);
    }

    void rejectInvitation(Offer* offer) {
        offer = normalizeOffer(offer);
        m_connection->sendCancel(offer);
        m_status = LS_ONLINE;
        statusChanged(m_status);
    }

    void taskFinished() {
        Task* task = qobject_cast<Task*>(sender());
        m_tasks.removeOne(task);
        if (m_tasks.empty()) {
            m_status = LS_ONLINE;
            statusChanged(m_status);
        }
    }

    void tagReceived(TaskTag* tag) {
        tag->setParent(this);
        foreach(TaskTag* current, m_tags) {
            if (current->name() == tag->name()) {
                m_tags.removeOne(current);
//                delete current;
            }
        }

        m_tags.append(tag);
        tagsChanged();
    }

    void patternReceived(AnswerPattern* pattern) {
        pattern->setParent(this);
        foreach(AnswerPattern* current, m_patterns) {
            if (current->name() == pattern->name()) {
                m_patterns.removeOne(current);
//                current->deleteLater();
            }
        }

        m_patterns.append(pattern);
        patternsChanged();
    }

    void messageReceived(const QString& room, const QString& from, const QString& text);
    void imageReceived(const QString& room, const QString& from, const QUrl&);
    void answerReceived(const QString& room, const QString& from, const QString&);
    void progressReceived(const QString& room, const QString& from, const Progress&);

public:
    explicit League(QObject* parent = 0);

protected:
    doSearch* parent() const;

private:
    Offer* normalizeOffer(Offer*);
    void startTask(Offer* offer);

private:
    QMap<QString, Offer*> m_offers;
    QList<Task*> m_tasks;
    QList<TaskTag*> m_tags;
    QList<AnswerPattern*> m_patterns;
    Status m_status = LS_OFFLINE;
    xmpp::ExpLeagueConnection* m_connection = 0;
    ImagesStore* m_store;
};

class ImagesStoreResponse: public QQuickImageResponse {
    Q_OBJECT

public:
    QQuickTextureFactory* textureFactory() const {
        qDebug() << "Result image: " << m_result;
        return m_result;
    }

public:
    QString id() {
        return m_id;
    }

public:
    void setResult(const QImage& image) {
        m_result = QQuickTextureFactory::textureFactoryForImage(image);
        emit QQuickImageResponse::finished();
        qDebug() << "Image acquired " << image;
    }

public:
    explicit ImagesStoreResponse(const QString& id): m_id(id) {}

private:
    QString m_id;
    QQuickTextureFactory* m_result;
};

class ImagesStorePrivate;
class Profile;
class ImagesStore: public QObject, public QQuickAsyncImageProvider {
    Q_OBJECT

public:
    QQuickImageResponse* requestImageResponse(const QString &id, const QSize &requestedSize);
    QUrl upload(const QImage& image) const;
    QUrl url(const QString& id) const;

private slots:
    void requestFinished(QNetworkReply* reply);
    void profileChanged(Profile* profile);
    void imageRequested(const QString& id);

signals:
    void requestImageById(const QString& id);

public:
    ImagesStore(League* parent);
    virtual ~ImagesStore();

private:
    friend class ImagesStorePrivate;
    ImagesStorePrivate* m_instance;
};

}

Q_DECLARE_METATYPE(expleague::Member*)
Q_DECLARE_METATYPE(expleague::TaskTag*)
Q_DECLARE_METATYPE(expleague::AnswerPattern*)
Q_DECLARE_METATYPE(expleague::League*)
Q_DECLARE_METATYPE(expleague::League::Status)

#endif // LEAGUE_H

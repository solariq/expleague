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
class GlobalChat;
class RoomStatus;
class Member: public QObject {
    Q_OBJECT

    Q_PROPERTY(QString id READ id CONSTANT)
    Q_PROPERTY(QString name READ name NOTIFY nameChanged)
    Q_PROPERTY(QUrl avatar READ avatar NOTIFY avatarChanged)
    Q_PROPERTY(QQmlListProperty<expleague::RoomStatus> history READ historyQml NOTIFY historyChanged)
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

    QQmlListProperty<RoomStatus> historyQml() const { return QQmlListProperty<RoomStatus>(const_cast<Member*>(this), const_cast<QList<RoomStatus*>&>(m_history)); }

signals:
    void nameChanged(const QString& name);
    void avatarChanged(const QUrl& avatar);
    void statusChanged(bool status);
    void historyChanged();

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

    void clear() {
        m_history.clear();
        emit historyChanged();
    }

    void append(RoomStatus* room);
    void remove(RoomStatus* room);

    Q_INVOKABLE void requestHistory() const;

public:
    explicit Member(const QString& id = "", QObject* parent = 0): QObject(parent), m_id(id) {}

private:
    QString m_id;
    QString m_name;
    QUrl m_avatar = QUrl("qrc:/avatar.png");
    Status m_status = OFFLINE;
    QList<RoomStatus*> m_history;
};

class TaskTag: public QObject {
    Q_OBJECT

    Q_PROPERTY(QString name READ name CONSTANT)
    Q_PROPERTY(QUrl icon READ icon CONSTANT)
public:

    QString name() const { return m_name; }
    QUrl icon() const { return m_icon; }

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

    QString name() const { return m_name; }
    QUrl icon() const { return m_icon; }
    QString text() const { return m_text; }

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
    Q_PROPERTY(expleague::League::Role role READ role NOTIFY roleChanged)

    Q_PROPERTY(expleague::Profile* profile READ active WRITE setActive NOTIFY profileChanged)
    Q_PROPERTY(QQmlListProperty<expleague::Profile> profiles READ profiles NOTIFY profilesChanged)
    Q_PROPERTY(QQmlListProperty<expleague::TaskTag> tags READ tags NOTIFY tagsChanged)
    Q_PROPERTY(QQmlListProperty<expleague::AnswerPattern> patterns READ patterns NOTIFY patternsChanged)
    Q_PROPERTY(QStringList helloPatterns READ helloPatterns NOTIFY chatPatternsChanged)
    Q_PROPERTY(QStringList chatPatterns READ chatPatterns NOTIFY chatPatternsChanged)
    Q_PROPERTY(QStringList experts READ experts NOTIFY membersChanged)
    Q_PROPERTY(expleague::GlobalChat* chat READ chat CONSTANT)

    Q_ENUMS(Status)
    Q_ENUMS(Role)

public:
    enum Status {
        LS_ONLINE,
        LS_OFFLINE,
        LS_CHECK,
        LS_INVITE,
        LS_ON_TASK
    };

    enum Role {
        NONE,
        EXPERT,
        ADMIN
    };

    Status status() const {
        return m_status;
    }

    Role role() const { return m_role; }

    Profile* active() const {
        return m_profile;
    }

    Member* self() const { return findMember(id()); }


    QQmlListProperty<Profile> profiles() {
        return QQmlListProperty<Profile>(this, Profile::list());
    }

    QQmlListProperty<TaskTag> tags() {
        return QQmlListProperty<TaskTag>(this, m_tags);
    }

    QQmlListProperty<AnswerPattern> patterns() {
        return QQmlListProperty<AnswerPattern>(this, m_patterns);
    }

    QStringList chatPatterns() const { return m_chat_templates["chat"]; }
    QStringList helloPatterns() const { return m_chat_templates["hello"]; }

    QStringList experts() const;

    Q_INVOKABLE void connect();
    Q_INVOKABLE void disconnect();

    Q_INVOKABLE QString id() const {
        return m_connection ? m_connection->id() : "local";
    }

    Q_INVOKABLE QUrl imageUrl(const QString& normalizeImageUrlForUI) const;
    Q_INVOKABLE QUrl normalizeImageUrlForUI(const QUrl& imageUrl) const;

    Q_INVOKABLE QString templateContent(const QString& templateName) {
        auto ptr = m_chat_template_contents.find(templateName);
        if (ptr != m_chat_template_contents.end())
            return *ptr;
        return QString();
    }

    Q_INVOKABLE Member* findMember(const QString& id) const;
    Q_INVOKABLE Member* findMemberByName(const QString& name) const;
    Q_INVOKABLE TaskTag* findTag(const QString& id) const;
    Q_INVOKABLE AnswerPattern* findPattern(const QString& id) const;

    QUrl uploadImage(const QImage& img) const;

public:
    void setActive(Profile* profile);
    xmpp::ExpLeagueConnection* connection() { return m_connection; }
    ImagesStore* store() { return m_store; }

    static League* instance();

    Q_INVOKABLE void acceptInvitation(Offer* offer) {
        startTask(offer);
        m_connection->sendAccept(offer);
    }

    Q_INVOKABLE void rejectInvitation(Offer* offer) {
        m_connection->sendCancel(offer);
        m_status = LS_ONLINE;
        emit statusChanged(m_status);
    }

    GlobalChat* chat() const;
    Task* task(const QString& roomId);

    void notifyIfNeeded(const QString& from, const QString& message, bool broadcast = false);

signals:
    void statusChanged(League::Status status);
    void roleChanged(League::Role role);
    void profileChanged(Profile* profile);
    void receivedInvite(Offer* offer);
    void tasksChanged();
    void tasksAvailableChanged();
    void chatPatternsChanged();
    void globalchatChanged();

    Q_INVOKABLE void profilesChanged();
    void patternsChanged();
    void tagsChanged();
    void roomsChanged();
    void membersChanged();
    void connectionChanged();
//    void roomDumpReceived(const QString& roomId, );

private slots:
    void onConnected(int role);
    void onDisconnected();
    void onCheck(const Offer& offer);
    void onInvite(const Offer& offer);
    void onResume(const Offer& offer);
    void onCancel(const Offer& offer);
    void onTaskFinished();

    void onTag(TaskTag* tag);
    void onPattern(AnswerPattern* pattern);
    void onChatTemplate(const QString& type, const QString& name, const QString& pattern);
    void onMessage(const QString& room, const QString& id, const QString& from, const QString& text);
    void onImage(const QString& room, const QString& id, const QString& from, const QUrl&);
    void onAnswer(const QString& room, const QString& id, const QString& from, const QString&);
    void onProgress(const QString& room, const QString& id, const QString& from, const xmpp::Progress&);
    void onOffer(const QString& room, const QString& id, const Offer& offer);
    void onRoomOffer(const QString& room, const Offer& offer);

    void onPresenceChanged(const QString& user, bool available);
    void onMembersChanged() {
        emit membersChanged();
    }

public:
    explicit League(QObject* parent = 0);
    virtual ~League() { if (m_connection) m_connection->disconnect(); }

protected:
    doSearch* parent() const;

private:
    Offer* registerOffer(const Offer&);
    void startTask(Offer* offer, bool cont = false);

private:
    QMap<QString, Offer*> m_offers;
    QHash<QString, Task*> m_tasks;
    QList<TaskTag*> m_tags;
    QList<AnswerPattern*> m_patterns;
    Status m_status = LS_OFFLINE;
    Role m_role = NONE;
    xmpp::ExpLeagueConnection* m_connection = 0;
    ImagesStore* m_store = 0;
    Profile* m_profile = 0;
    bool m_reconnect = false;
    QSet<QString> m_known_ids;
    QString m_admin_focus;
    QHash<QString, QStringList> m_chat_templates;
    QHash<QString, QString> m_chat_template_contents;
};

class ImagesStoreResponse: public QQuickImageResponse {
    Q_OBJECT

public:
    QQuickTextureFactory* textureFactory() const {
//        qDebug() << "Result image: " << m_result;
        return m_result;
    }

public:
    QString id() {
        return m_id;
    }

    bool needRetry() {
        return ++m_attempts < 10;
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
    int m_attempts = 0;
    QQuickTextureFactory* m_result = 0;
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
Q_DECLARE_METATYPE(expleague::League::Role)

#endif // LEAGUE_H

#ifndef TASK_H
#define TASK_H

#include <memory>
#include <functional>

#include <QObject>
#include <QList>
#include <QMap>
#include <QUrl>
#include <QDateTime>
#include <QDomElement>

#include <QMutex>

#include <QGeoCoordinate>
#include <QAbstractItemModel>
#include <QQmlListProperty>

#include <QDebug>

#include "./util/pholder.h"

class QTimer;
namespace expleague {
class League;
class Offer;
class Bubble;
class ChatMessage;
class Member;
class AnswerPattern;
class MarkdownEditorPage;
class TaskTag;
class Context;
namespace xmpp {
class Progress;
class ExpLeagueConnection;
}

class Offer: public QObject {
    Q_OBJECT

    Q_PROPERTY(QString room READ room CONSTANT)
    Q_PROPERTY(QString client READ client CONSTANT)
    Q_PROPERTY(QString topic READ topic CONSTANT)
    Q_PROPERTY(bool local READ local CONSTANT)
    Q_PROPERTY(bool hasLocation READ hasLocation CONSTANT)
    Q_PROPERTY(QGeoCoordinate location READ location CONSTANT)
    Q_PROPERTY(double latitude READ latitude CONSTANT)
    Q_PROPERTY(double longitude READ longitude CONSTANT)
    Q_PROPERTY(long duration READ duration CONSTANT)
    Q_PROPERTY(QStringList images READ images CONSTANT)
    Q_PROPERTY(long timeLeft READ timeLeft NOTIFY timeTick)
    Q_PROPERTY(QString comment READ comment CONSTANT)
    Q_PROPERTY(QString draft READ draft CONSTANT)
    Q_PROPERTY(QString region READ region NOTIFY regionChanged)

    Q_ENUMS(Urgency)
    Q_ENUMS(FilterType)
    Q_ENUMS(Status)

public:
    enum Urgency {
        TU_DAY,
        TU_ASAP
    };

    enum FilterType {
        TFT_ACCEPT,
        TFT_REJECT,
        TFT_PREFER,
        TFT_LAST
    };

    enum Status {
        OPEN,
        FORMATION,
        LFE,
        IN_WORK,
        DELIVERY,
        UNSEEN,
        DONE,
    };

    QString roomJid() const { return m_room; }
    QString room() const { return m_room.section('@', 0, 0); }
    QString topic() const { return m_topic; }
    QString comment() const { return m_comment; }
    QString draft() const { return m_draft; }
    QString client() const { return m_client; }
    QString region() const { return m_region; }

    bool local() const { return m_local; }
    bool hasLocation() const { return m_location.isValid(); }
    QGeoCoordinate location() { return hasLocation() ? m_location : QGeoCoordinate(-10, 300); }
    double longitude() const { return hasLocation() ? m_location.longitude() : -10; }
    double latitude() const { return hasLocation() ? m_location.latitude() : -10; }
    QDateTime started() const { return m_started; }

    QStringList images() const { return m_images; }

    long duration() const {
        switch (m_urgency) {
        case TU_DAY:
            return 24 * 60 * 60 * 1000;
        case TU_ASAP:
            return 60 * 60 * 1000;
        }
    }

    long timeLeft() const { return QDateTime::currentDateTimeUtc().msecsTo(m_started.addMSecs(duration())); }

    QList<TaskTag*> tags() const { return m_tags; }
    QList<AnswerPattern*> patterns() const { return m_patterns; }

    QMap<QString, FilterType> filter() const { return m_filter; }

    void start();
    void setRegion(const QString& region) { m_region = region; emit regionChanged(); }

public:
    explicit Offer(QDomElement xml = QDomElement(), QObject *parent = 0);
    explicit Offer(const QString& client,
                   const QString& room,
                   const QString& topic,
                   Urgency urgency,
                   bool local,
                   const QStringList& images,
                   const QMap<QString, FilterType>& filter,
                   QGeoCoordinate location,
                   QDateTime started,
                   QList<TaskTag*> tags,
                   QList<AnswerPattern*> m_patterns,
                   const QString& comment,
                   const QString& draft);

public:
    QDomElement toXml() const;
    friend bool operator ==(const Offer& left, const Offer& right);
    //TODO delete this
//    ~Offer(){
//        qDebug() << "remove offer";
//    }

signals:
    void timeTick();
    void cancelled();
    void regionChanged();

private slots:
    void tick();

private:
    friend class xmpp::ExpLeagueConnection;
    friend class Task;

    QString m_client;
    QString m_room;
    QString m_topic;
    Urgency m_urgency = TU_DAY;
    bool m_local = false;
    QStringList m_images;
    QMap<QString, FilterType> m_filter;
    QGeoCoordinate m_location;
    QDateTime m_started;
    QList<TaskTag*> m_tags;
    QList<AnswerPattern*> m_patterns;
    QString m_comment;

    QTimer* m_timer = 0;
    QString m_region;
    QString m_draft;
};

bool operator ==(const Offer& left, const Offer& right);

class RoomStatus;
class Task: public QObject {
    Q_OBJECT

    Q_PROPERTY(expleague::Offer* offer READ offer NOTIFY offerChanged)
    Q_PROPERTY(expleague::Member* client READ client NOTIFY offerChanged)
    Q_PROPERTY(expleague::Context* context READ context NOTIFY offerChanged)
    Q_PROPERTY(QQmlListProperty<expleague::Bubble> chat READ chat NOTIFY bubblesChanged)
    Q_PROPERTY(QQmlListProperty<expleague::TaskTag> tags READ tags NOTIFY tagsChanged)
    Q_PROPERTY(QQmlListProperty<expleague::AnswerPattern> patterns READ patterns NOTIFY patternsChanged)
    Q_PROPERTY(QStringList phones READ phones NOTIFY phonesChanged)
    Q_PROPERTY(QString comment READ comment WRITE setComment NOTIFY commentChanged)

    Q_PROPERTY(QStringList banned READ banned NOTIFY filterChanged)
    Q_PROPERTY(QStringList accepted READ accepted NOTIFY filterChanged)
    Q_PROPERTY(QStringList preferred READ preferred NOTIFY filterChanged)

    Q_PROPERTY(QQmlListProperty<expleague::Member> experts READ expertsQml NOTIFY filterChanged)
    Q_PROPERTY(QList<int> roles READ roles NOTIFY filterChanged)

    Q_PROPERTY(expleague::MarkdownEditorPage* answer READ answer NOTIFY answerChanged)
    Q_PROPERTY(expleague::RoomStatus* status READ status CONSTANT)

    Q_ENUMS(Status)

public:
    enum Status: int {
        OPEN,
        CHAT,
        RESPONSE,
        CONFIRMATION,
        OFFER,
        WORK,
        DELIVERY,
        FEEDBACK,
        CLOSED,
        VERIFY,
    };

public:
    Offer* offer() const { return m_offer; }
    RoomStatus* status() const;

    QQmlListProperty<Bubble> chat() { return QQmlListProperty<Bubble>(this, m_chat); }

    MarkdownEditorPage* answer() const { return m_answer; }

    Member* client() const;

    QQmlListProperty<TaskTag> tags() { return QQmlListProperty<TaskTag>(this, m_tags); }
    QQmlListProperty<AnswerPattern> patterns() { return QQmlListProperty<AnswerPattern>(this, m_patterns); }
    QStringList phones() { return m_phones; }

    QStringList banned() const { return filter(Offer::TFT_REJECT); }
    QStringList accepted() const { return filter(Offer::TFT_ACCEPT); }
    QStringList preferred() const { return filter(Offer::TFT_PREFER); }

    QQmlListProperty<Member> expertsQml() const { return QQmlListProperty<Member>(const_cast<Task*>(this), const_cast<QList<Member*>&>(m_experts)); }
    QList<int> roles() const { return m_roles; }

    QString id() const;
    Context* context() const { return m_context; }
    QString comment() const { return m_comment; }
    void setComment(const QString& comment) { m_comment = comment; emit commentChanged(); }

public:
    Q_INVOKABLE void enter() const;
    Q_INVOKABLE void exit() const;

    Q_INVOKABLE void verify() const;
    Q_INVOKABLE void close(const QString& shortAnswer);
    Q_INVOKABLE void sendMessage(const QString& str) const;
    Q_INVOKABLE void sendAnswer(const QString& shortAnswer, int difficulty, int success, bool extraInfo);
    Q_INVOKABLE void tag(TaskTag*);
    Q_INVOKABLE void pattern(AnswerPattern*);
    Q_INVOKABLE void phone(const QString&);
    Q_INVOKABLE void cancel();
    Q_INVOKABLE void stop();
    Q_INVOKABLE void suspend(int seconds);
    Q_INVOKABLE void clearFilter();
    Q_INVOKABLE void filter(Member* members, int role);
    Q_INVOKABLE void commitOffer(const QString& topic, const QString& comment, const QList<Member*>& selected = QList<Member*>()) const;


    bool active() const { return m_context; }
    void setContext(Context* context);

public:
    const QList<MarkdownEditorPage*>& receivedAnswers() const { return m_answers; }
    League* parent() const;

public:
    explicit Task(Offer* offer = 0, League* parent = 0);
    explicit Task(const QString& room, League* parent = 0);

signals:
    void offerChanged();
    void statusChanged();

    void bubblesChanged();
    void chatChanged();

    void tagsChanged();
    void patternsChanged();
    void phonesChanged();

    void receivedProgress(const xmpp::Progress&);
    void finished();
    void cancelled();

    void filterChanged();
    void answerChanged();

    void commentChanged();

public slots:
    void setAnswer(MarkdownEditorPage* answer) {
        m_answer = answer;
        emit answerChanged();
    }
    void setOffer(Offer* offer);

    void messageReceived(const QString& from, const QString& text);
    void imageReceived(const QString& from, const QUrl& id);
    void answerReceived(const QString& from, const QString& text);
    void progressReceived(const QString& from, const xmpp::Progress& progress);
    void cancelReceived();

    void urlVisited(const QUrl&) const;

private:
    Bubble* bubble(const QString& from);
    QStringList filter(Offer::FilterType type) const;
    void setFilter(QMap<QString, Offer::FilterType> filter);

private:
    QString m_room;
    Offer* m_offer;
    QList<Bubble*> m_chat;
    MarkdownEditorPage* m_answer = 0;

    QMap<QString, Offer::FilterType> m_filter;
    QList<MarkdownEditorPage*> m_answers;
    QStringList m_phones;
    Context* m_context = 0;

    QList<TaskTag*> m_tags;
    QList<AnswerPattern*> m_patterns;

    QList<Member*> m_experts;
    QList<int> m_roles;
    QString m_comment;
};

class ChatMessage: public QObject {
    Q_OBJECT

    Q_PROPERTY(QString reference READ reference CONSTANT)
    Q_PROPERTY(QString text READ text CONSTANT)
    Q_PROPERTY(bool action READ action CONSTANT)

public:
    QString reference() {
        return m_reference.toString();
    }

    QString text() {
        return m_text;
    }

    bool action() {
        return m_action_available;
    }

    Q_INVOKABLE void fire() {
        m_action();
    }

public:
    explicit ChatMessage(QObject* parent = 0): QObject(parent) {}
    explicit ChatMessage(const QString& text, QObject* parent = 0): QObject(parent), m_text(text) {}
    explicit ChatMessage(const QUrl& imageUrl, QObject* parent = 0): QObject(parent), m_reference(imageUrl) {}
    explicit ChatMessage(std::function<void ()> action, const QString& description, QObject* parent = 0): QObject(parent), m_text(description), m_action(action), m_action_available(true) {}

private:
    QUrl m_reference;
    QString m_text;
    std::function<void ()> m_action;
    bool m_action_available = false;
};

class Bubble: public QAbstractListModel {
    Q_OBJECT

    Q_PROPERTY(bool incoming READ incoming CONSTANT)
    Q_PROPERTY(QString from READ from CONSTANT)

public:

    QVariant data(const QModelIndex &index, int role) const {
        QMutexLocker lock(&m_lock);
        QVariant var;
        if (role == Qt::UserRole)
            var.setValue(m_messages.at(index.row()));
        return var;
    }

    int rowCount(const QModelIndex &) const {
        QMutexLocker lock(&m_lock);
        return m_messages.size();
    }

    QHash<int, QByteArray> roleNames() const {
        QHash<int, QByteArray> result;
        result[Qt::UserRole] = "msg";
        return result;
    }

    QString from() const {
        return m_from;
    }

    bool incoming() const;

public:
    void append(ChatMessage* msg) {
        QMutexLocker lock(&m_lock);
        beginInsertRows(QModelIndex(), m_messages.size(), m_messages.size());
        m_messages.append(msg);
        endInsertRows();
    }

public:
    explicit Bubble(const QString& from = "me", QObject* parent = 0): QAbstractListModel(parent), m_from(from), m_lock(QMutex::RecursionMode::Recursive) {}

private:
    QString m_from;
    QList<ChatMessage*> m_messages;
    mutable QMutex m_lock;
};
}

#include <QQuickItem>

QML_DECLARE_TYPE(expleague::Task)
QML_DECLARE_TYPE(expleague::Offer)
QML_DECLARE_TYPE(expleague::Bubble)
QML_DECLARE_TYPE(expleague::ChatMessage)

#endif // TASK_H

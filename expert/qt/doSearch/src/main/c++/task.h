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

class QTimer;
namespace expleague {
class League;
class Offer;
class Bubble;
class ChatMessage;
class Member;
class AnswerPattern;
class TaskTag;
class Context;
namespace xmpp {
class Progress;
}

class ReceivedAnswer: public QObject {
    Q_OBJECT

    Q_PROPERTY(QString text READ text CONSTANT)
    Q_PROPERTY(Member* author READ author CONSTANT)
public:
    QString text() {
        return m_text;
    }

    Member* author() {
        return m_author;
    }

signals:
    void requestFocus();

public:
    ReceivedAnswer(QObject* parent = 0): QObject(parent) {}
    ReceivedAnswer(Member* author, const QString& text, QObject* parent = 0): QObject(parent), m_text(text), m_author(author) {}

private:
    QString m_text;
    Member* m_author;
};

class Task: public QObject {
    Q_OBJECT

    Q_PROPERTY(expleague::Offer* offer READ offer CONSTANT)
    Q_PROPERTY(expleague::Context* context READ context CONSTANT)
    Q_PROPERTY(QQmlListProperty<expleague::Bubble> chat READ chat NOTIFY chatChanged)
    Q_PROPERTY(QString answer READ answer WRITE setAnswer NOTIFY answerChanged)
    Q_PROPERTY(QQmlListProperty<expleague::TaskTag> tags READ tags NOTIFY tagsChanged)
    Q_PROPERTY(QQmlListProperty<expleague::AnswerPattern> patterns READ patterns NOTIFY patternsChanged)
    Q_PROPERTY(QStringList phones READ phones NOTIFY phonesChanged)

public:
    Offer* offer() const { return m_offer; }

    QQmlListProperty<Bubble> chat() { return QQmlListProperty<Bubble>(this, m_chat); }

    QString answer() const { return m_answer; }

    QQmlListProperty<ReceivedAnswer> answers() { return QQmlListProperty<ReceivedAnswer>(this, m_answers); }

    QQmlListProperty<TaskTag> tags() { return QQmlListProperty<TaskTag>(this, m_tags); }
    QQmlListProperty<AnswerPattern> patterns() { return QQmlListProperty<AnswerPattern>(this, m_patterns); }
    QStringList phones() { return m_phones; }

    QString id() const;
    Context* context() const { return m_context; }

public:
    Q_INVOKABLE void sendMessage(const QString& str) const;
    Q_INVOKABLE void sendAnswer();
    Q_INVOKABLE void tag(TaskTag*);
    Q_INVOKABLE void pattern(AnswerPattern*);
    Q_INVOKABLE void phone(const QString&);

    void setContext(Context* context) {m_context = context;}

public:
    const QList<ReceivedAnswer*>& receivedAnswers() const { return m_answers; }
    League* parent() const;

public:
    Task(Offer* offer = 0, QObject* parent = 0);

signals:
    void chatChanged();

    void answerChanged(const QString&);
    void answerReset(const QString&);

    void tagsChanged();
    void patternsChanged();
    void phonesChanged();

    void receivedAnswer(ReceivedAnswer*);
    void receivedProgress(const xmpp::Progress&);
    void finished();
    void cancelled();

public slots:
    void setAnswer(const QString& answer) {
        m_answer = answer;
        answerChanged(answer);
    }

    void messageReceived(const QString& from, const QString& text);
    void imageReceived(const QString& from, const QUrl& id);
    void answerReceived(const QString& from, const QString& text);
    void progressReceived(const QString& from, const xmpp::Progress& progress);
    void cancelReceived() { cancelled(); }

private:
    Bubble* bubble(const QString& from);

private:
    Offer* m_offer;
    QList<Bubble*> m_chat;
    QString m_answer;

    QList<TaskTag*> m_tags;
    QList<AnswerPattern*> m_patterns;
    QList<ReceivedAnswer*> m_answers;
    QStringList m_phones;
    Context* m_context;
};

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

    Q_ENUMS(Urgency)
    Q_ENUMS(FilterType)
public:
    enum Urgency {
        TU_DAY,
        TU_ASAP
    };

    enum FilterType {
        TFT_ACCEPT,
        TFT_REJECT,
        TFT_PREFER,
    };

    QString roomJid() const { return m_room; }

    QString room() const { return m_room.section('@', 0, 0); }

    QString topic() const { return m_topic; }

    QString client() const { return m_client; }

    bool local() const { return m_local; }

    bool hasLocation() const { return m_location.get(); }

    QGeoCoordinate location() { return hasLocation() ? *m_location : QGeoCoordinate(-10, 300); }

    double longitude() const {
        return hasLocation() ? m_location->longitude() : -10;
    }

    double latitude() const {
        return hasLocation() ? m_location->latitude() : -10;
    }

    QStringList images() const {
        return m_images;
    }

    long duration() const {
        switch (m_urgency) {
        case TU_DAY:
            return 24 * 60 * 60 * 1000;
        case TU_ASAP:
            return 60 * 60 * 1000;
        }
    }

    long timeLeft() const {
        return QDateTime::currentDateTimeUtc().msecsTo(m_started.addMSecs(duration()));
    }

public:
    explicit Offer(QDomElement xml, QObject *parent = 0);
    explicit Offer(QObject *parent = 0): QObject(parent) {}

public:
    QDomElement toXml() const;

signals:
    void timeTick();
    void cancelled();

private slots:
    void tick();

private:
    Urgency m_urgency = TU_DAY;
    bool m_local = false;
    QString m_room;
    QString m_client;
    QString m_topic;
    QStringList m_images;
    QMap<QString, FilterType> m_filter;
    std::unique_ptr<QGeoCoordinate> m_location;
    QDateTime m_started;
    QTimer* m_timer;
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
QML_DECLARE_TYPE(expleague::ReceivedAnswer)

#endif // TASK_H

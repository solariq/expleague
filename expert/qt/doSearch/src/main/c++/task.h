#ifndef TASK_H
#define TASK_H

#include <QObject>
#include <QList>
#include <QMap>
#include <QUrl>
#include <QDateTime>
#include <QDomElement>

#include <QGeoCoordinate>

#include <QQmlListProperty>

namespace expleague {
class ExpLeagueConnection;
class Offer;
class Bubble;
class ChatMessage;

class Task: public QObject {
    Q_OBJECT

    Q_PROPERTY(expleague::Offer* offer READ offer CONSTANT)
    Q_PROPERTY(QQmlListProperty<Bubble> chat READ chat NOTIFY chatChanged)
    Q_PROPERTY(QString answer READ answer WRITE setAnswer NOTIFY answerChanged)
    Q_PROPERTY(QStringList answers READ answers CONSTANT)

public:
    Offer* offer() const {
        return m_offer;
    }

    QQmlListProperty<Bubble> chat() {
        return QQmlListProperty<Bubble>(this, m_chat);
    }

    QString answer() {
        return m_answer;
    }

    QStringList answers() {
        return m_answers;
    }

public:
    void setAnswer(const QString& answer) {
        m_answer = answer;
        answerChanged(answer);
    }

public:
    Task(Offer* offer = 0, QObject* parent = 0): QObject(parent), m_offer(offer) {}

signals:
    void chatChanged();
    void answerChanged(const QString&);

private:
    Offer* m_offer;
    QList<Bubble*> m_chat;
    QString m_answer;
    QStringList m_answers;
};

class Offer: public QObject {
    Q_OBJECT

    Q_PROPERTY(QString room READ room CONSTANT)
    Q_PROPERTY(QString client READ client CONSTANT)
    Q_PROPERTY(QString topic READ topic CONSTANT)
    Q_PROPERTY(bool local READ local CONSTANT)
    Q_PROPERTY(bool hasLocation READ hasLocation CONSTANT)
    Q_PROPERTY(QGeoCoordinate location READ location CONSTANT)
    Q_PROPERTY(QTime timeLeft READ timeLeft NOTIFY timeTick)

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

    QString roomJid() {
        return m_room;
    }

    QString room() {
        return m_room.section('@', 0);
    }

    QString topic() {
        return m_topic;
    }

    QString client() {
        return m_client;
    }

    bool local() {
        return m_local;
    }

    bool hasLocation() {
        return m_location.get();
    }

    QGeoCoordinate location() {
        return hasLocation() ? *m_location : QGeoCoordinate();
    }

    QTime timeLeft() {
        return QTime();
    }

public:
    explicit Offer(QDomElement xml, QObject *parent = 0);
    explicit Offer(QObject *parent = 0): QObject(parent) {}

public:
    QDomElement toXml();

signals:
    void timeTick();

private:
    Urgency m_urgency = TU_DAY;
    bool m_local = false;
    QString m_room;
    QString m_client;
    QString m_topic;
    QList<QUrl> m_attachments;
    QMap<QString, FilterType> m_filter;
    std::unique_ptr<QGeoCoordinate> m_location;
    QDateTime m_started;
};

class ChatMessage: public QObject {
    Q_OBJECT

    Q_PROPERTY(expleague::ChatMessage::Type type READ type CONSTANT)
    Q_PROPERTY(QUrl reference READ reference CONSTANT)
    Q_PROPERTY(QString text READ text CONSTANT)

    Q_ENUMS(Type)

public:
    enum Type {
        CMT_EMPTY,
        CMT_IMAGE,
        CMT_TEXT,
        CMT_ACTION,
    };

    Type type() {
        if (m_reference.get()) {
            return CMT_IMAGE;
        }
        else if (m_text.get()) {
            return CMT_TEXT;
        }
        else if (m_action) {
            return CMT_ACTION;
        }
        return CMT_EMPTY;
    }

    QUrl reference() {
        return *m_reference;
    }

    QString text() {
        return *m_text;
    }

    Q_INVOKABLE void fire() {
        (*m_action)();
    }

public:
    explicit ChatMessage(const QString& text, QObject* parent = 0): QObject(parent), m_text(new QString(text)) {}
    explicit ChatMessage(const QUrl& imageUrl, QObject* parent = 0): QObject(parent), m_reference(new QUrl(imageUrl)) {}
    explicit ChatMessage(void (*action)(), QObject* parent = 0): QObject(parent), m_action(action) {}

private:
    std::unique_ptr<QUrl> m_reference;
    std::unique_ptr<QString> m_text;
    void (*m_action)();
};

class Bubble: public QObject {
    Q_OBJECT

    Q_PROPERTY(QQmlListProperty<ChatMessage> messages READ messages NOTIFY messagesChanged)
    Q_PROPERTY(bool incoming READ incoming CONSTANT)
    Q_PROPERTY(QUrl avatar READ avatar CONSTANT)

public:
    QQmlListProperty<ChatMessage> messages() {
        return QQmlListProperty<ChatMessage>(this, m_messages);
    }

    bool incoming() {
        return m_incoming;
    }

    QUrl avatar() {
        return m_avatar;
    }

public:
    void append(ChatMessage* msg) {
        m_messages.append(msg);
        messagesChanged();
    }

signals:
    void messagesChanged();

public:
    Bubble(bool incoming, const QUrl& avatar, QObject* parent = 0): QObject(parent), m_incoming(incoming), m_avatar(avatar) {}

private:
    bool m_incoming;
    QUrl m_avatar;
    QList<ChatMessage*> m_messages;
};

}

#include <QQuickItem>

QML_DECLARE_TYPE(expleague::Task)
QML_DECLARE_TYPE(expleague::Offer)
#endif // TASK_H

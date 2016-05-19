#include <QThread>
#include <QRunnable>
#include <QQueue>
#include <QWaitCondition>
#include <QMultiMap>

#include <QSystemTrayIcon>
#include <QSound>

#include <QNetworkRequest>
#include <QNetworkReply>
#include <QNetworkAccessManager>

#include <QQuickWindow>

#include <QBuffer>
#include <QByteArray>

#include "league.h"
#include "dosearch.h"
#include "profile.h"
#include "protocol.h"
#include "dosearch.h"

#ifdef Q_OS_MAC
int showNotification(const char* titleC, const char* detailsC);
#else
int showNotification(const char* titleC, const char* detailsC) {
    trayIcon->showMessage(QString::fromUtf8(titleC), QString::fromUtf8(detailsC));
}
#endif

namespace expleague {

League::League(QObject *parent): QObject(parent) {
    m_store = new ImagesStore(this);
}

doSearch* League::parent() const {
    return qobject_cast<doSearch*>(QObject::parent());
}

using namespace xmpp;

void League::setActive(Profile *profile) {
    if (m_connection) {
        m_connection->disconnect();
        delete m_connection;
    }
    if (profile) {
//        qDebug() << "Activating " << profile->deviceJid();
        m_connection = new xmpp::ExpLeagueConnection(profile, this);
        QObject::connect(m_connection, SIGNAL(connected()), SLOT(connected()));
        QObject::connect(m_connection, SIGNAL(disconnected()), SLOT(disconnected()));
        QObject::connect(m_connection, SIGNAL(receiveCheck(Offer*)), SLOT(checkReceived(Offer*)));
        QObject::connect(m_connection, SIGNAL(receiveInvite(Offer*)), SLOT(inviteReceived(Offer*)));
        QObject::connect(m_connection, SIGNAL(receiveResume(Offer*)), SLOT(resumeReceived(Offer*)));
        QObject::connect(m_connection, SIGNAL(receiveCancel(Offer*)), SLOT(cancelReceived(Offer*)));
        QObject::connect(m_connection, SIGNAL(receiveTag(TaskTag*)), SLOT(tagReceived(TaskTag*)));
        QObject::connect(m_connection, SIGNAL(receivePattern(AnswerPattern*)), SLOT(patternReceived(AnswerPattern*)));
        QObject::connect(m_connection, SIGNAL(receiveMessage(QString,QString,QString)), SLOT(messageReceived(QString,QString,QString)));
        QObject::connect(m_connection, SIGNAL(receiveImage(QString,QString,QUrl)), SLOT(imageReceived(QString,QString,QUrl)));
        QObject::connect(m_connection, SIGNAL(receiveAnswer(QString,QString,QString)), SLOT(answerReceived(QString,QString,QString)));
        QObject::connect(m_connection, SIGNAL(receiveProgress(QString,QString,Progress)), SLOT(progressReceived(QString,QString,Progress)));
    }
    profileChanged(profile);
}

Member* League::findMember(const QString &id) const {
    return m_connection ? m_connection->find(id) : 0;
}

AnswerPattern* League::findPattern(const QString &id) const {
    foreach (AnswerPattern* pattern, m_patterns) {
        if (pattern->name() == id)
            return pattern;
    }
    return 0;
}

TaskTag* League::findTag(const QString &id) const {
    foreach (TaskTag* tag, m_tags) {
        if (tag->name() == id)
            return tag;
    }
    return 0;
}

void League::startTask(Offer* offer) {
    offer = normalizeOffer(offer);
    Task* task = new Task(offer, this);
    m_tasks.append(task);
    QObject::connect(task, SIGNAL(finished()), SLOT(taskFinished()));
    QObject::connect(task, SIGNAL(cancelled()), SLOT(taskFinished()));
    m_status = LS_ON_TASK;
    statusChanged(m_status);
    Context* context = new Context(task, parent());
    parent()->append(context);
    context->setActive(true);
}

void League::inviteReceived(Offer* offer) {
    offer = normalizeOffer(offer);
    QSound::play(":/sounds/owl.wav");

    showNotification(tr("Лига Экспертов").toUtf8().data(), (tr("Открыто задание на тему: '") + offer->topic() + "'").toUtf8().data());

    QQuickWindow* inviteDialog = doSearch::instance()->main()->findChild<QQuickWindow*>("invite");
    if (inviteDialog) {
        QVariant ret;
        QVariant offerValue;
        offerValue.setValue(offer);
        QMetaObject::invokeMethod(doSearch::instance()->main(), "invite",
                                  Q_RETURN_ARG(QVariant, ret),
                                  Q_ARG(QVariant, offerValue));
        QObject::connect(inviteDialog, SIGNAL(rejected(Offer*)), this, SLOT(rejectInvitation(Offer*)));
        QObject::connect(inviteDialog, SIGNAL(accepted(Offer*)), this, SLOT(acceptInvitation(Offer*)));
    }

    receivedInvite(offer);
    m_status = LS_INVITE;
    statusChanged(m_status);
}

Offer* League::normalizeOffer(Offer* offer) {
    QString roomId = offer->room();
    if (m_offers.contains(roomId)) {
        offer = m_offers[roomId];
    }
    else {
        offer->setParent(this);
        m_offers[offer->room()] = offer;
    }
    return offer;
}

void League::disconnected() {
    foreach(Task* task, m_tasks)
        task->finished();

    m_status = LS_OFFLINE;
    statusChanged(m_status);
}

void League::messageReceived(const QString& room, const QString& from, const QString& text) {
    foreach(Task* task, m_tasks) {
        if (task->id() == room) {
            task->messageReceived(from, text);
            break;
        }
    }
}

void League::imageReceived(const QString& room, const QString& from, const QUrl& url) {
    foreach(Task* task, m_tasks) {
        if (task->id() == room) {
            QString localUrl = "image://store/" + url.path().section('/', -1, -1);
            task->imageReceived(from, QUrl(localUrl));
            break;
        }
    }
}

void League::answerReceived(const QString& room, const QString& from, const QString& text) {
    foreach(Task* task, m_tasks) {
        if (task->id() == room) {
            task->answerReceived(from, text);
            break;
        }
    }
}

void League::progressReceived(const QString& room, const QString& from, const xmpp::Progress& progress) {
    foreach(Task* task, m_tasks) {
        if (task->id() == room) {
            task->progressReceived(from, progress);
            break;
        }
    }
}

League* League::instance() {
    return doSearch::instance()->league();
}

QString randomString(int length)
{
   const QString possibleCharacters("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789");
   const int randomStringLength = length;

   QString randomString;
   for(int i=0; i<randomStringLength; ++i) {
       int index = qrand() % possibleCharacters.length();
       QChar nextChar = possibleCharacters.at(index);
       randomString.append(nextChar);
   }
   return randomString;
}

QUrl League::uploadImage(const QImage &img) const {
    return m_store->upload(img);
}

Task::Task(Offer* offer, QObject* parent): QObject(parent), m_offer(offer) {
    QObject::connect(offer, SIGNAL(cancelled()), this, SLOT(cancelReceived()));
}

League* Task::parent() const {
    return static_cast<League*>(QObject::parent());
}

void Task::answerReceived(const QString &from, const QString& text) {
    ReceivedAnswer* answer = new ReceivedAnswer(doSearch::instance()->league()->findMember(from), text, this);
    m_answers.append(answer);
    receivedAnswer(answer);

    Bubble* bubble = this->bubble(from);

    bubble->append(new ChatMessage([answer]() -> void {
        answer->requestFocus();
    }, "Ответ", this));
}

void Task::messageReceived(const QString& from, const QString& text) {
    Bubble* bubble = this->bubble(from);
    bubble->append(new ChatMessage(text, this));
}

void Task::imageReceived(const QString& from, const QUrl& id) {
    Bubble* bubble = this->bubble(from);
    bubble->append(new ChatMessage(id, this));
}

template <typename T>
void change(QList<T>& container, const T& item, xmpp::Progress::Operation operation) {
    switch(operation) {
    case xmpp::Progress::PO_VISIT:
    case xmpp::Progress::PO_ADD:
        container.append(item);
        break;
    case xmpp::Progress::PO_REMOVE:
        container.removeOne(item);
        break;
    }
}

void Task::progressReceived(const QString&, const xmpp::Progress& progress) {
    switch(progress.target) {
    case xmpp::Progress::PO_PATTERN:
        change<AnswerPattern*>(m_patterns, parent()->findPattern(progress.name), progress.operation);
        patternsChanged();
        break;
    case xmpp::Progress::PO_TAG:
        change<TaskTag*>(m_tags, parent()->findTag(progress.name), progress.operation);
        tagsChanged();
        break;
    case xmpp::Progress::PO_PHONE:
        change<QString>(m_phones, progress.name, progress.operation);
        phonesChanged();
        break;
    case xmpp::Progress::PO_URL:
        break;
    }
}

Bubble* Task::bubble(const QString& from) {
    Bubble* bubble;
    if (m_chat.isEmpty() || m_chat.last()->from() != from) {
        m_chat.append(bubble = new Bubble(from, this));
        chatChanged();
    }
    else bubble = m_chat.last();
    return bubble;
}

void Task::sendMessage(const QString &str) const {
    parent()->connection()->sendMessage(offer()->roomJid(), str);
}

void Task::sendAnswer() {
//    qDebug() << "Sending answer: " << answer();
    parent()->connection()->sendAnswer(offer()->roomJid(), answer());
    emit finished();
}

void Task::tag(TaskTag* tag) {
    qDebug() << "Sending tag: " << tag;
    m_tags.append(tag);
    tagsChanged();
    parent()->connection()->sendProgress(offer()->roomJid(), {xmpp::Progress::PO_ADD, xmpp::Progress::PO_TAG, tag->name()});
}

void Task::pattern(AnswerPattern* pattern) {
    m_patterns.append(pattern);
    patternsChanged();
    parent()->connection()->sendProgress(offer()->roomJid(), {xmpp::Progress::PO_ADD, xmpp::Progress::PO_PATTERN, pattern->name()});
}

void Task::phone(const QString& phone) {
    m_phones.append(phone);
    phonesChanged();
    parent()->connection()->sendProgress(offer()->roomJid(), {xmpp::Progress::PO_ADD, xmpp::Progress::PO_PHONE, phone});
}

QString Task::id() const  {
    return m_offer ? m_offer->room() : "";
}

bool Bubble::incoming() const {
    return m_from != doSearch::instance()->league()->id();
}

class ImagesStorePrivate: public QThread {
    static const QString IMAGE_STORE_MAGIC;
    static const QDir CACHE_ROOT;
public:
    void enqueue(ImagesStoreResponse* task) {
        QMutexLocker lock(&m_lock);
        m_queue.append(task);
        m_condition.wakeOne();
    }

    void run() {
        forever {
            ImagesStoreResponse* task;
            {
                QMutexLocker lock(&m_lock);
                while (m_queue.empty()) {
                    m_condition.wait(&m_lock);
                }
                task = m_queue.takeFirst();
            }
            qDebug() << "Request for " << task->id() << " received";
            QFile cacheFile(m_ava_cache_dir.filePath(task->id()));
            if (!cacheFile.exists()) {
                m_lock.lock();
                if (!m_pending.contains(task->id())) {
                    m_lock.unlock();
                    emit m_facade->requestImageById(task->id());
                    m_lock.lock();
                }
                m_pending.insert(task->id(), task);
                m_lock.unlock();
            }
            else {
                qDebug() << "Cache hit on " << task->id() << " file: " << cacheFile.fileName();
                task->setResult(QImage(cacheFile.fileName()));
            }
        }
    }

    void sendRequest(const QString& id) {
        QMutexLocker locker(&m_lock);
        qDebug() << "Sending images store request " << id;
        QNetworkRequest request(QUrl(baseUrl() + id));

        request.setOriginatingObject(m_pending.find(id).value());
        m_replies.append(m_nam->get(request));
    }

    void domain(const QString& domain) {
        {
            QMutexLocker locker(&m_lock);
            foreach(QNetworkReply* reply, m_replies) {
                reply->abort();
            }
            m_replies.clear();
        }
        m_domain = domain;
        CACHE_ROOT.mkpath(domain);
        m_ava_cache_dir.setPath(CACHE_ROOT.absoluteFilePath(domain));
        {
            foreach(QString id, m_pending.keys()) {
                emit m_facade->requestImageById(id);
            }
        }
    }

    QUrl upload(const QImage& img) {
        QByteArray requestBodyData;
        QByteArray imageId = randomString(10).toLatin1() + ".png";
        QByteArray boundary = randomString(20).toLatin1();

        requestBodyData.append("--" + boundary + "\r\n");
        requestBodyData.append("Content-Disposition: form-data; name=\"id\"\r\n\r\n" + imageId + "\r\n");
        requestBodyData.append("--" + boundary + "\r\n");
        requestBodyData.append("Content-Disposition: form-data; name=\"image\"; filename=\"" + imageId + "\"\r\n");
        requestBodyData.append("Content-Type: image/jpeg\r\n\r\n");
        QByteArray imageBuffer;
        QBuffer buffer(&imageBuffer);
        img.save(&buffer, "PNG");
        requestBodyData.append(imageBuffer);
        requestBodyData.append("\r\n");
        requestBodyData.append("--" + boundary + "--\r\n");
        QNetworkRequest request((QUrl)baseUrl());
        request.setHeader(QNetworkRequest::ContentTypeHeader, "multipart/form-data; boundary=" + boundary);
        request.setHeader(QNetworkRequest::ContentLengthHeader, QByteArray::number(requestBodyData.length()));
        m_nam->post(request, requestBodyData);
        return QUrl(baseUrl() + imageId);
    }

public:
    void requestFinished(QNetworkReply* reply) {
        ImagesStoreResponse* firstPending = qobject_cast<ImagesStoreResponse*>(reply->request().originatingObject());
        if (!firstPending)
            return;
        qDebug() << "Web request finished for " << firstPending->id();
        QImage img;
        img.loadFromData(reply->readAll());
        if (!img.isNull()) {
            QString id = firstPending->id();
            qDebug() << "Saving image to " << m_ava_cache_dir.filePath(id);
            img.save(m_ava_cache_dir.filePath(id));
            {
                QMutexLocker locker(&m_lock);
                for (QMap<QString, ImagesStoreResponse*>::iterator iter = m_pending.find(id); iter != m_pending.end() && iter.key() == id; iter++) {
                    iter.value()->setResult(img);
                }
                m_pending.remove(id);
            }
        }
        {
            QMutexLocker locker(&m_lock);
            m_replies.removeOne(reply);
        }
    }

public:
    explicit ImagesStorePrivate(ImagesStore* facade): m_facade(facade), m_nam(new QNetworkAccessManager(facade)) {
        QObject::connect(m_nam, SIGNAL(finished(QNetworkReply*)), facade, SLOT(requestFinished(QNetworkReply*)));
        start();
    }

private:
    QString baseUrl() {
        if (m_domain == "localhost")
            return "http://localhost:8067/";
        else
            return "https://img." + m_domain + "/" + IMAGE_STORE_MAGIC + "/";
    }

private:
    QMultiMap<QString, ImagesStoreResponse*> m_pending;
    QList<QNetworkReply*> m_replies;
    QMutex m_lock;
    ImagesStore* m_facade;

    QQueue<ImagesStoreResponse*> m_queue;
    QWaitCondition m_condition;

    QString m_domain;
    QDir m_ava_cache_dir;
    QNetworkAccessManager* m_nam;
};
const QString ImagesStorePrivate::IMAGE_STORE_MAGIC = "OSYpRdXPNGZgRvsY";
const QDir ImagesStorePrivate::CACHE_ROOT = QDir(QStandardPaths::writableLocation(QStandardPaths::CacheLocation) + "/doSearch/images/");


void ImagesStore::requestFinished(QNetworkReply* reply) {
    m_instance->requestFinished(reply);
}

ImagesStore::ImagesStore(League* parent): QObject(parent) {
    m_instance = new ImagesStorePrivate(this);
    QObject::connect(parent, SIGNAL(profileChanged(Profile*)), this, SLOT(profileChanged(Profile*)));
    QObject::connect(this, SIGNAL(requestImageById(QString)), this, SLOT(imageRequested(QString)));
}

ImagesStore::~ImagesStore() {
    delete m_instance;
}

void ImagesStore::imageRequested(const QString& id) {
    m_instance->sendRequest(id);
}

void ImagesStore::profileChanged(Profile *profile) {
    m_instance->domain(profile->domain());
}

QUrl ImagesStore::upload(const QImage &image) const {
    return m_instance->upload(image);
}

QQuickImageResponse* ImagesStore::requestImageResponse(const QString& id, const QSize&) {
    qDebug() << "Image requested: " << id;
    ImagesStoreResponse* response = new ImagesStoreResponse(id);
    m_instance->enqueue(response);
    return response;
}

}
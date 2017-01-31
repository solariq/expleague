#include <QThread>
#include <QRunnable>
#include <QMutex>
#include <QMutexLocker>
#include <QQueue>
#include <QWaitCondition>
#include <QMultiMap>

#include <QSystemTrayIcon>
#include <QSound>

#include <QImageReader>

#include <QNetworkRequest>
#include <QNetworkReply>
#include <QNetworkAccessManager>

#include <QQuickWindow>

#include <QRegularExpression>

#include <QBuffer>
#include <QByteArray>

#include <QTimer>

#include <time.h>

#include "league.h"
#include "dosearch.h"
#include "profile.h"
#include "protocol.h"
#include "dosearch.h"
#include "model/pages/globalchat.h"

#ifdef Q_OS_MAC
int showNotification(const char* titleC, const char* detailsC);
#else
int showNotification(const char* titleC, const char* detailsC) {
    trayIcon->showMessage(QString::fromUtf8(titleC), QString::fromUtf8(detailsC));
    return 1;
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

void League::connect() {
    disconnect();
    m_connection = new xmpp::ExpLeagueConnection(m_profile, this);
    QObject::connect(m_connection, SIGNAL(connected(int)), SLOT(onConnected(int)));
    QObject::connect(m_connection, SIGNAL(disconnected()), SLOT(onDisconnected()));
    QObject::connect(m_connection, SIGNAL(check(const Offer&)), SLOT(onCheck(const Offer&)));
    QObject::connect(m_connection, SIGNAL(invite(const Offer&)), SLOT(onInvite(const Offer&)));
    QObject::connect(m_connection, SIGNAL(resume(const Offer&)), SLOT(onResume(const Offer&)));
    QObject::connect(m_connection, SIGNAL(cancel(const Offer&)), SLOT(onCancel(const Offer&)));
    QObject::connect(m_connection, SIGNAL(tag(TaskTag*)), SLOT(onTag(TaskTag*)));
    QObject::connect(m_connection, SIGNAL(pattern(AnswerPattern*)), SLOT(onPattern(AnswerPattern*)));
    QObject::connect(m_connection, SIGNAL(message(QString,QString,QString,QString)), SLOT(onMessage(QString,QString,QString,QString)));
    QObject::connect(m_connection, SIGNAL(image(QString,QString,QString,QUrl)), SLOT(onImage(QString,QString,QString,QUrl)));
    QObject::connect(m_connection, SIGNAL(answer(QString,QString,QString,QString)), SLOT(onAnswer(QString,QString,QString,QString)));
    QObject::connect(m_connection, SIGNAL(progress(QString,QString,QString,Progress)), SLOT(onProgress(QString,QString,QString,Progress)));
    QObject::connect(m_connection, SIGNAL(offer(QString,QString,Offer)), SLOT(onOffer(QString,QString,Offer)));
    QObject::connect(m_connection, SIGNAL(tasksAvailableChanged(int)), SLOT(onTasksAvailableChanged(int)));
    QObject::connect(m_connection, SIGNAL(roomStarted(QString,QString,QString)), SLOT(onRoomStarted(QString,QString,QString)));
    QObject::connect(m_connection, SIGNAL(roomOffer(QString,Offer)), SLOT(onRoomOffer(QString,Offer)));
    QObject::connect(m_connection, SIGNAL(presenceChanged(QString,bool)), SLOT(onPresenceChanged(QString,bool)));
    m_connection->connect();
    m_reconnect = true;
}

void League::disconnect() {
    m_reconnect = false;
    if (m_connection) {
        m_connection->disconnect();
    }
}

void League::setActive(Profile *profile) {
    disconnect();
    m_profile = profile;
    emit profileChanged(profile);
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

void League::startTask(Offer* offer, bool cont) {
    Task* task = this->task(offer->roomJid());
    task->setOffer(offer);
    QObject::connect(task, SIGNAL(finished()), SLOT(onTaskFinished()));
    QObject::connect(task, SIGNAL(cancelled()), SLOT(onTaskFinished()));
    m_status = LS_ON_TASK;
    emit statusChanged(m_status);
    Context* context = parent()->context("context/" + task->id(), task->offer()->topic().replace('\n', ' '));
    context->setTask(task);
    parent()->navigation()->open(context);
    Member* self = findMember(id());
    MarkdownEditorPage* answerPage = parent()->document("Ваш ответ", self, true, task->id() + "-" + "answer");
    if (!cont)
        answerPage->setTextContent("");
    context->transition(answerPage, Page::TYPEIN);
    task->setAnswer(answerPage);
    foreach (MarkdownEditorPage* document, context->documents()) {
        context->removeDocument(document);
    }

    context->appendDocument(answerPage);
    parent()->append(context);
    parent()->navigation()->open(answerPage);
}

void League::onInvite(const Offer& offer) {
    Offer* roffer = registerOffer(offer);
    QSound::play(":/sounds/owl.wav");

    showNotification(tr("Лига Экспертов").toUtf8().data(), (tr("Открыто задание на тему: '") + roffer->topic() + "'").toUtf8().data());

    QVariant ret;
    QVariant offerValue;
    offerValue.setValue(roffer);
    QMetaObject::invokeMethod(doSearch::instance()->main(), "invite",
                              Q_RETURN_ARG(QVariant, ret),
                              Q_ARG(QVariant, offerValue)
    );

    receivedInvite(roffer);
    m_status = LS_INVITE;
    emit statusChanged(m_status);
}

Offer* League::registerOffer(const Offer& offer) {
    QString roomId = offer.room();
    if (m_offers.contains(roomId))
        return m_offers[roomId];
    Offer* result = m_offers[roomId] = new Offer(offer.toXml(), this);
    result->start();
    return result;
}

void League::onDisconnected() {
    if (!m_connection)
        return;
    m_connection->deleteLater();
    m_connection = 0;
    foreach(Task* task, m_tasks.values())
        task->stop();
    foreach (RoomState* state, m_rooms) {
        state->deleteLater();
    }
    m_rooms.clear();
    emit roomsChanged();
    m_status = LS_OFFLINE;
    emit statusChanged(m_status);
    m_role = NONE;
    emit roleChanged(m_role);
    emit tasksAvailableChanged();
    if (m_reconnect)
        QTimer::singleShot(500, this, &League::connect);
}

static time_t prevMessageTS = 0;
void League::onMessage(const QString& room, const QString& id, const QString& from, const QString& text) {
    if (m_known_ids.contains(id))
        return;
    m_known_ids.insert(id);

    Task* const task = this->task(room);
    if (task->active())
        notifyIfNeeded(from, tr("Получено сообщение от ") + from + ": '" + text + "'");
    task->messageReceived(from, text);
    if (task->context())
        doSearch::instance()->navigation()->activate(task->context());
}

void League::onImage(const QString& room, const QString& id, const QString& from, const QUrl& url) {
    if (m_known_ids.contains(id))
        return;
    m_known_ids.insert(id);

    Task* const task = this->task(room);
    if (task->active())
        notifyIfNeeded(from, tr("Полученa картинка от ") + from);
    QString localUrl = "image://store/" + url.path().section('/', -1, -1);
    task->imageReceived(from, QUrl(localUrl));
}

void League::onAnswer(const QString& room, const QString& id, const QString& from, const QString& text) {
    if (m_known_ids.contains(id))
        return;
    m_known_ids.insert(id);

    Task* const task = this->task(room);
    task->answerReceived(from, text);
}

void League::onProgress(const QString& room, const QString& id, const QString& from, const xmpp::Progress& progress) {
    if (m_known_ids.contains(id))
        return;
    m_known_ids.insert(id);

    Task* const task = this->task(room);
    task->progressReceived(from, progress);
}

void League::onOffer(const QString& room, const QString& id, const Offer& offer) {
    if (m_known_ids.contains(id))
        return;
    m_known_ids.insert(id);
    onRoomOffer(room, offer);
}

void League::onRoomOffer(const QString& room, const Offer& offer) {
    Offer* roffer = registerOffer(offer);
    Task* const task = this->task(room);
    task->setOffer(roffer);
}

void League::onConnected(int role) {
    m_status = LS_ONLINE;
    emit statusChanged(m_status);
    m_role = (Role)(NONE + role);
    emit roleChanged(m_role);
}

void League::onCheck(const Offer& offer) {
    Offer* roffer = registerOffer(offer);
    Task* const task = this->task(offer.room());
    task->setOffer(roffer);
    m_connection->sendOk(roffer);
    m_status = LS_CHECK;
    emit statusChanged(m_status);
}

void League::onResume(const Offer& offer) {
    Offer* roffer = registerOffer(offer);
    startTask(roffer, true);
    m_connection->sendResume(roffer);
    notifyIfNeeded("", tr("Задание вернулось: ") + offer.topic());
}

void League::onCancel(const Offer& offer) {
    Offer* roffer = registerOffer(offer);
    emit roffer->cancelled();
    m_status = LS_ONLINE;
    emit statusChanged(m_status);
}

void League::onTaskFinished() {
    Task* const finished = qobject_cast<Task*>(sender());
    finished->setContext(0);
    foreach(Task* task, m_tasks.values()) {
        if (task->active())
            return;
    }

    m_status = LS_ONLINE;
    emit statusChanged(m_status);
}

void League::onTag(TaskTag* tag) {
    tag->setParent(this);
    foreach(TaskTag* current, m_tags) {
        if (current->name() == tag->name()) {
            m_tags.removeOne(current);
//                delete current;
        }
    }

    m_tags.append(tag);
    qSort(m_tags.begin(), m_tags.end(), [](const TaskTag* a, const TaskTag* b) {
        return a->name() < b->name();
    });
    emit tagsChanged();
}

void League::onPattern(AnswerPattern* pattern) {
    pattern->setParent(this);
    foreach(AnswerPattern* current, m_patterns) {
        if (current->name() == pattern->name()) {
            m_patterns.removeOne(current);
//                current->deleteLater();
        }
    }

    m_patterns.append(pattern);
    qSort(m_patterns.begin(), m_patterns.end(), [](const AnswerPattern* a, const AnswerPattern* b) {
        return a->name() < b->name();
    });

    emit patternsChanged();
}

void League::notifyIfNeeded(const QString& from, const QString& message, bool broadcast) {
    if (from != m_connection->id()) {
        if (time(0) - prevMessageTS > 10) {
            prevMessageTS = time(0);
            if (!broadcast)
                QSound::play(":/sounds/owl.wav");
            else
                QSound::play(":/sounds/kuku.wav");
            showNotification(tr("Лига Экспертов").toUtf8().data(), message.toUtf8().data());
        }
    }
}

void League::onRoomStarted(const QString& roomId, const QString& topic, const QString& client) {
    for (auto it = m_rooms.begin(); it != m_rooms.end(); it++) {
        if ((*it)->roomId() == roomId)
            return; // already exists
    }
    RoomState* room = new RoomState(roomId + "@muc." + xmpp::domain(static_cast<xmpp::ExpLeagueConnection*>(sender())->jid()), findMember(client), topic, this);
    m_rooms.append(room);
    room->connectTo(m_connection);
    emit roomsChanged();
}

Task* League::task(const QString& roomId) {
    QString room = xmpp::user(roomId);
    auto existing = m_tasks.find(room);
    if (existing != m_tasks.end())
        return existing.value();
//    qDebug() << "Creating task for room: " << room;
    Task* const newOne = new Task(room + "@muc." + xmpp::domain(m_connection->jid()), this);
    m_tasks[room] = newOne;
    emit tasksChanged();
    return newOne;
}

void League::onPresenceChanged(const QString& user, bool available) {
    Member* member = findMember(user);
    if (member)
        member->setStatus(available ? Member::ONLINE : Member::OFFLINE);
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

QUrl League::imageUrl(const QString& imageId) const {
    return m_store->url(imageId);
}

Task::Task(Offer* offer, QObject* parent): QObject(parent), m_room(offer->room()), m_offer(offer) {
    QObject::connect(offer, SIGNAL(cancelled()), this, SLOT(cancelReceived()));
}

Task::Task(const QString& roomId, QObject* parent): QObject(parent), m_room(roomId), m_offer(0) {}

League* Task::parent() const {
    return static_cast<League*>(QObject::parent());
}

QStringList Task::filter(Offer::FilterType type) const {
    QStringList filtered;
    for (auto expert = m_filter.begin(); expert != m_filter.end(); expert++)
        if (expert.value() == type)
            filtered.append(expert.key());
    return filtered;
}

void Task::setOffer(Offer* offer) {
    QObject::disconnect(m_offer);
    QObject::connect(offer, SIGNAL(cancelled()), this, SLOT(cancelReceived()));
    m_offer = offer;
    emit offerChanged();
}

void Task::answerReceived(const QString &from, const QString& text) {
    doSearch* dosearch = doSearch::instance();
    Member* author = parent()->findMember(from);
    MarkdownEditorPage* answerPage = dosearch->document("Ответ " + QString::number(m_answers.size() + 1), author, false, id() + "-" + QString::number(m_answers.size() + 1));
    answerPage->setTextContent(text);
    if (context()) {
        context()->appendDocument(answerPage);
        context()->transition(answerPage, Page::TYPEIN);
    }
    m_answers += answerPage;
    Bubble* bubble = this->bubble(from);
    bubble->append(new ChatMessage([answerPage, dosearch]() -> void {
        doSearch::instance()->navigation()->open(answerPage);
    }, "Ответ", this));
    emit chatChanged();
}

void Task::messageReceived(const QString& from, const QString& text) {
    Bubble* bubble = this->bubble(from);
    bubble->append(new ChatMessage(text, this));
    emit chatChanged();
}

void Task::imageReceived(const QString& from, const QUrl& id) {
    Bubble* bubble = this->bubble(from);
    bubble->append(new ChatMessage(id, this));
    emit chatChanged();
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

void Task::setContext(Context *context) {
    m_context = context;
    if (context)
        QObject::connect(context, SIGNAL(visitedUrl(QUrl)), this, SLOT(urlVisited(QUrl)));
}

void Task::urlVisited(const QUrl& url) const {
    parent()->connection()->sendProgress(offer()->roomJid(), {Progress::PO_VISIT, Progress::PO_URL, url.toString()});
}

Bubble* Task::bubble(const QString& from) {
    Bubble* bubble;
    if (m_chat.isEmpty() || m_chat.last()->from() != from) {
        m_chat.append(bubble = new Bubble(from, this));
        bubblesChanged();
    }
    else bubble = m_chat.last();
    return bubble;
}

void Task::sendMessage(const QString &str) const {
    parent()->connection()->sendMessage(offer()->roomJid(), str);
}

QString removeSpacesInside(const QString& textOrig, const QString& separator) {
    QString text = textOrig;
    int index = text.indexOf(separator);
    bool inside = false;
    while (index >= 0) {
        inside = !inside;
        if (inside) {
            index += separator.size();
            while (text.at(index) == ' ') {
                text.remove(index, 1);
            }
        }
        else {
            while (text.at(index - 1) == ' ') {
                text.remove(--index, 1);
            }
            index += separator.size();
        }
        index = text.indexOf(separator, index);
    }
    return text;
}

void Task::sendAnswer(const QString& shortAnswer, int difficulty, int success, bool extraInfo) {
//    qDebug() << "Sending answer: " << answer();
    QString text = answer()->textContent();
    text.replace("\t", "    ");
//    text = removeSpacesInside(text, "*");
    text = removeSpacesInside(text, "**");
    text.replace(QRegularExpression("#([^#])"), "# \\1");
    parent()->connection()->sendAnswer(offer()->roomJid(), difficulty, success, extraInfo, shortAnswer + "\n" + text);
    stop();
}

void Task::tag(TaskTag* tag) {
//    qDebug() << "Sending tag: " << tag;
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

void Task::cancel() {
    parent()->connection()->sendCancel(offer());
    stop();
}

void Task::suspend(int seconds) {
    parent()->connection()->sendSuspend(offer(), seconds);
    stop();
}

void Task::stop() {
    m_answers.clear();
    m_tags.clear();
    m_patterns.clear();
    emit finished();
}

void Task::enter() const {
    parent()->connection()->sendPresence(m_room);
}

void Task::commitOffer(const QString &topic, const QString& comment, const QList<Member*>& selected) const {
    QMap<QString, Offer::FilterType> filter = m_offer->m_filter;
    foreach(Member* expert, selected) {
        filter[expert->id()] = Offer::TFT_ACCEPT;
    }

    Offer offer(m_offer->client(),
                m_offer->room(),
                topic,
                m_offer->m_urgency,
                m_offer->local(),
                m_offer->images(),
                filter,
                m_offer->location(),
                m_offer->m_started,
                m_offer->tags(),
                m_offer->patterns(),
                comment);
    parent()->connection()->sendOffer(offer);
}

void Task::filter(const QString& memberId, Offer::FilterType type) {
    m_filter[memberId] = type;
    emit filterChanged();
}

QString Task::id() const  {
    return m_room;
}

bool Bubble::incoming() const {
    return m_from != doSearch::instance()->league()->id();
}

class ImagesStorePrivate: public QThread {
public:
    static const QString IMAGE_STORE_MAGIC;
    static const QDir CACHE_ROOT;
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
//            qDebug() << "Request for " << task->id() << " received";
            QFile cacheFile(m_ava_cache_dir.filePath(task->id()));
            if (!cacheFile.exists()) {
                m_lock.lock();
                if (!m_pending.contains(task->id())) {
                    m_lock.unlock();
                    emit m_facade->requestImageById(task->id());
                    m_lock.lock();
                }
                m_pending.insert(task->id(), task);
                QString id = task->id();
                QObject::connect(task, &QObject::destroyed, [this, id, task]() {
                    QMutexLocker locker(&m_lock);
                    m_pending.remove(id, task);
                });
                m_lock.unlock();
            }
            else {
//                qDebug() << "Cache hit on " << task->id() << " file: " << cacheFile.fileName();
                QImageReader reader(&cacheFile);
                reader.setAutoTransform(true);

                task->setResult(reader.read());
            }
        }
    }

    void sendRequest(const QString& id) {
        QMutexLocker locker(&m_lock);
//        qDebug() << "Sending images store request " << id;
        QNetworkRequest request(QUrl(baseUrl() + id));

        auto existing = m_pending.find(id);
        if (existing != m_pending.end())
            request.setOriginatingObject(existing.value());
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

    QString baseUrl() const {
        if (m_domain == "localhost")
            return "http://localhost:8067/";
        else
            return "https://img." + m_domain + "/" + IMAGE_STORE_MAGIC + "/";
    }

    QString cachePath(const QString& id) const {
        return m_ava_cache_dir.filePath(id);
    }

public:
    void requestFinished(QNetworkReply* reply) {
        ImagesStoreResponse* firstPending = qobject_cast<ImagesStoreResponse*>(reply->request().originatingObject());
        if (!firstPending)
            return;
//        qDebug() << "Web request finished for " << firstPending->id();
        QByteArray content = reply->readAll();
        QBuffer buffer(&content);
        QImageReader reader(&buffer);
        reader.setAutoTransform(true);
        QImage img = reader.read();
        if (!img.isNull()) {
            QString id = firstPending->id();
//            qDebug() << "Saving image to " << m_ava_cache_dir.filePath(id);
            img.save(m_ava_cache_dir.filePath(id));
            {
                QMutexLocker locker(&m_lock);
                foreach (ImagesStoreResponse* response, m_pending.values(id)) {
                    response->setResult(img);
                }
                m_pending.remove(id);
            }
        }
        else if (firstPending->needRetry()){ // retry
            QTimer::singleShot(5000, this, [this, firstPending]() {
//                qDebug() << "Retry";
                sendRequest(firstPending->id());
            });
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

    ~ImagesStorePrivate() { terminate(); wait(); }

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

QUrl ImagesStore::url(const QString &id) const {
    return QUrl("file:" + m_instance->cachePath(id));
}

QQuickImageResponse* ImagesStore::requestImageResponse(const QString& id, const QSize&) {
//    qDebug() << "Image requested: " << id;
    ImagesStoreResponse* response = new ImagesStoreResponse(id);
    QTimer::singleShot(10, [this, response]() {
        m_instance->enqueue(response);
    });
    return response;
}

QUrl League::normalizeImageUrlForUI(const QUrl& imageUrl) const {
    if (imageUrl.path().startsWith("/" + ImagesStorePrivate::IMAGE_STORE_MAGIC + "/")) {
        QString imageId = imageUrl.path().section('/', -1);
        return "image://store/" + imageId;
    }
    return imageUrl;
}

void RoomState::onRoomStatus(const QString& id, int status) {
    if (id != roomId())
        return;
    m_status = (Status)status;
    emit statusChanged(m_status);
    if (m_status == WORK) {
        m_orders++;
        emit ordersChanged();
    }
}

void RoomState::onFeedback(const QString& id, int feedback) {
    if (id != roomId())
        return;
    m_feedback = feedback;
    emit feedbackChanged();
}

void RoomState::onMessage(const QString& id, const QString& author) {
    if (id != roomId() || author == doSearch::instance()->league()->id())
        return;
    m_unread++;
    emit unreadChanged(m_unread);
}

void RoomState::onAssignment(const QString& id, const QString& expert, int iRole) {
    if (id != roomId())
        return;
    League::Role role = (League::Role)iRole;
    Member* const member = parent()->findMember(expert);
    switch (role) {
    case League::ADMIN:
        if (m_admins.indexOf(member) != m_admins.size() - 1) {
            m_admins.removeOne(member);
            m_admins.append(member);
            emit participantsChanged();
        }
        if (!m_admin_active) {
            m_admin_active = true;
            emit occupiedChanged();
        }
        break;
    default:
        if (m_admin_active) {
            m_admin_active = false;
            emit occupiedChanged();
        }
    }
}

bool RoomState::connectTo(xmpp::ExpLeagueConnection* connection) {
    if (xmpp::domain(m_jid) != xmpp::domain(connection->jid()))
        return false;
    connect(connection, SIGNAL(roomStatus(QString,int)), SLOT(onRoomStatus(QString,int)));
    connect(connection, SIGNAL(feedback(QString,int)), SLOT(onFeedback(QString,int)));
    connect(connection, SIGNAL(messageNotification(QString,QString)), SLOT(onMessage(QString,QString)));
    connect(connection, SIGNAL(assignment(QString,QString,int)), SLOT(onAssignment(QString,QString,int)));
    return true;
}

void RoomState::enter() {
    m_task->enter();
}

RoomState::RoomState(const QString& id, Member* client, const QString& topic, League* parent):
    QObject(parent),
    m_jid(id), m_client(client), m_topic(topic), m_task(parent->task(id))
{}
}

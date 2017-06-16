#include "league.h"

#include <time.h>

#include <QSound>
#include <QTimer>

#include <QQuickWindow>

#include "dosearch.h"
#include "profile.h"
#include "protocol.h"


#include "model/pages/globalchat.h"

#ifdef Q_OS_MAC
int showNotification(const char* titleC, const char* detailsC);
#else
#include <QSystemTrayIcon>

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
    QObject::connect(m_connection, SIGNAL(chatTemplate(QString,QString,QString)), SLOT(onChatTemplate(QString,QString,QString)));
    QObject::connect(m_connection, SIGNAL(message(QString,QString,QString,QString)), SLOT(onMessage(QString,QString,QString,QString)));
    QObject::connect(m_connection, SIGNAL(image(QString,QString,QString,QUrl)), SLOT(onImage(QString,QString,QString,QUrl)));
    QObject::connect(m_connection, SIGNAL(answer(QString,QString,QString,QString)), SLOT(onAnswer(QString,QString,QString,QString)));
    QObject::connect(m_connection, SIGNAL(progress(QString,QString,QString,xmpp::Progress)), SLOT(onProgress(QString,QString,QString,xmpp::Progress)));
    QObject::connect(m_connection, SIGNAL(offer(QString,QString,Offer)), SLOT(onOffer(QString,QString,Offer)));
    QObject::connect(m_connection, SIGNAL(roomOffer(QString,Offer)), SLOT(onRoomOffer(QString,Offer)));
    QObject::connect(m_connection, SIGNAL(presenceChanged(QString,bool)), SLOT(onPresenceChanged(QString,bool)));
    QObject::connect(m_connection, SIGNAL(membersChanged()), SLOT(onMembersChanged()));
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

Member* League::findMemberByName(const QString& name) const {
    foreach(Member* member, m_connection->members()) {
        if (member->name() == name)
            return member;
    }
    return 0;
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

GlobalChat* League::chat() const {
    return qobject_cast<GlobalChat*>(parent()->page(GlobalChat::ID));
}

void League::startTask(Offer* offer, bool cont) {
    Task* task = this->task(offer->roomJid());
    task->setOffer(offer);
    QObject::connect(task, SIGNAL(finished()), SLOT(onTaskFinished()));
    QObject::connect(task, SIGNAL(cancelled()), SLOT(onTaskFinished()));
    m_status = LS_ON_TASK;
    emit statusChanged(m_status);
    auto state = chat()->state(offer->room());
    if (state)
        state->clearUnread();
    Context* context = parent()->context("context/" + task->id(), task->offer()->topic().replace('\n', ' '));
    context->setTask(task);
    parent()->navigation()->open(context);
    foreach (MarkdownEditorPage* document, context->documents()) {
        context->removeDocument(document);
    }

    context->appendDocument(task->answer());
    if (task->answer()->textContent().isEmpty() && !cont)
        task->answer()->setTextContent(offer->draft());
    parent()->append(context);
    parent()->navigation()->open(task->answer());
    connection()->sendPresence(offer->roomJid(), true);
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
    if (m_offers.contains(roomId) && *m_offers[roomId] == offer)
        return m_offers[roomId];
    Offer* const result = new Offer(offer.toXml(), this);
    if (m_offers.contains(roomId)) {
        auto task = m_tasks.find(roomId);
        if (task != m_tasks.end())
            (*task)->setOffer(result);
    }
    m_offers[roomId] = result;
    result->start();
    return result;
}

void League::onDisconnected() {
    if (!m_connection)
        return;
    m_admin_focus = "";
    ExpLeagueConnection* connection = m_connection;
    m_connection = 0;
    connection->clearHistory();
    emit connectionChanged();
    QList<Task*> tasks = m_tasks.values();
    m_tasks.clear();
    foreach(Task* task, tasks) {
        task->stop();
        task->deleteLater();
    }

    m_status = LS_OFFLINE;
    emit statusChanged(m_status);
    m_role = NONE;
    m_known_ids.clear();
    emit roleChanged(m_role);
    emit tasksAvailableChanged();
    foreach(Offer* offer, m_offers.values()) {
        offer->deleteLater();
    }
    m_offers.clear();
    m_patterns.clear();
    m_tags.clear();
    m_chat_templates.clear();
    m_chat_template_contents.clear();
    connection->deleteLater();
    if (m_reconnect)
        QTimer::singleShot(500, this, &League::connect);
}

static time_t prevMessageTS = time(0);
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

void League::onRoomOffer(const QString& /*room*/, const Offer& offer) {
//    notifyIfNeeded(room, tr("Задание в комнате ") + room + tr(" изменено."));
    chat()->registerRoom(registerOffer(offer));
}

void League::onConnected(int role) {
    prevMessageTS = time(0);
    m_status = LS_ONLINE;
    emit statusChanged(m_status);
    m_role = (Role)(NONE + role);
    emit roleChanged(m_role);
    if (m_role == Role::ADMIN) {
        connection()->listExperts();
    }
    emit connectionChanged();
}

void League::onCheck(const Offer& offer) {
    Offer* roffer = registerOffer(offer);
    Task* const task = this->task(offer.room());
    task->setOffer(roffer);
    if (!task->active()) { // if the task is active the offer changed due to offer-change command
        m_connection->sendOk(roffer);
        m_status = LS_CHECK;
    }
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
        if (task && task->active())
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

void League::onChatTemplate(const QString& type, const QString& name, const QString& pattern) {
    if (m_chat_templates[type].contains(name))
        return;
    m_chat_templates[type].append(name);
    m_chat_template_contents[name] = pattern;
    emit chatPatternsChanged();
}

void League::notifyIfNeeded(const QString& from, const QString& message, bool broadcast) {
    if (from != m_connection->id()) {
        if (time(0) - prevMessageTS > 30) {
            prevMessageTS = time(0);
            if (!broadcast)
                QSound::play(":/sounds/owl.wav");
            else
                QSound::play(":/sounds/kuku.wav");
            showNotification(tr("Лига Экспертов").toUtf8().data(), message.toUtf8().data());
        }
    }
}

Task* League::task(const QString& roomId) {
    QString room = roomId.contains("@") ? xmpp::user(roomId) : roomId;
    auto existing = m_tasks.find(room);
    if (existing != m_tasks.end())
        return existing.value();
    if (!m_connection)
        return 0;
//    qDebug() << "Creating task for room: " << room;
    Task* const newOne = new Task(room + "@muc." + xmpp::domain(m_connection->jid()), this);
    auto offerPtr = m_offers.find(roomId);
    if (offerPtr != m_offers.end())
        newOne->setOffer(*offerPtr);
    m_tasks[room] = newOne;
    QObject::connect(newOne, &Task::destroyed, [this, room]() {
        m_tasks.remove(room);
    });
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

QUrl League::uploadImage(const QImage &img) const {
    return m_store->upload(img);
}

QUrl League::imageUrl(const QString& imageId) const {
    return m_store->url(imageId);
}

QStringList League::experts() const {
    if (!m_connection)
        return QStringList();
    QStringList result;
    foreach (Member* member, m_connection->members()) {
        QString name = member->name();
        if (name != member->id()) // TODO: make member role visible
            result.append(name);
    }
    return result;
}

void Member::append(RoomStatus* room) {
    if (!m_history.contains(room)) {
        m_history.append(room);
        std::sort(m_history.begin(), m_history.end(), [this](RoomStatus* left, RoomStatus* right) {
           return right->started().toTime_t() < left->started().toTime_t();
        });
        emit historyChanged();
    }
}

void Member::remove(RoomStatus* room) {
    m_history.removeAll(room);
    emit historyChanged();
}

void Member::requestHistory() const {
    ExpLeagueConnection* connection = League::instance()->connection();
    if (connection)
        connection->requestHistory(m_id);
}
}

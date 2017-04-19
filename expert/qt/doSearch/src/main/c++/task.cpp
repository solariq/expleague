#include "task.h"

#include <QRegularExpression>

#include <QDomDocument>
#include <QBuffer>

#include "league.h"
#include "protocol.h"
#include "model/context.h"
#include "dosearch.h"
#include "model/pages/editor.h"
#include "model/pages/globalchat.h"

namespace expleague {
League* Task::parent() const {
    return static_cast<League*>(QObject::parent());
}

QStringList Task::filter(Offer::FilterType type) const {
    QStringList filtered;
    for (auto expert = m_filter.begin(); expert != m_filter.end(); ++expert)
        if (expert.value() == type)
            filtered.append(expert.key());
    return filtered;
}

void Task::setOffer(Offer* offer) {
    if (m_offer)
        QObject::disconnect(m_offer);
    QObject::connect(offer, SIGNAL(cancelled()), this, SLOT(cancelReceived()));
    m_offer = offer;
    m_comment = offer->comment();
    m_patterns = offer->patterns();
    m_tags = offer->tags();
    if (!offer->draft().isEmpty() && answer()->textContent().isEmpty()) {
        answer()->setTextContent(m_offer->draft());
    }
    setFilter(offer->filter());
    emit tagsChanged();
    emit patternsChanged();
    emit filterChanged();
    emit offerChanged();
}

void Task::cancelReceived() {
    emit cancelled();
}

void Task::setFilter(QMap<QString, Offer::FilterType> filter) {
    m_filter = filter;
    foreach (QString id, filter.keys()) {
        Member* const expert = parent()->findMember(id);
        int index = m_experts.indexOf(expert);
        if (index < 0) {
            index = m_experts.size();
            m_experts.append(expert);
            m_roles.append((int)m_filter[id]);
        }
        else m_roles[index] = (int)m_filter[id];
    }
}

void Task::answerReceived(const QString &from, const QString& text) {
    doSearch* dosearch = doSearch::instance();
    Member* author = parent()->findMember(from);
    MarkdownEditorPage* answerPage = dosearch->document("Ответ " + QString::number(m_answers.size() + 1), author, false, id() + "-" + QString::number(m_answers.size() + 1));
    answerPage->setTextContent(text);
    answerPage->setEditable(false);
    if (context()) {
        context()->appendDocument(answerPage);
        context()->transition(answerPage, Page::TYPEIN);
    }
    m_answers += answerPage;
    Bubble* bubble = this->bubble(from);
    bubble->append(new ChatMessage([answerPage, dosearch]() -> void {
        doSearch::instance()->navigation()->open(answerPage);
    }, "Ответ", this));
//    if (!active())
//        m_answer->re
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

bool operator ==(const Offer& left, const Offer& right) {
    return  left.m_client == right.m_client &&
            left.m_room == right.m_room &&
            left.m_topic == right.m_topic &&
            left.m_urgency == right.m_urgency &&
            left.m_local == right.m_local &&
            left.m_images == right.m_images &&
            left.m_filter == right.m_filter &&
            left.m_location == right.m_location &&
            left.m_started == right.m_started &&
            left.m_tags == right.m_tags &&
            left.m_patterns == right.m_patterns &&
            left.m_comment == right.m_comment;
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
        emit patternsChanged();
        break;
    case xmpp::Progress::PO_TAG:
        change<TaskTag*>(m_tags, parent()->findTag(progress.name), progress.operation);
        emit tagsChanged();
        break;
    case xmpp::Progress::PO_PHONE:
        change<QString>(m_phones, progress.name, progress.operation);
        emit phonesChanged();
        break;
    case xmpp::Progress::PO_URL:
        break;
    }
}

RoomStatus* Task::status() const {
    return parent()->chat()->state(xmpp::user(m_room));
}

Member* Task::client() const {
    return m_offer ? parent()->findMember(xmpp::user(m_offer->client())) : 0;
}

void Task::setContext(Context *context) {
    m_context = context;
    if (context)
        QObject::connect(context, SIGNAL(visitedUrl(QUrl)), this, SLOT(urlVisited(QUrl)));
}

void Task::urlVisited(const QUrl& url) const {
    if (parent()->connection())
        parent()->connection()->sendProgress(offer()->roomJid(), {"", Progress::PO_VISIT, Progress::PO_URL, url.toString(), Progress::OS_NONE});
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
    if (parent()->connection())
        parent()->connection()->sendMessage(offer()->roomJid(), str);
}

void Task::close(const QString& shortAnswer) {
    if (!m_answers.empty()) {
        sendAnswer(shortAnswer.isEmpty() ? m_answers[0]->textContent().section("\n", 0, 0) : shortAnswer, -1, -1, false);
    }
    else {
        sendAnswer(shortAnswer + tr("\nОтвет получен в диалоге:\n") + shortAnswer, -1, -1, false);
    }
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
    if (!parent()->connection())
        return;
    if (!answer()->textContent().isEmpty()) {
        QString text = answer()->textContent();
        text.replace("\t", "    ");
        //    text = removeSpacesInside(text, "*");
        text = removeSpacesInside(text, "**");
        text.replace(QRegularExpression("#([^#])"), "# \\1");
        parent()->connection()->sendAnswer(offer()->roomJid(), difficulty, success, extraInfo, shortAnswer + "\n" + text);
        answer()->setTextContent("");
    }
    else parent()->connection()->sendAnswer(offer()->roomJid(), difficulty, success, extraInfo, shortAnswer);
    stop();
}

void Task::tag(TaskTag* tag) {
    if (active()) {
        if (parent()->connection())
            parent()->connection()->sendProgress(offer()->roomJid(), {"", xmpp::Progress::PO_ADD, xmpp::Progress::PO_TAG, tag->name(), xmpp::Progress::OS_NONE});
    }
    else {
        m_tags.append(tag);
        emit tagsChanged();
    }
}

void Task::pattern(AnswerPattern* pattern) {
    if (active()) {
        if (parent()->connection())
            parent()->connection()->sendProgress(offer()->roomJid(), {"", xmpp::Progress::PO_ADD, xmpp::Progress::PO_PATTERN, pattern->name(), xmpp::Progress::OS_NONE});
    }
    else {
        m_patterns.append(pattern);
        emit patternsChanged();
    }
}

void Task::phone(const QString& phone) {
    if (parent()->connection())
        parent()->connection()->sendProgress(offer()->roomJid(), {"", xmpp::Progress::PO_ADD, xmpp::Progress::PO_PHONE, phone, xmpp::Progress::OS_NONE});
}

void Task::cancel() {
    if (parent()->connection())
        parent()->connection()->sendCancel(offer());
    stop();
}

void Task::suspend(int seconds) {
    if (parent()->connection())
        parent()->connection()->sendSuspend(offer(), seconds);
    stop();
}

void Task::stop() {
    if (parent()->connection())
        parent()->connection()->sendPresence(offer()->roomJid(), false);
    m_answers.clear();
    m_tags.clear();
    m_patterns.clear();
    emit finished();
}

void Task::enter() const {
    if (parent()->connection())
        parent()->connection()->sendPresence(m_room, true);
}

void Task::exit() const {
    if (parent()->connection())
        parent()->connection()->sendPresence(m_room, false);
}

void Task::verify() const {
    if (parent()->connection())
        parent()->connection()->sendVerify(m_room);
}

void Task::commitOffer(const QString &topic, const QString& comment, const QList<Member*>& selected) const {
    if (!parent()->connection())
        return;
    QMap<QString, Offer::FilterType> filter = m_filter;
    foreach(Member* expert, selected) {
        filter[expert->id()] = Offer::TFT_ACCEPT;
    }

    Offer offer(m_offer->client(),
                m_offer->roomJid(),
                topic,
                m_offer->m_urgency,
                m_offer->local(),
                m_offer->images(),
                filter,
                m_offer->location(),
                m_offer->m_started,
                m_tags,
                m_patterns,
                comment,
                answer()->textContent());
    parent()->connection()->sendOffer(offer);
}

void Task::clearFilter() {
    m_filter.clear();
    emit filterChanged();
}

void Task::filter(Member* member, int role) {
    if (role < 0 || role >= Offer::TFT_LAST)
        m_filter.remove(member->id());
    else
        m_filter[member->id()] = (Offer::FilterType)role;
    emit filterChanged();
}

QString Task::id() const  {
    return m_room;
}

Task::Task(Offer* offer, League* parent):
    QObject(parent),
    m_room(offer->room()), m_offer(offer), m_comment(offer->comment())
{
    QObject::connect(offer, SIGNAL(cancelled()), this, SLOT(cancelReceived()));
    setFilter(offer->filter());
    m_answer = doSearch::instance()->document("Ваш ответ", League::instance()->self(), true, id() + "-" + "answer");
    m_patterns = offer->patterns();
    m_tags = offer->tags();
    if (!m_offer->draft().isEmpty() && answer()->textContent().isEmpty()) {
        answer()->setTextContent(m_offer->draft());
    }
    setFilter(offer->filter());
}

Task::Task(const QString& roomId, League* parent): QObject(parent), m_room(roomId), m_offer(0) {
    m_answer = doSearch::instance()->document("Ваш ответ", League::instance()->self(), true, id() + "-" + "answer");
}


bool Bubble::incoming() const {
    return m_from != doSearch::instance()->league()->id();
}
}

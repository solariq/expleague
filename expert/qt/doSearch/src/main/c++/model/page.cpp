#include "page.h"

#include "../dosearch.h"
#include "../league.h"
#include "../util/filethrottle.h"
#include "../util/mmath.h"
#include "../ir/dictionary.h"

#include <QHash>
#include <QStack>
#include <QUrl>

#include <QQuickItem>
#include <QQmlComponent>
#include <QQmlContext>
#include <QQmlApplicationEngine>

#include <QFontMetrics>

#include <algorithm>

static QFontMetrics titleFontMetrics(QFont("Helvetica", 10));

namespace expleague {

QVariant PageModel::toVariant(const QString& pageId) const {
    QHash<QString, QVariant> result;
    result["id"] = pageId;
    result["freq"] = freq;
    result["when"] = (int)when;
    return QVariant(result);
}

PageModel PageModel::fromVariant(const QVariant& var) {
    PageModel result;
    if (var.canConvert(QVariant::Hash)) {
        QHash<QString, QVariant> hash = var.toHash();
        result.freq = hash["freq"].toInt();
        result.when = hash["when"].toInt();
    }
    else {
        result.freq = 0;
        result.when = 0;
    }
    return result;
}

QList<Page*> Page::incoming() const {
    QList<Page*> keys = m_incoming.keys();
    std::sort(keys.begin(), keys.end(), [this](Page* left, Page* right) {
        return pIn(left) > pIn(right);
    });
    return keys;
}

QList<Page*> Page::outgoing() const {
    QList<Page*> keys = m_outgoing.keys();
    std::sort(keys.begin(), keys.end(), [this](Page* left, Page* right) {
        return pOut(left) > pOut(right);
    });
    return keys;
}

void Page::forgetIncoming(Page* page) {
    remove("incoming", [page](const QVariant& var){ return page->id() == var.toString(); });
    m_incoming.remove(page);
}

void Page::forgetOutgoing(Page* page) {
    remove("outgoing", [page](const QVariant& var){ return page->id() == var.toString(); });
    m_outgoing.remove(page);
}

QHash<QUrl, QQmlComponent*> componentsCache;

QQuickItem* Page::ui(bool cache) const {
    if (cache && m_ui)
        return m_ui;
    if (!m_context) {
        m_context = new QQmlContext(rootEngine, (QObject*)this);
        m_context->setContextProperty("owner", const_cast<Page*>(this));
    }

    QQmlComponent* component = componentsCache[m_ui_url];
    if (!component) {
        component = new QQmlComponent(rootEngine, QUrl(m_ui_url));
        if (component->isError()) {
            qWarning() << "Error on component load. Context: " << rootEngine->rootContext() << ". doSearch: " << rootEngine->rootContext()->contextProperty("dosearch");
            foreach(QQmlError error, component->errors()) {
                qWarning() << error;
            }
        }
        componentsCache[m_ui_url] = component;
    }
    QQuickItem* result = (QQuickItem*)component->create(m_context);
    if (cache) {
        m_ui = result;
        //    m_ui->setParent(const_cast<Page*>(this));
        connect(m_ui, &QQuickItem::destroyed, [this](){
            m_ui = 0;
            emit uiChanged();
        });
    }
    initUI(result);
    return result;
}

bool Page::transferUI(Page* other) const {
    if (!m_ui || !m_context || other->m_ui) // have no ui or other have alreagy got one
        return false;
    other->m_context = m_context;
    other->m_ui = m_ui;
    QObject::disconnect(m_ui, 0, this, 0);
    m_ui->setParentItem(0);
//    m_ui->setParent(other);
    m_context->setContextProperty("owner", other);
    connect(m_ui, &QQuickItem::destroyed, [other](){
        other->m_ui = 0;
        emit other->uiChanged();
    });
    m_ui = 0;
    m_context = 0;
    emit other->uiChanged();
    emit uiChanged();
    return true;
}

double Page::pOut(Page* page) const {
    QHash<Page*, PageModel>::const_iterator ptr = m_outgoing.find(page);
    PageModel model;
    if (ptr != m_outgoing.end())
        model = ptr.value();
    double pTime = 0; { // transition to the page, based on page open time
        const time_t now = time(0);
        const double deltaFromCurrent = now - model.when;
        for (int t = 1; t < 60; t++) { // kind of integral ;)
            pTime += erlang(2, 1.0/30, deltaFromCurrent + t);
        }
    }
    double pFreq = 0; { // transition based on how often the page is visited from the current
        const int c = m_outgoing.size();
        const double dpLambda = optimalExpansionDP(m_out_total, c);
        if (dpLambda < 1000)
            pFreq = m_out_total/(m_out_total + dpLambda) * (model.freq + 1)/(double)(m_out_total + c) ;
    }
    return pTime * 0.8 + pFreq * 0.2;
}

double Page::pIn(Page*) const {
    return 0;
}

void Page::transition(Page* page, TransitionType type) {
    if (page == this)
        return;
    switch(type) {
    case TransitionType::SELECT_TAB:
    case TransitionType::FOLLOW_LINK:
    case TransitionType::REDIRECT:
    case TransitionType::TYPEIN:
    case TransitionType::CHILD_GROUP_OPEN: {
        PageModel& data = m_outgoing[page];
        data.freq++;
        data.when = time(0);
        m_out_total++;
        replaceOrAppend("outgoing", data.toVariant(page->id()), [](const QVariant& lhs, const QVariant& rhs){
            return lhs.toHash().value("id") == rhs.toHash().value("id");
        });

        m_last_visited = page;
        store("lastVisited", page->id());
        break;
    }
    case TransitionType::CHANGED_SCREEN:
        break;
    }

    page->incomingTransition(this, type);
    save();
}

void Page::incomingTransition(Page* page, TransitionType type) {
    switch(type) {
    case TransitionType::TYPEIN:
        time(&m_last_visit_ts);
        store("ts", qlonglong(m_last_visit_ts));
    case TransitionType::CHILD_GROUP_OPEN:
    case TransitionType::REDIRECT:
    case TransitionType::FOLLOW_LINK:
    {
        PageModel& data = m_incoming[page];
        data.freq++;
        data.when = time(0);
        m_in_total++;
        replaceOrAppend("incoming", data.toVariant(page ? page->id() : ""), [](const QVariant& lhs, const QVariant& rhs){
            return lhs.toHash().value("id") == rhs.toHash().value("id");
        });
        break;
    }
    case TransitionType::SELECT_TAB:
        time(&m_last_visit_ts);
        store("ts", qlonglong(m_last_visit_ts));
        break;
    case TransitionType::CHANGED_SCREEN:
        m_last_visited = 0;
        store("lastVisited", QVariant());
        break;
    }
    save();
}

double Page::titleWidth() const {
    QString title = this->title();
    return title.isEmpty() ? 200 : titleFontMetrics.boundingRect(title).width();
}

doSearch* Page::parent() const {
    return static_cast<doSearch*>(QObject::parent());
}

QList<Page*> Page::children(const QString& prefixOrig) const {
    QString prefix = prefixOrig;
    prefix.replace(".", "/");
    QDir storage(parent()->pageResource(id()) + "/" + prefix);
    QList<Page*> result;
    foreach(QFileInfo info, storage.entryInfoList()) {
        if (info.isDir() && !info.fileName().startsWith(".") && QFile(info.absoluteFilePath() + "/page.xml").exists())
            result.append(parent()->page(id() + "/" + prefix + "/" + info.fileName()));
    }

    QString start = storage.dirName();
    prefix = prefix.section("/", 0, -2);
    storage.cdUp();
    foreach(QFileInfo info, storage.entryInfoList()) {
        if (info.isDir() && info.fileName().startsWith(start) && QFile(info.absoluteFilePath() + "/page.xml").exists())
            result.append(parent()->page(id() + "/" + prefix + "/" + info.fileName()));
    }
    return result;
}

void Page::visitChildren(const QString& fullKey, std::function<void (Page* value)> visitor) const {
    foreach(Page* page, children(fullKey)) {
        visitor(page);
    }
}

QDir Page::storage() const {
    return QDir(parent()->pageResource(id()));
}

Page::Page(const QString& id, const QString& ui, doSearch* parent): QObject(parent), PersistentPropertyHolder(parent->pageResource(id) + "/page.xml"),
    m_id(id), m_ui_url(ui), m_in_total(0), m_out_total(0)
{
    m_last_visit_ts = value("ts").toInt();

//    qDebug() << id << " restored: " << m_properties;
}

void Page::interconnect() {
    visitKeys("incoming", [this](const QVariant& value) {
        PageModel model(PageModel::fromVariant(value));
        QString pageId = value.toHash().value("id").toString();
        m_incoming[parent()->page(pageId)] = model;
        m_in_total += model.freq;
    });
    visitKeys("outgoing", [this](const QVariant& value) {
        PageModel model(PageModel::fromVariant(value));
        QString pageId = value.toHash().value("id").toString();
        m_outgoing[parent()->page(pageId)] = model;
        m_out_total += model.freq;
    });
    QVariant lastVisitedVar = value("lastVisited");
    m_last_visited = lastVisitedVar.isNull() ? 0 : parent()->page(lastVisitedVar.toString());
    if (m_last_visited == this)
        m_last_visited = 0;
}

void ContentPage::setTextContent(const QString& content) {
    auto compositeParent = qobject_cast<CompositeContentPage*>(parentPage());
    if (compositeParent)
        compositeParent->appendPart(this);
    FileWriteThrottle::enqueue(storage().absoluteFilePath("content.txt"), content, [this, content]() {
        BoW profile = BoW::fromPlainText(content, parent()->dictionary());
        setProfile(profile);
        FileWriteThrottle::enqueue(storage().absoluteFilePath("profile.txt"), profile.toString());
        this->textContentChanged();
    });
}

QString ContentPage::textContent() const {
    QFile file(storage().absoluteFilePath("content.txt"));
    if (!file.exists())
        return QString(QString::null);
    file.open(QFile::ReadOnly);
    return QString(file.readAll());
}

void ContentPage::processTextContentWhenAvailable(std::function<void (const QString &)> callback) const {
    QString text = textContent();
    if (!text.isNull())
        callback(text);
    else
        connect(this, &ContentPage::textContentChanged, [this, callback](){
           callback(textContent());
           disconnect(const_cast<ContentPage*>(this), &ContentPage::textContentChanged, const_cast<ContentPage*>(this), 0);
        });
}

void ContentPage::processProfileWhenAvailable(std::function<void (const BoW&)> callback) const {
    QString text = textContent();
    if (!text.isNull())
        callback(m_profile);
    else
        connect(this, &ContentPage::changingProfile, [this, callback](const BoW&, const BoW& newOne){
           callback(newOne);
           disconnect(const_cast<ContentPage*>(this), &ContentPage::changingProfile, const_cast<ContentPage*>(this), 0);
        });
}

void ContentPage::setProfile(const BoW& profile) {
    emit changingProfile(m_profile, profile);
    if (!qobject_cast<CompositeContentPage*>(this)) {
        profile.terms()->updateProfile(m_profile, profile);
    }
    m_profile = profile;
    FileWriteThrottle::enqueue(storage().absoluteFilePath("profile.txt"), profile.toString());
}

ContentPage::ContentPage(const QString& id, const QString& uiQml, doSearch* parent): Page(id, uiQml, parent) {
    QFile profileFile(storage().absoluteFilePath("profile.txt"));
    if (profileFile.exists()) {
        profileFile.open(QFile::ReadOnly);
        m_profile = BoW::fromString(QString::fromUtf8(profileFile.readAll()), parent->dictionary());
    }
}

bool CompositeContentPage::appendPart(ContentPage* part) {
    if (!part || m_parts.contains(part) || part == this)
        return false;
    m_parts.append(part);
    setProfile(profile() + part->profile());
    append("content.part", part->id());
    connect(part, SIGNAL(textContentChanged()), SLOT(onPartContentsChanged()));
    connect(part, SIGNAL(changingProfile(BoW,BoW)), SLOT(onPartProfileChanged(BoW,BoW)));
    save();
    emit partAppended(part);
    return true;
}

void CompositeContentPage::removePart(ContentPage* part) {
    if (!m_parts.contains(part))
        return;
    m_parts.removeOne(part);
    remove("content.part", [part](const QVariant& var){ return var.toString() == part->id(); });
    part->forgetIncoming(this);
    part->disconnect(this);
    forgetOutgoing(part);
    save();
    setProfile(updateSumComponent(profile(), part->profile(), BoW()));
    emit textContentChanged();
    emit partRemoved(part);
}

void CompositeContentPage::setTextContent(const QString&) {
    qWarning() << "Content of composite page must not be set directly!";
}

QString CompositeContentPage::textContent() const {
    QString result;

    foreach(ContentPage* page, m_parts) {
        result += page->textContent();
    }
    return result;
}

void CompositeContentPage::onPartProfileChanged(const BoW &oldOne, const BoW &newOne) {
    setProfile(updateSumComponent(profile(), oldOne, newOne));
}

CompositeContentPage::CompositeContentPage(const QString& id, const QString& uiQml, doSearch* parent): ContentPage(id, uiQml, parent)
{}

void CompositeContentPage::interconnect() {
    visitKeys("content.part", [this](const QVariant& var){
        ContentPage* part = qobject_cast<ContentPage*>(parent()->page(var.toString()));
        if (m_parts.contains(part))
            return;
        m_parts.append(part);
        connect(part, SIGNAL(textContentChanged()), SLOT(onPartContentsChanged()));
        connect(part, SIGNAL(changingProfile(BoW,BoW)), SLOT(onPartProfileChanged(BoW,BoW)));
        emit partAppended(part);
    });
    ContentPage::interconnect();
}
}

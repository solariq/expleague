#include "page.h"

#include "../dosearch.h"
#include "../league.h"
#include "../util/filethrottle.h"

#include <QHash>
#include <QStack>
#include <QUrl>

#include <QQuickItem>
#include <QQmlComponent>
#include <QQmlContext>
#include <QQmlApplicationEngine>

#include <QXmlStreamWriter>
#include <QXmlStreamReader>

#include <QFontMetrics>

#include <math.h>
#include <time.h>

#include <algorithm>

static QFontMetrics titleFontMetrics(QFont("Helvetica [Cronyx]", 12));

QVariantHash buildVariantByXml(QXmlStreamReader& reader);
void writeXml(const QString& local, const QString& ns, const QVariant& variant, QXmlStreamWriter* writer, bool attribute = false, bool enforceTag = false);
double bisection(double left, double right, std::function<double (double)> func);
double optimalExpansionDP(double statPower, int classes);
double erlang(int k, double lambda, double x);

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

QVariant* Page::resolve(const QStringList& path, bool create) {
    QVariant* current = &m_properties;
    int index = 0;
    while (index < path.size() - 1) {
        const QString key = path[index];
        QVariantHash& hash = *reinterpret_cast<QVariantHash*>(current->data());
        QVariant* next = &hash[key];
        if (!next->canConvert(QVariant::Hash)) {
            if (!create) {
//                qDebug() << "Unresolved context: " + path.mid(0, index + 1).join(".") << " current: " << *next;
                return 0;
            }
            if (!next->isNull())
                qWarning() << "Expected composite element but found " << next->type() << " at [" << path.mid(0, index + 1).join(".") << "]. Rewriting with composite, previous value: " << next;

            next->setValue(QVariantHash());
        }
        current = next;
        index++;
    }
    return current;
}

QVariant Page::value(const QString& fullKey) const {
    QStringList path = fullKey.split(".");
    QVariant* context = const_cast<Page*>(this)->resolve(path);
    if (!context)
        return QVariant();
    return context->toHash().value(path.last());
}

void Page::store(const QString& fullKey, const QVariant& value) {
    QStringList path = fullKey.split(".");
//    qDebug() << "Store: " << fullKey << " value: " << value << " last: " << path.last();
    QVariant* context = resolve(path, true);
    QVariantHash& hash = *reinterpret_cast<QVariantHash*>(context->data());

//    qDebug() << " context: : " << *context;
    if (!value.isNull()) {
        QVariant& current = hash[path.last()];
        if (current == value) {
//            qDebug() << " Found the same value";
            return;
        }
        current.setValue(value);
    }
    else {
        if (!hash.contains(path.last())) {
//            qDebug() << " Already clear key";
            return;
        }
        hash.remove(path.last());
    }
//    qDebug() << " Successfully set to " << context->toHash()[path.last()];
    m_changes++;
}

void Page::visitAll(const QString& fullKey, std::function<void (const QVariant& value)> visitor) const {
    QVariant value = this->value(fullKey);
    if (value.canConvert(QVariant::List)) {
        foreach(const QVariant& value, value.toList()) {
            visitor(value);
        }
    }
    else if (!value.isNull()) {
        visitor(value);
    }
}

void Page::append(const QString& fullKey, const QVariant& value) {
    QStringList path = fullKey.split(".");
    QVariant* context = resolve(path, true);
    QVariantHash& hash = *reinterpret_cast<QVariantHash*>(context->data());
    QVariant& current = hash[path.last()];
    if (current.type() == QVariant::List) { // replace existing list
        QVariantList lst = current.toList();
        lst += value;
        current.setValue(lst);
    }
    else if (!current.isNull()) { // convert current value to the list
        QVariantList lst;
        lst += current;
        lst += value;
        current.setValue(lst);
    }
    else { // fill new key
        current.setValue(value);
    }
    m_changes++;
}

void Page::remove(const QString& key) {
    QStringList path = key.split(".");
    QVariant* context = resolve(path, true);
    QVariantHash& hash = *reinterpret_cast<QVariantHash*>(context->data());
    hash.remove(path.last());
}

void Page::replaceOrAppend(const QString& fullKey, const QVariant& value, std::function<bool (const QVariant& lhs, const QVariant& rhs)> equals) {
    QStringList path = fullKey.split(".");
    QVariant* context = resolve(path, true);
    QVariantHash& hash = *reinterpret_cast<QVariantHash*>(context->data());
    QVariant& current = hash[path.last()];
    if (current.type() == QVariant::List) { // replace existing list
        QVariantList lst = current.toList();
        int index = 0;
        while (index < lst.size()) {
            if (equals(lst[index], value)) {
                lst.replace(index, value);
                break;
            }
            index++;
        }
        if (index >= lst.size())
            lst += value;
        current.setValue(lst);
    }
    else if (!current.isNull() && !equals(current, value)) { // convert current value to the list
        QVariantList lst;
        lst += current;
        lst += value;
        current.setValue(lst);
    }
    else { // fill new key
        current.setValue(value);
    }
    m_changes++;
}


int Page::count(const QString& fullKey) const {
    QVariant value = this->value(fullKey);
    if (value.canConvert(QVariant::List)) {
        return value.toList().size();
    }
    else if (!value.isNull()) {
        return 1;
    }
    else {
        return 0;
    }
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

QHash<QUrl, QQmlComponent*> componentsCache;

QQuickItem* Page::ui() const {
    if (m_ui)
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
    m_ui = (QQuickItem*)component->create(m_context);
//    m_ui->setParent(const_cast<Page*>(this));
    connect(m_ui, &QQuickItem::destroyed, [this](){
        m_ui = 0;
    });
    initUI(m_ui);
    m_ui->setVisible(false);
    return m_ui;
}

void Page::transferUI(Page* other) const {
    if (!m_ui || !m_context)
        return;
    other->m_context = m_context;
    other->m_ui = m_ui;
    m_ui->disconnect();
//    m_ui->setParent(other);
    m_context->setContextProperty("owner", other);
    connect(m_ui, &QQuickItem::destroyed, [other](){
        other->m_ui = 0;
    });
    m_ui = 0;
    m_context = 0;
}

double Page::pOut(Page* page) const {
    QHash<Page*, PageModel>::const_iterator ptr = m_outgoing.find(page);
    PageModel model;
    if (ptr != m_outgoing.end())
        model = ptr.value();
    const int c = m_outgoing.size();
    const double dpLambda = optimalExpansionDP(m_out_total, c);
    const time_t now = time(0);
    const double deltaFromCurrent = now - model.when;
    double a = erlang(2, 1.0/60, deltaFromCurrent);
    double b = erlang(2, 1.0/60, deltaFromCurrent + 30);
    double result = ((a * 30) + (b -a) * 30 /2) * 0.5; // two visits of the same page is set to 1 minute, here is probability of visit in next 30 seconds
    const double deltaReturn = now - page->lastVisitTs();
    result += erlang(2, 1.0/10/60, deltaReturn) * 0.01;
    if (dpLambda < 1000)
        result += m_out_total/(m_out_total + dpLambda) * (model.freq + 1)/(double)(m_out_total + c) * 0.5;
    return result;
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
        if (m_state == State::ACTIVE)
            setState(State::INACTIVE);
    }

    page->incomingTransition(this, type);
    save();
}

void Page::incomingTransition(Page* page, TransitionType type) {
    if (state() == Page::CLOSED)
        setState(Page::INACTIVE);
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
        setState(State::ACTIVE);
        m_last_visited = 0;
        store("lastVisited", QVariant());
        break;
    }
    save();
}

void Page::setState(Page::State state) {
    if (this->state() == state)
        return;
    m_state = state;
    switch(state) {
    case ACTIVE:
        store("state", "active");
        break;
    case INACTIVE:
        store("state", "inactive");
        break;
    case CLOSED:
        store("state", "closed");
        break;
    }
    save();
    emit stateChanged(state);
}

double Page::titleWidth() const {
    QString title = this->title();
    return title.isEmpty() ? 200 : titleFontMetrics.boundingRect(title).width();
}

doSearch* Page::parent() const {
    return static_cast<doSearch*>(QObject::parent());
}

void Page::save() const {
    if (m_saved_changes == m_changes)
        return;
    QByteArray buffer;
    QXmlStreamWriter writer(&buffer);
    writer.writeStartDocument();
    QVariant copy = m_properties;
    m_saved_changes = m_changes;
    writeXml("page", "http://expleague.com/expert/page", copy, &writer);
    writer.writeEndDocument();
//    qDebug() << buffer;
    FileWriteThrottle::enqueue(storage().absoluteFilePath("page.xml"), buffer);
}

QDir Page::storage() const {
    return QDir(parent()->pageResource(id()));
}

Page::Page(const QString& id, const QString& ui, doSearch* parent): QObject(parent),
    m_id(id), m_ui_url(ui), m_in_total(0), m_out_total(0)
{
    QDir dir(parent->pageResource(id));
    QFile file(dir.filePath("page.xml"));
    if (!file.exists())
        return;
    if (!file.open(QFile::ReadOnly)) {
        qWarning() << "Unable to read page properties: " << dir.filePath("page.xml");
        return;
    }
    QXmlStreamReader reader(&file);
    m_properties = buildVariantByXml(reader)["page"];
    QVariant var = value("state");
    m_state = INACTIVE;
    if (!var.isNull()) {
        QString value = var.toString();
        if (value == "inactive")
            m_state = INACTIVE;
        else if (value == "closed")
            m_state = CLOSED;
        else if (value == "active")
            m_state = ACTIVE;
        else
            qWarning() << "Unable to parse page status: " << value << " using INACTIVE";
    }
    m_last_visit_ts = value("ts").toInt();

//    qDebug() << id << " restored: " << m_properties;
}

void Page::interconnect() {
    visitAll("incoming", [this](const QVariant& value) {
        PageModel model(PageModel::fromVariant(value));
        QString pageId = value.toHash().value("id").toString();
        m_incoming[parent()->page(pageId)] = model;
        m_in_total += model.freq;
    });
    visitAll("outgoing", [this](const QVariant& value) {
        PageModel model(PageModel::fromVariant(value));
        QString pageId = value.toHash().value("id").toString();
        m_outgoing[parent()->page(pageId)] = model;
        m_out_total += model.freq;
    });
    QVariant lastVisitedVar = value("lastVisited");
    m_last_visited = lastVisitedVar.isNull() ? 0 : parent()->page(lastVisitedVar.toString());
    if (m_last_visited == this)
        m_last_visited = 0;
    m_saved_changes = m_changes;
}
}

bool attributeString(const QString& str) {
    return str.length() < 50 && !str.contains('\n');
}

void writeXml(const QString& local, const QString& ns, const QVariant& variant, QXmlStreamWriter* writer, bool attribute, bool enforceTag) {
    if (attribute) {
        switch(variant.type()) {
        case QVariant::Type::Double:
            writer->writeAttribute(local, QString::number(variant.toDouble()));
            break;
        case QVariant::Type::Time:
        case QVariant::Type::LongLong:
        case QVariant::Type::Int:
            writer->writeAttribute(local, QString::number(variant.toLongLong()));
            break;
        case QVariant::Type::Url:
        case QVariant::Type::String:
            if (local != "type" && local != "name" && attributeString(variant.toString()))
                writer->writeAttribute(local, variant.toString());
            break;
        }
    }
    else if (variant.canConvert(QVariant::Hash)) {
        static QRegExp illegalChars("[<>\\\\/\\.\\?&\\+!]");
        if (local.length() > 10 || local.contains(illegalChars)) {
            writer->writeStartElement("item");
            writer->writeAttribute("name", local);
        }
        else
            writer->writeStartElement(local);
        if (!ns.isEmpty())
            writer->writeDefaultNamespace(ns);
        QHash<QString, QVariant> hash = variant.toHash();
        {
            QHash<QString, QVariant>::iterator iter = hash.begin();
            while (iter != hash.end()) {
                writeXml(iter.key(), "", iter.value(), writer, true, false);
                iter++;
            }
        }
        {
            QHash<QString, QVariant>::iterator iter = hash.begin();
            while (iter != hash.end()) {
                writeXml(iter.key(), "", iter.value(), writer, false, false);
                iter++;
            }
        }
        writer->writeEndElement();
    }
    else if (variant.canConvert(QVariant::List)) {
        QVariantList lst = variant.toList();
        foreach (const QVariant& var, lst) {
            writeXml(local, ns, var, writer, false, true);
        }
    }
    else if (local == "type" || local == "name" || (variant.canConvert(QVariant::String) && (enforceTag || !attributeString(variant.toString())))) {
        writer->writeStartElement(local);
        writer->writeAttribute("type", "text");
        writer->writeCharacters(variant.toString());
        writer->writeEndElement();
    }
}

template <typename T>
void appendVariant(QVariant& to, const T& value) {
    if (to.canConvert(QVariant::Type::List)) {
        QVariantList& lst = *reinterpret_cast<QVariantList*>(to.data());
        lst.append(value);
    }
    else if (!to.isNull()) {
        QVariantList lst;
        lst += to;
        lst += value;
        to.setValue(lst);
    }
    else to.setValue(value);
}

QVariantHash buildVariantByXml(QXmlStreamReader& reader) {
    QString key;
    QString type;
    QVariantHash result;

//    qDebug() << "XML reader enter ";
    while (!reader.atEnd() && !reader.hasError()) {
        reader.readNext();
        if (reader.isStartElement()) {
            key = reader.name().toString();
            QVariantHash fold;
            QXmlStreamAttributes attrs = reader.attributes();
            if (attrs.hasAttribute("name"))
                key = attrs.value("name").toString();
            if (!attrs.hasAttribute("type")) {
//                qDebug() << "Start element: " << reader.name();

                type = "hash";
                for (int i = 0; i < attrs.size(); i++) {
                    const QXmlStreamAttribute& attr = attrs[i];
                    QString local = attr.name().toString();
                    if (local == "name")
                        continue;
                    bool ok;
                    QVariant value = attr.value().toInt(&ok);
                    if (!ok)
                        value = attr.value().toDouble(&ok);
                    if (!ok)
                        value = attr.value().toString();
                    fold[local] = value;
                }
                if (!reader.isEndElement())
                    fold.unite(buildVariantByXml(reader));
                if (!reader.isEndElement())
                    qWarning() << "Invalid xml";
                appendVariant<QVariantHash>(result[key], fold);
            }
            else type = attrs.value("type").toString();
        }
        else if (reader.isCharacters() && type == "text") {
            appendVariant<QString>(result[key], reader.text().toString());
            reader.readNext();
        }
        else if (reader.isEndElement()) {
//            qDebug() << "End element: " << reader.name();
            break;
        }
    }
//    qDebug() << "XML reader exit";

    return result;
}



const double EPSILON = 1e-6;

double bisection(double left, double right, std::function<double (double)> func) {
    const double fLeft = func(left);
    if (fabs(fLeft) < EPSILON)
        return left;
    const double fRight = func(right);
    if (fabs(fRight) < EPSILON)
        return right;

    if (fLeft * fRight > 0) {
        qWarning() << "Function values for left and right parameters should lay on different sides of 0";
        return nan("");
    }

    const double middle = (left + right) / 2.;
    const double fMiddle = func(middle);
    if (fLeft * fMiddle > 0)
        return bisection(middle, right, func);
    else
        return bisection(left, middle, func);
}

double optimalExpansionDP(double statPower, int classes) {
    if (statPower <= classes)
        return std::numeric_limits<double>::infinity();
    return bisection(0, 2 * classes, [statPower, classes](double x) {
        return x == 0.0 ? -classes : x * log(1 + statPower / x) - classes;
    });
}

double erlang(int k, double lambda, double x) {
    const double nom = exp(log(lambda)* k + log(x) * (k - 1) - lambda * x - lgamma(k));
    return nom;
}

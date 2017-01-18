#include "pholder.h"

#include "../util/filethrottle.h"

#include <QHash>
#include <QStack>
#include <QUrl>
#include <QFile>

#include <QVariant>

#include <QXmlStreamWriter>
#include <QXmlStreamReader>

#include <time.h>

#include <algorithm>

#include <QDebug>

QVariantHash buildVariantByXml(QXmlStreamReader& reader);
void writeXml(const QString& local, const QString& ns, const QVariant& variant, QXmlStreamWriter* writer, bool attribute = false, bool enforceTag = false);

QVariant* PersistentPropertyHolder::resolve(const QStringList& path, bool create) {
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

QVariant PersistentPropertyHolder::value(const QString& fullKey) const {
    QStringList path = fullKey.split(".");
    QVariant* context = const_cast<PersistentPropertyHolder*>(this)->resolve(path);
    if (!context)
        return QVariant();
    return context->toHash().value(path.last());
}

void PersistentPropertyHolder::store(const QString& fullKey, const QVariant& value) {
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

void PersistentPropertyHolder::visitKeys(const QString& fullKey, std::function<void (const QVariant& value)> visitor) const {
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

void PersistentPropertyHolder::append(const QString& fullKey, const QVariant& value) {
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

void PersistentPropertyHolder::remove(const QString& key) {
    QStringList path = key.split(".");
    QVariant* context = resolve(path, true);
    QVariantHash& hash = *reinterpret_cast<QVariantHash*>(context->data());
    hash.remove(path.last());
    m_changes++;
}

void PersistentPropertyHolder::remove(const QString& key, std::function<bool (const QVariant& value)> filter) {
    QStringList path = key.split(".");
    QVariant* context = resolve(path, true);
    QVariantHash& hash = *reinterpret_cast<QVariantHash*>(context->data());
    QVariant val = hash.value(path.last());
    if (filter(val))
        hash.remove(path.last());
    else if (val.type() == QVariant::List) {
        QVariantList lst = val.toList();
        for (auto it = lst.begin(); it < lst.end(); it++) {
            if (filter(*it))
                it = lst.erase(it);
        }
        if (lst.size() > 1)
            hash[path.last()] = lst;
        else
            hash[path.last()] = lst[0];
    }
    m_changes++;
}

void PersistentPropertyHolder::replaceOrAppend(const QString& fullKey, const QVariant& value, std::function<bool (const QVariant& lhs, const QVariant& rhs)> equals) {
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


int PersistentPropertyHolder::count(const QString& fullKey) const {
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

void PersistentPropertyHolder::save() const {
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
    FileWriteThrottle::enqueue(m_file, buffer);
}

PersistentPropertyHolder::PersistentPropertyHolder(const QString& fname):
    m_file(fname)
{
    QFile file(fname);
    if (!file.exists())
        return;
    if (!file.open(QFile::ReadOnly)) {
        qWarning() << "Unable to read page properties: " << fname;
        return;
    }
    QXmlStreamReader reader(&file);
    m_properties = buildVariantByXml(reader)["page"];
    m_saved_changes = m_changes;
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

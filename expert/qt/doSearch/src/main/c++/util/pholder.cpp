#include "pholder.h"

#include "../util/filethrottle.h"
#include "leveldb/db.h"


#include <QHash>
#include <QStack>
#include <QUrl>
#include <QFile>
#include <QDebug>
#include <QVariant>

#include <QXmlStreamWriter>
#include <QXmlStreamReader>

#include <time.h>

#include <algorithm>

#include <QDebug>

const char devideListChar = '\\';
const char devideHashChar = '>';

QString toString(const QVariant& var);
QVariant fromString(const QString& s);

LevelDBContainer& PersistentPropertyHolder::getLevelDBCotainer(){
    static LevelDBContainer container("pages");
    return container;
}


QVariant PersistentPropertyHolder::value(const QString& key) const {
    leveldb::DB* db = getLevelDBCotainer().get();
    leveldb::ReadOptions options;
    std::string value;
    QByteArray fullKey = (m_page + "." + key).toUtf8();

    leveldb::Status status = db->Get(options, leveldb::Slice(fullKey.data(), fullKey.length()), &value);
    if(status.ok()){
        return fromString(QString::fromStdString(value));
    }
    return QVariant();
}

void PersistentPropertyHolder::store(const QString& key, const QVariant& value) {
    QByteArray fullKey = (m_page + "." + key).toUtf8();

    leveldb::Slice addr(fullKey.data(), fullKey.length());
    if (!value.isNull()) {
        batch.Put(addr, toString(value).toStdString());
    }
    else {
        batch.Delete(addr);
    }
    m_changes++;
}

void PersistentPropertyHolder::visitKeys(const QString& key, std::function<void (const QVariant& value)> visitor) const {
    QVariant value = this->value(key);
    if (value.canConvert(QVariant::List)) {
        foreach(const QVariant& val, value.toList()) {
            //qDebug() << val << " visited";
            visitor(val);
        }
        //qDebug() << "end of visit";
    }
    else if (!value.isNull()) {
        visitor(value);
    }
}

void PersistentPropertyHolder::append(const QString& key, const QVariant& value) {
    leveldb::DB* db = getLevelDBCotainer().get();
    QByteArray fullKey = (m_page + "." + key).toUtf8();
    leveldb::Slice addr(fullKey.data(), fullKey.length());

    std::string prev;
    leveldb::Status status = db->Get(leveldb::ReadOptions(), addr, &prev);
    if(prev.length() != 0){
        prev += devideListChar;
    }
    prev.append(toString(value).toStdString());
    batch.Put(addr, prev);
    m_changes++;
}

void PersistentPropertyHolder::remove(const QString& key) {
    QByteArray fullKey = (m_page + "." + key).toUtf8();
    leveldb::Slice addr(fullKey.data(), fullKey.length());
    batch.Delete(addr);
    m_changes++;
}

void PersistentPropertyHolder::remove(const QString& key, std::function<bool (const QVariant& value)> filter) {
    leveldb::DB* db = getLevelDBCotainer().get();
    QByteArray fullKey = (m_page + "." + key).toUtf8();
    leveldb::Slice addr(fullKey.data(), fullKey.length());
    std::string value;
    db->Get(leveldb::ReadOptions(), leveldb::Slice(fullKey.data(), fullKey.length()), &value);
    QVariant val = fromString(QString::fromStdString(value));

    if (filter(val))
        batch.Delete(addr);
    else if (val.type() == QVariant::List) {
        QVariantList lst = val.toList();
        for (auto it = lst.begin(); it < lst.end(); it++) {
            if (filter(*it))
                it = lst.erase(it);
        }
        if (lst.size() > 1){
            batch.Put(addr, toString(QVariant(lst)).toStdString());
        }else if(lst.size() == 1){
            batch.Put(addr, toString(QVariant(lst.first())).toStdString());
        }else{
            batch.Delete(addr);
        }
    }
    m_changes++;
}

void PersistentPropertyHolder::replaceOrAppend(const QString& fullKey, const QVariant& val, std::function<bool (const QVariant& lhs, const QVariant& rhs)> equals) {
    QVariant current = value(fullKey);
    if (current.type() == QVariant::List) { // replace existing list
        QVariantList lst = current.toList();
        int index = 0;
        while (index < lst.size()) {
            if (equals(lst[index], val)) {
                lst.replace(index, val);
                break;
            }
            index++;
        }
        if (index >= lst.size())
            lst += val;
        current.setValue(lst);
    }
    else if (!current.isNull() && !equals(current, val)) { // convert current value to the list
        QVariantList lst;
        lst += current;
        lst += val;
        current.setValue(lst);
    }
    else { // fill new key
        current.setValue(val);
    }
    store(fullKey, current);
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
    leveldb::DB* db = getLevelDBCotainer().get();
    db->Write(leveldb::WriteOptions(), &batch);
    batch.Clear();
}

PersistentPropertyHolder::PersistentPropertyHolder(const QString& pname):
    m_page(pname), batch(){
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

QString toString(const QVariant& var){
    QString s;
    if(var.canConvert(QVariant::String)){
        return var.toString();
    }else if(var.canConvert(QVariant::StringList)){
        QStringList list = var.toStringList();
        for(int i = 0; i < list.size() - 1; i++){
            s.append(list[i]);
            s.append(devideListChar);
        }
        s.append(list.last());
        return s;
    }
    QVariantHash hash = var.toHash();
    auto i = hash.begin();
    QString key = i.key();
    QVariant val = i.value();
    assert(val.canConvert(QVariant::String));
    s += key + devideHashChar + toString(val);
    ++i;
    for(; i !=hash.end(); ++i){
        QString key = i.key();
        QVariant val = i.value();
        assert(val.canConvert(QVariant::String));
        s += devideHashChar + key + devideHashChar + toString(val);
    }
    return s;
}

QVariant convert(const QString& s){
    bool ok = false;
    QVariant value;
    value = s.toInt(&ok);
    if(!ok){
        value = s.toDouble(&ok);
    }
    if(!ok){
        value = s;
    }
    return value;
}

QVariant convertHash(const QString& s){
    QStringList strlist = s.split(devideHashChar);
    if(strlist.size() == 1){
        return convert(strlist.at(0));
    }
    QVariantHash hash;
    for(int i = 0; i < strlist.size(); i+=2){
        hash.insert(strlist.at(i), convert(strlist.at(i + 1)));
    }
    return hash;
}

QVariant fromString(const QString& s){
    QStringList strlist = s.split(devideListChar);
    if(strlist.size() == 1){
        return convertHash(strlist.at(0));
    }
    QVariantList varlist;
    for(auto i: strlist){
        varlist.append(convertHash(i));
    }
    return varlist;
}

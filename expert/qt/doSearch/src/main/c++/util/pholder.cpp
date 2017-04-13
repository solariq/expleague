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
#include <memory>

#include <QDebug>

QString toString(const QVariant& var);
QVariant fromString(const QString& s);
int commonPrefixSize(const QStringList &a, const QStringList& b);

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

LevelDBContainer& PersistentPropertyHolder::getLevelDBCotainer(){
    static LevelDBContainer container("pages");
    return container;
}

void PersistentPropertyHolder::put(const QString& key, const QVariant& val){
    if(val.canConvert(QVariant::String)){
        QByteArray qdata = val.toString().toUtf8();
        QByteArray qkey = key.toUtf8();
        leveldb::Slice addr(qkey.data(), qkey.length());
        leveldb::Slice data(qdata.data(), qdata.length());
        batch.Put(addr, data);
    }else if(val.canConvert(QVariant::List)){
        QVariantList list = val.toList();
        for(int i = 0; i < list.size(); i++){
            put(key + "." + QString::number(i), list[i]);
        }
    }else if(val.canConvert(QVariant::Hash)){
        QVariantHash hash = val.toHash();
        for(auto i = hash.begin(); i != hash.end(); ++i){
            put(key + "." + i.key(), i.value());
        }
    }else{
        qDebug() << "Warning unknown type in leveldb";
    }
}

QVariant makeComplexVariant(const QStringList& path, int start, const QVariant& var){
    if(start == path.length()){
        return var;
    }
    bool ok = false;
    path[start].toInt(&ok);
    if(ok){
        QVariantList list;
        list.append(makeComplexVariant(path, start + 1 , var));
        return list;
    }
    QVariantHash hash;
    hash.insert(path[start], makeComplexVariant(path, start +1 , var));
    return hash;
}

QString nextKey(const QString& root, const QString& fullKey){
    int left = root.size();
    int right = left + 1;
    for(;right < fullKey.length() && fullKey[right] != '.'; right++);
    return fullKey.mid(left + 1, right - left - 1);
}

QVariant PersistentPropertyHolder::get(const QString& key) const {
    leveldb::DB* db = getLevelDBCotainer().get();
    QByteArray fullKey = key.toUtf8();
    leveldb::Slice slicekey(fullKey.data(), fullKey.length());
    QStringList prevPath;
    std::unique_ptr<leveldb::Iterator> iter(db->NewIterator(leveldb::ReadOptions()));
    QVariant root;
    for(iter->Seek(slicekey); iter->Valid() && iter->key().starts_with(slicekey); iter->Next()){
        QVariant* current = &root;
        QString data =  QString::fromUtf8(QByteArray(iter->value().data(), iter->value().size()));
        QByteArray qkey(iter->key().data(), iter->key().size());
        QString spath = QString::fromUtf8(qkey).mid(fullKey.size() + 1);
        if(spath.size() == 0){
            return convert(data);
        }
        QStringList path = spath.split(".");
        int prefixSize = commonPrefixSize(path, prevPath);
        for(int i = 0; i <= path.length(); i++){
            if(current->isNull()){
                *current = makeComplexVariant(path, i, data);
                break;
            }
            if(current->type() == QVariant::List){
                QVariantList& list = *reinterpret_cast<QVariantList*>(current->data());
                if(prefixSize <= i){
                    list.append(QVariant());
                }
                current = &list.last();
            }
            else if(current->type() == QVariant::Hash){
                QVariantHash& hash = *reinterpret_cast<QVariantHash*>(current->data());
                if(!hash.contains(path[i])){
                    hash.insert(path[i], QVariant());
                }
                current = &hash[path[i]];
            }
        }
        prevPath = std::move(path);
    }
    return root;
}

QVariant PersistentPropertyHolder::value(const QString& key) const {
    return get(m_page + "." + key);
}

void PersistentPropertyHolder::store(const QString& key, const QVariant& value) {
    if (!value.isNull()) {
        put(m_page + "." + key, value);
    }
    m_changes++;
}


void PersistentPropertyHolder::visitKeys(const QString& key, std::function<void (const QString& str)> visitor) const {
    leveldb::DB* db = getLevelDBCotainer().get();
    QByteArray fullKey = (m_page + "." + key).toUtf8();
    leveldb::Slice addr(fullKey.data(), fullKey.length());
    QString prev;
    std::unique_ptr<leveldb::Iterator> iter(db->NewIterator(leveldb::ReadOptions()));
    for(iter->Seek(addr); iter->Valid() && iter->key().starts_with(addr); iter->Next()){
        QString str = QString::fromUtf8(QByteArray(iter->key().data(), iter->key().size()));
        QString sub = nextKey(fullKey, str);
        if(sub != prev){
            visitor(sub);
        }
        prev = std::move(sub);
    }
}

void PersistentPropertyHolder::visitValues(const QString& key, std::function<void (const QVariant& val)> visitor) const {
    leveldb::DB* db = getLevelDBCotainer().get();
    QByteArray fullKey = (m_page + "." + key).toUtf8();
    leveldb::Slice addr(fullKey.data(), fullKey.length());
    QString prev;
    std::unique_ptr<leveldb::Iterator> iter(db->NewIterator(leveldb::ReadOptions()));
    for(iter->Seek(addr); iter->Valid() && iter->key().starts_with(addr); iter->Next()){
        QString str = QString::fromUtf8(QByteArray(iter->key().data(), iter->key().size()));
        QString sub = nextKey(fullKey, str);
        if(sub != prev){
            visitor(get(fullKey + "." + sub));
        }
        prev = std::move(sub);
    }
}

void PersistentPropertyHolder::ap(const QString& key, const QVariant& value) {
    leveldb::DB* db = getLevelDBCotainer().get();
    QByteArray fullKey = key.toUtf8();
    leveldb::Slice addr(fullKey.data(), fullKey.length());
    std::unique_ptr<leveldb::Iterator> iter(db->NewIterator(leveldb::ReadOptions()));
    int last = -1;
    for(iter->Seek(addr); iter->Valid() && iter->key().starts_with(addr); iter->Next()){
        bool ok = false;
        QString str = QString::fromUtf8(QByteArray(iter->key().data(), iter->key().size()));
        QString sub = nextKey(fullKey, str);
        last = sub.toInt(&ok);
        assert(ok || sub == ""); //Several types
    }
    put(fullKey + "." + QString::number(last + 1), value);
}

void PersistentPropertyHolder::append(const QString& key, const QVariant& value) {
    ap(m_page + "." + key, value);
    m_changes++;
}

void PersistentPropertyHolder::remove(const QString& key) {
    leveldb::DB* db = getLevelDBCotainer().get();
    QByteArray fullKey = (m_page + "." + key).toUtf8();
    leveldb::Slice addr(fullKey.data(), fullKey.length());
    std::unique_ptr<leveldb::Iterator> iter(db->NewIterator(leveldb::ReadOptions()));
    for(iter->Seek(addr); iter->Valid() && iter->key().starts_with(addr); iter->Next()){
        batch.Delete(iter->key());
    }
    save();
    m_changes++;
}

void PersistentPropertyHolder::remove(const QString& key, std::function<bool (const QVariant& value)> filter) {
    leveldb::DB* db = getLevelDBCotainer().get();
    QByteArray fullKey = (m_page + "." + key).toUtf8();
    leveldb::Slice addr(fullKey.data(), fullKey.length());
    std::unique_ptr<leveldb::Iterator> iter(db->NewIterator(leveldb::ReadOptions()));
    for(iter->Seek(addr); iter->Valid() && iter->key().starts_with(addr); iter->Next()){
        if(filter(QString::fromUtf8(QByteArray(iter->value().data(), iter->value().size())))){
            batch.Delete(iter->key());
        }
    }
    save();
    m_changes++;
}

void PersistentPropertyHolder::replaceOrAppend(const QString& key, const QVariant& val, std::function<bool (const QVariant& lhs, const QVariant& rhs)> equals) {
    leveldb::DB* db = getLevelDBCotainer().get();
    QByteArray fullKey = (m_page + "." + key).toUtf8();
    leveldb::Slice addr(fullKey.data(), fullKey.length());

    std::unique_ptr<leveldb::Iterator> iter(db->NewIterator(leveldb::ReadOptions()));
    for(iter->Seek(addr); iter->Valid() && iter->key().starts_with(addr); iter->Next()){
        QString str = QString::fromUtf8(QByteArray(iter->key().data(), iter->key().size()));
        QString sub = nextKey(fullKey, str);
        if(sub == ""){
            put(fullKey + ".0", val);
            return;
        }
        if(equals(get(str), val)){
            remove(str);
            put(str, val);
            save();
            return;
        }
    }
    ap(fullKey, val);
    save();
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
    m_page(pname){
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

int commonPrefixSize(const QStringList &a, const QStringList& b){
    int i = 0;
    for(i = 0; i < qMin(a.size(), b.size()); i++){
        if(a[i] != b[i]){
            return i;
        }
    }
    return i;
}


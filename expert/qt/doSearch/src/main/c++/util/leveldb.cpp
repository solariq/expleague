#include "leveldb.h"
#include "leveldb/db.h"

#include <QStandardPaths>
#include <QDebug>
#include <QTextCodec>
#include <memory>
#include <QDir>

LevelDBContainer::LevelDBContainer(): m_db(nullptr){
}

LevelDBContainer::LevelDBContainer(const QString& path, leveldb::Options options){
    options.create_if_missing = true;
    std::string cpath;
#ifdef Q_OS_WIN32
    QTextCodec* codec = QTextCodec::codecForName("System");
    assert(codec);
    cpath = codec->fromUnicode(QStandardPaths::writableLocation(QStandardPaths::AppLocalDataLocation) + "/" + path);
#else
    cpath = (QStandardPaths::writableLocation(QStandardPaths::AppLocalDataLocation) + "/" + path).toStdString();
#endif
    leveldb::DB* db;
    leveldb::Status status = leveldb::DB::Open(options, cpath, &db);
    m_db.reset(db);
    assert(status.ok());
}

leveldb::DB* LevelDBContainer::operator->(){
    return m_db.get();
}

leveldb::DB* LevelDBContainer::get(){
    return m_db.get();
}

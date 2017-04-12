#include "leveldb.h"
#include "leveldb/db.h"

#include <QStandardPaths>
#include <QDebug>

LevelDBContainer::LevelDBContainer() : LevelDBContainer("test"){
}

LevelDBContainer::LevelDBContainer(const QString& path){
    leveldb::Options options;
    options.create_if_missing = true;
    leveldb::Status status = leveldb::DB::Open(options, (QStandardPaths::writableLocation(QStandardPaths::AppLocalDataLocation) + "/" + path).toStdString(), &db);
    assert(status.ok());
}

leveldb::DB* LevelDBContainer::get(){
    return db;
}

LevelDBContainer::~LevelDBContainer(){
    delete db;
}

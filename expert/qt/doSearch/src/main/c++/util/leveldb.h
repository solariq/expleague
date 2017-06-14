#ifndef LEVELDB_H
#define LEVELDB_H

#include "leveldb/db.h"

#include <QString>
#include <memory>

class LevelDBContainer
{
public:
    LevelDBContainer();
    LevelDBContainer(const QString& path, leveldb::Options options = leveldb::Options());
    leveldb::DB* get();
    leveldb::DB* operator->();
private:
    std::unique_ptr<leveldb::DB> m_db;
};

#endif // LEVELDB_H

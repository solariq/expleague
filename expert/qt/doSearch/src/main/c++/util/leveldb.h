#ifndef LEVELDB_H
#define LEVELDB_H

#include "leveldb/db.h"

#include <QString>

class LevelDBContainer
{
public:
    LevelDBContainer();
    LevelDBContainer(const QString& path);
    leveldb::DB* get();
    ~LevelDBContainer();
private:
    LevelDBContainer operator=(LevelDBContainer d);
    LevelDBContainer(const LevelDBContainer& d);
    leveldb::DB* db;
};

#endif // LEVELDB_H

TARGET = leveldb
TEMPLATE = lib

CONFIG += staticlib
CONFIG -= qt

QMAKE_CXXFLAGS += -DLEVELDB_PLATFORM_WINDOWS -DLEVELDB_ATOMIC_PRESENT -DNDEBUG

INCLUDEPATH += ./include ./port/win

LIBS += -lshlwapi

HEADERS += \
    db/builder.h \
    db/db_impl.h \
    db/db_iter.h \
    db/dbformat.h \
    db/filename.h \
    db/log_format.h \
    db/log_reader.h \
    db/log_writer.h \
    db/memtable.h \
    db/skiplist.h \
    db/snapshot.h \
    db/table_cache.h \
    db/version_edit.h \
    db/version_set.h \
    db/write_batch_internal.h \
    helpers/memenv/memenv.h \
#    include/leveldb/c.h \
    include/leveldb/cache.h \
    include/leveldb/comparator.h \
    include/leveldb/db.h \
    include/leveldb/env.h \
    include/leveldb/filter_policy.h \
    include/leveldb/iterator.h \
    include/leveldb/options.h \
    include/leveldb/slice.h \
    include/leveldb/status.h \
    include/leveldb/table.h \
    include/leveldb/table_builder.h \
    include/leveldb/write_batch.h \
#    LevelDB/resource.h \
#    port/win/unistd.h \
    port/atomic_pointer.h \
    port/port.h \
#    port/port_example.h \
#    port/port_posix.h \
    port/port_win.h \
    port/thread_annotations.h \
    table/block.h \
    table/block_builder.h \
    table/filter_block.h \
    table/format.h \
    table/iterator_wrapper.h \
    table/merger.h \
    table/two_level_iterator.h \
    util/arena.h \
    util/coding.h \
    util/crc32c.h \
    util/hash.h \
    util/histogram.h \
    util/logging.h \
    util/mutexlock.h \
    util/posix_logger.h \
    util/random.h \

SOURCES += \
#    db/autocompact_test.cc \
    db/builder.cc \
#    db/c.cc \
#    db/corruption_test.cc \
#    db/db_bench.cc \
    db/db_impl.cc \
    db/db_iter.cc \
    db/dbformat.cc \
    db/filename.cc \
    db/leveldb_main.cc \
    db/log_reader.cc \
    db/log_writer.cc \
    db/memtable.cc \
    db/repair.cc \
    db/table_cache.cc \
    db/version_edit.cc \
#    db/version_edit_test.cc \
    db/version_set.cc \
#    db/version_set_test.cc \
    db/write_batch.cc \
#    db/write_batch_test.cc \
    helpers/memenv/memenv.cc \
#    helpers/memenv/memenv_test.cc \
    LevelDB/dllmain.cpp \
#    util/crc32c_win.cc \
#    port/port_posix.cc \
    port/port_win.cc \
    table/block.cc \
    table/block_builder.cc \
    table/filter_block.cc \
#    table/filter_block_test.cc \
    table/format.cc \
    table/iterator.cc \
    table/merger.cc \
    table/table.cc \
    table/table_builder.cc \
    table/two_level_iterator.cc \
    util/arena.cc \
    util/bloom.cc \
    util/cache.cc \
    util/coding.cc \
    util/comparator.cc \
#    util/crc32c.cc \
    util/env.cc \
    util/env_win.cc \
    util/filter_policy.cc \
    util/hash.cc \
    util/histogram.cc \
    util/logging.cc \
    util/options.cc \
    util/status.cc \
    util/crc32c_win.cc

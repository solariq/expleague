set(leveldb_sources
  ./db/builder.cc
#  ./db/c.cc
  #    ./db/db_bench.cc \
  ./db/db_impl.cc
  ./db/db_iter.cc
  ./db/dbformat.cc
  ./db/dumpfile.cc
  ./db/filename.cc
#  ./db/leveldbutil.cc
  ./db/log_reader.cc
  ./db/log_writer.cc
  ./db/memtable.cc
  ./db/repair.cc
  ./db/table_cache.cc
  ./db/version_edit.cc
  ./db/version_set.cc
  ./db/write_batch.cc
  ./helpers/memenv/memenv.cc
  ./port/port_win.cc
#  ./port/port_posix.cc
  ./table/block.cc
  ./table/block_builder.cc
  ./table/filter_block.cc
  ./table/format.cc
  ./table/iterator.cc
  ./table/merger.cc
  ./table/table.cc
  ./table/table_builder.cc
  ./table/two_level_iterator.cc
  ./util/arena.cc
  ./util/bloom.cc
  ./util/cache.cc
  ./util/coding.cc
  ./util/comparator.cc
  ./util/crc32c.cc
  ./util/env.cc
  ./util/env_win.cc
#  ./util/env_posix.cc
  ./util/filter_policy.cc
  ./util/hash.cc
  ./util/histogram.cc
  ./util/logging.cc
  ./util/options.cc
  ./util/status.cc)

add_library(leveldb STATIC ${leveldb_sources})
add_definitions(-DLEVELDB_PLATFORM_WINDOWS -DLEVELDB_ATOMIC_PRESENT -DNDEBUG)

find_library(SHLWAPI REQUIRED Shlwapi.lib)
target_link_libraries(leveldb Shlwapi.lib)
target_include_directories(leveldb PUBLIC ./ include)

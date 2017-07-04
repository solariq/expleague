find_package(Qt5 REQUIRED COMPONENTS Core)

set(hunspell_sources
        datalocation.cpp
        spellchecker.cpp
        src/src/hunspell/affentry.cxx
        src/src/hunspell/affixmgr.cxx
        src/src/hunspell/csutil.cxx
        #    src/src/hunspell/dictmgr.cxx
        src/src/hunspell/filemgr.cxx
        src/src/hunspell/hashmgr.cxx
        src/src/hunspell/hunspell.cxx
        src/src/hunspell/hunzip.cxx
        src/src/hunspell/phonet.cxx
        src/src/hunspell/replist.cxx
        src/src/hunspell/suggestmgr.cxx)

#CONFIG += static
#CONFIG += precompile_header warn_off
#if(MSVC)
#    set(CMAKE_CXX_FLAGS_DEBUG "/DWIN32 /D_WINDOWS /EHsc /wd4355 /wd4251 /wd4250 /wd4996")
#endif()

file(WRITE config.h "/* Version number of package */\n#define VERSION \"${VERSION}\"")
include_directories(src/src/hunspell)

add_library(hunspell ${hunspell_sources})
target_compile_definitions(hunspell PRIVATE -DHUNSPELL_STATIC)
target_link_libraries(hunspell
  Qt5::Core
)

TEMPLATE = lib

QT += core

TARGET = hunspell

CONFIG += staticlib

HEADERS += \
    spellchecker.h \
    datalocation.h

SOURCES += \
    datalocation.cpp \
    spellchecker.cpp

DEFINES += HUNSPELL_STATIC
#CONFIG += static
#CONFIG += precompile_header warn_off
CONFIG(debug, debug|release) {
    DEFINES += HUNSPELL_WARNING_ON
}

config_file = "/* Version number of package */" "$${LITERAL_HASH}define VERSION \"$$VERSION\""
write_file($$PWD/config.h, config_file)

INCLUDEPATH += \
    ./ \
    src/src/hunspell

SOURCES += \
    src/src/hunspell/affentry.cxx \
    src/src/hunspell/affixmgr.cxx \
    src/src/hunspell/csutil.cxx \
#    src/src/hunspell/dictmgr.cxx \
    src/src/hunspell/filemgr.cxx \
    src/src/hunspell/hashmgr.cxx \
    src/src/hunspell/hunspell.cxx \
    src/src/hunspell/hunzip.cxx \
    src/src/hunspell/phonet.cxx \
    src/src/hunspell/replist.cxx \
    src/src/hunspell/suggestmgr.cxx

HEADERS += \
#    config.h \
    src/src/hunspell/affentry.hxx \
    src/src/hunspell/affixmgr.hxx \
    src/src/hunspell/atypes.hxx \
    src/src/hunspell/baseaffix.hxx \
    src/src/hunspell/csutil.hxx \
#    src/src/hunspell/dictmgr.hxx \
    src/src/hunspell/filemgr.hxx \
    src/src/hunspell/hashmgr.hxx \
    src/src/hunspell/htypes.hxx \
    src/src/hunspell/hunspell.h \
    src/src/hunspell/hunspell.hxx \
    src/src/hunspell/hunvisapi.h \
    src/src/hunspell/hunzip.hxx \
    src/src/hunspell/langnum.hxx \
    src/src/hunspell/phonet.hxx \
    src/src/hunspell/replist.hxx \
    src/src/hunspell/suggestmgr.hxx \
    src/src/hunspell/w_char.hxx

OTHER_FILES +=\
    src/hunspell/license.hunspell \
    src/hunspell/license.myspell \
    src/hunspell/utf_info.cxx


win32 {
    SOURCES +=
}

macx {
    SOURCES +=
}

unix {
    SOURCES +=
}

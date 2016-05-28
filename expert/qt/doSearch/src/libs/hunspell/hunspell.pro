TEMPLATE = lib

QT += widgets core

TARGET = hunspell

CONFIG += staticlib

HEADERS += \
    spellchecker.h \
    datalocation.h \
    dictionary.h

SOURCES += \
    dictionary.cpp \
    datalocation.cpp \
    spellchecker.cpp

win32 {
    SOURCES += \
        spellchecker_win.cpp
}

macx {
    SOURCES += \
        spellchecker_macx.cpp
}

unix {
    SOURCES += \
        spellchecker_unix.cpp
}

#-------------------------------------------------
#
# Project created by QtCreator 2016-03-29T13:58:49
#
#-------------------------------------------------

CONFIG += ordered
CONFIG += cef

TARGET = doSearch

TEMPLATE = subdirs

QXMPP_NO_EXAMPLES = true
QXMPP_NO_TESTS = true
QXMPP_LIBRARY_TYPE = staticlib

SUBDIRS = src/libs/qxmpp \
          src/libs/discount \
          src/libs/peg-markdown-highlight \
          src/libs/hunspell \
          src/libs/cutemarked \
          src/libs/breakpad \

win32: SUBDIRS += src/libs/leveldb-win
else: SUBDIRS += src/libs/leveldb

cef {
    SUBDIRS += src/libs/cef-exec
    macx: SUBDIRS += src/libs/cef-mac
}

SUBDIRS += src/main


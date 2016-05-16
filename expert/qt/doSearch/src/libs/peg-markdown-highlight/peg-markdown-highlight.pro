#
# PEG Markdown Highlight Adapter Static Libary Project for CuteMarkEd
#
# Github : https://github.com/cloose/CuteMarkEd
#

QT += core gui widgets

TEMPLATE = lib

TARGET = pmh

CONFIG += staticlib

SOURCES += \
    pmhmarkdownparser.cpp \
    styleparser.cpp \
    pmh_parser.cpp \
    pmh_styleparser.cpp

HEADERS  += \
    pmhmarkdownparser.h \
    styleparser.h \
    definitions.h \
    pmh_styleparser.h \
    pmh_parser.h \
    pmh_definitions.h

###################################################################################################
## DEPENDENCIES
###################################################################################################

#
# peg-markdown-highlight
#

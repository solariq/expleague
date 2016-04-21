#-------------------------------------------------
#
# Project created by QtCreator 2016-03-29T13:58:49
#
#-------------------------------------------------

greaterThan(QT_MAJOR_VERSION, 4): QT += widgets

VERSION = 0.1

QT       += core network concurrent gui quick webview xml webenginewidgets

TARGET = doSearch
TEMPLATE = app

SOURCES += src/main/c++/main.cpp \
    src/main/c++/protocol.cpp \
    src/main/c++/profile.cpp \
    src/main/c++/model/model.cpp \
    src/main/c++/model/web.cpp

HEADERS += \
    src/main/c++/protocol.h \
    src/main/c++/profile.h \
    src/main/c++/task.h \
    src/main/c++/expleague.h \
    src/main/c++/dosearch.h \
    src/main/c++/model/context.h \
    src/main/c++/model/folder.h \
    src/main/c++/model/screen.h \
    src/main/c++/model/web/webfolder.h \
    src/main/c++/model/web/webscreen.h \
    src/main/c++/model/settings.h \
    src/main/c++/model/web/websearch.h

RESOURCES += ./src/main/resources/qml/qml.qrc \
             ./src/main/resources/images/images.qrc

DISTFILES += ./src/main/resources/qml/*.qml
DISTFILES += ./src/main/resources/qml/*.js
DISTFILES += ./src/main/resources/images/*.png
DISTFILES += ./src/main/resources/images/status/*.png

win32:CONFIG(release, debug|release): LIBS += -L$$OUT_PWD/../qxmpp/src/release/ -lqxmpp_d
else:win32:CONFIG(debug, debug|release): LIBS += -L$$OUT_PWD/../qxmpp/src/debug/ -lqxmpp_d
else:unix: LIBS += -L$$OUT_PWD/../qxmpp/src/ -lqxmpp_d

INCLUDEPATH += $$PWD/../qxmpp/src/client $$PWD/../qxmpp/src/base \
DEPENDPATH += $$PWD/../qxmpp/src \

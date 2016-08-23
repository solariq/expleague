#-------------------------------------------------
#
# Project created by QtCreator 2016-03-29T13:58:49
#
#-------------------------------------------------

# # Win
# cd C:\Users\solar\Documents\GitHub\expleague\expert\qt\build-doSearch-Desktop_Qt_5_6_0_MSVC2015_64bit-Release\src\main\release
# mkdir doSearch
# c:\Qt\5.6\msvc2015_64\bin\windeployqt.exe --release --pdb --qmldir ..\..\..\..\doSearch\src\main\resources\qml\ --dir .\doSearch -geoservices .\doSearch.exe
# cp /Volumes/C/Windows//System32/ssleay32.dll ./doSearch/
# cp /Volumes/C/Windows//System32/libeay32.dll ./doSearch/
# copy .\doSearch.exe .\doSearch\
# zip -r ./doSearch-0.1.zip ./doSearch

# # Mac
# mkdir -p ./doSearch.app/Contents/PlugIns/geoservices
# cp ~/Qt/5.6/clang_64/plugins/geoservices/libqtgeoservices_osm.dylib ./doSearch.app/Contents/PlugIns/geoservices/
# ~/Qt/5.6/clang_64/bin/macdeployqt ./doSearch.app -verbose=1 -qmldir=/Users/solar/tree/tbts/expert/qt/doSearch/src/main/resources/qml/
# install_name_tool -rpath /Users/solar/Qt/5.6/clang_64/lib/ @executable_path/../Frameworks ./doSearch.app/Contents/MacOS/doSearch
# mkdir temp
# mv ./doSearch.app/ ./temp/
# hdiutil create -volname doSearch -srcfolder ./temp/ -ov -format UDZO doSearch.dmg
VERSION = 0.4.6

QT += widgets core network location concurrent positioning gui quick quickcontrols2 webengine webenginewidgets xml multimedia webenginecore
QT_PRIVATE += webengine-private
qml.path += resources/qml
target.path += ../../bin

TARGET = doSearch
TEMPLATE = app

ICON = resources/doSearch.icns
RC_ICONS = resources/doSearch.ico
QMAKE_TARGET_BUNDLE_PREFIX=com.expleague

QMAKE_CXXFLAGS += -DQXMPP_STATIC

macx: CONFIG += static objective_c
else:win32: CONFIG += static console

macx:QMAKE_RPATHDIR += /Users/solar/Qt/5.6/clang_64/lib/

SOURCES += \
    c++/main.cpp \
    c++/protocol.cpp \
    c++/profile.cpp \
    c++/model/web.cpp \
    c++/league.cpp \
    c++/model/history.cpp \
    c++/util/util.cpp \
    c++/model/page.cpp \
    c++/model/manager.cpp \
    c++/model/search.cpp \
    c++/model/group.cpp \
    c++/model/context.cpp \
    c++/editor.cpp \
    c++/dosearch.cpp

HEADERS += \
    c++/protocol.h \
    c++/profile.h \
    c++/task.h \
    c++/expleague.h \
    c++/dosearch.h \
    c++/model/context.h \
    c++/model/settings.h \
    c++/league.h \
    c++/model/editor.h \
    c++/model/history.h \
    c++/util/filethrottle.h \
    c++/util/call_once.h \
    c++/util/simplelistmodel.h \
    c++/model/page.h \
    c++/model/manager.h \
    c++/model/search.h \
    c++/model/group.h \
    c++/model/web.h \
    c++/util/math.h

macx: OBJECTIVE_SOURCES += \
    objc/ExpLeagueNotification.mm

macx: OBJECTIVE_HEADERS += \
    objc/ExpLeagueNotification.h

RESOURCES += resources/qml/qml.qrc \
             resources/images/images.qrc \
             resources/misc.qrc

DISTFILES += resources/doSearch.ico \
    config.xml \
    package.xml
DISTFILES += resources/doSearch.icns

win32:CONFIG(release, debug|release): LIBS += \
#    -L$$OUT_PWD/../libs/qxmpp/src/ -lqxmpp \
    $$OUT_PWD/../libs/qxmpp/src/qxmpp.lib \
    -L$$OUT_PWD/../libs/discount/release/ -ldiscount \
    -L$$OUT_PWD/../libs/hunspell/release/ -lhunspell \
    -L$$OUT_PWD/../libs/cutemarked/release/ -lcutemarked
else:win32:CONFIG(debug, debug|release): LIBS += \
    -L$$OUT_PWD/../libs/qxmpp/src/ -lqxmpp_d \
    -L$$OUT_PWD/../libs/discount/debug/ -ldiscount \
    -L$$OUT_PWD/../libs/hunspell/debug/ -lhunspell \
    -L$$OUT_PWD/../libs/cutemarked/debug/ -lcutemarked
else:unix:CONFIG(debug, debug|release): LIBS += \
    -L$$OUT_PWD/../libs/qxmpp/src/ -lqxmpp_d \
    -L$$OUT_PWD/../libs/discount/ -ldiscount \
    -L$$OUT_PWD/../libs/hunspell/ -lhunspell \
    -L$$OUT_PWD/../libs/cutemarked/ -lcutemarked
else:unix:CONFIG(release, debug|release): LIBS += \
    -L$$OUT_PWD/../libs/qxmpp/src/ -lqxmpp \
    -L$$OUT_PWD/../libs/discount/ -ldiscount \
    -L$$OUT_PWD/../libs/hunspell/ -lhunspell \
    -L$$OUT_PWD/../libs/cutemarked/ -lcutemarked


win32:CONFIG(release, debug|release): LIBS += -L$$OUT_PWD/../libs/peg-markdown-highlight/release/ -lpmh
else:win32:CONFIG(debug, debug|release): LIBS += -L$$OUT_PWD/../libs/peg-markdown-highlight/debug/ -lpmh
else:unix: LIBS += -L$$OUT_PWD/../libs/peg-markdown-highlight/ -lpmh

macx:LIBS += -framework AppKit

INCLUDEPATH += \
    $$PWD/../libs/qxmpp/src/client $$PWD/../libs/qxmpp/src/base \
    $$PWD/../libs/discount \
    $$PWD/../libs/peg-markdown-highlight \
    $$PWD/../libs/hunspell \
    $$PWD/../libs/cutemarked \
    /Users/solar/Qt/5.7/Src/qtwebengine/src

DEPENDPATH += \
    $$PWD/../libs/qxmpp/src \
    $$PWD/../libs/discount \
    $$PWD/../libs/hunspell \
    $$PWD/../libs/peg-markdown-highlight \
    $$PWD/../libs/cutemarked

unix:PRE_TARGETDEPS += $$OUT_PWD/../libs/cutemarked/libcutemarked.a
else:win32:CONFIG(debug, debug|release) PRE_TARGETDEPS += $$OUT_PWD/../libs/cutemarked/debug/cutemarked.lib
else:win32:CONFIG(release, debug|release) PRE_TARGETDEPS += $$OUT_PWD/../libs/cutemarked/release/cutemarked.lib

INSTALLS = target qml


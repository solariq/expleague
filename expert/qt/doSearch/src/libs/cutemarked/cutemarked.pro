TEMPLATE = lib

TARGET = cutemarked

CONFIG += staticlib

SOURCES += \
    markdownhighlighter.cpp

HEADERS += \
    markdownhighlighter.h

win32:CONFIG(release, debug|release): LIBS += -L$$OUT_PWD/../peg-markdown-highlight/release/ -lpmh
else:win32:CONFIG(debug, debug|release): LIBS += -L$$OUT_PWD/../peg-markdown-highlight/debug/ -lpmh
else:unix: LIBS += -L$$OUT_PWD/../peg-markdown-highlight/ -lpmh

INCLUDEPATH += $$PWD/../peg-markdown-highlight
DEPENDPATH += $$PWD/../peg-markdown-highlight

win32:CONFIG(release, debug|release): LIBS += -L$$OUT_PWD/../hunspell/release/ -lhunspell
else:win32:CONFIG(debug, debug|release): LIBS += -L$$OUT_PWD/../hunspell/debug/ -lhunspell
else:unix: LIBS += -L$$OUT_PWD/../hunspell/ -lhunspell

INCLUDEPATH += $$PWD/../hunspell
DEPENDPATH += $$PWD/../hunspell

unix:!macx {
  PKGCONFIG += hunspell
}

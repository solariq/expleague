TEMPLATE = app
CONFIG += static console c++11
CONFIG -= app_bundle
CONFIG -= qt

SOURCES += main.cpp

INCLUDEPATH += \
    $$PWD/../libs/cef_win32

win32:CONFIG(release, debug|release): LIBS += \
    -L$$OUT_PWD/../libs/cef_win32/release -llibcef \
#    -L$$OUT_PWD/../libs/cef_win32/release -lcef_sandbox \
    -L$$OUT_PWD/../libs/cef_win32/release -llibcef_dll_wrapper
else:win32:CONFIG(debug, debug|release): LIBS += \
    -L$$OUT_PWD/../libs/cef_win32/debug -llibcef \
#    -L$$OUT_PWD/../libs/cef_win32/debug -lcef_sandbox \
    -L$$OUT_PWD/../libs/cef_win32/debug -llibcef_dll_wrapper

#!/bin/sh
DYLD_FRAMEWORK_PATH=/usr/local/Cellar/qt5/5.6.0/lib${DYLD_FRAMEWORK_PATH:+:$DYLD_FRAMEWORK_PATH}
export DYLD_FRAMEWORK_PATH
QT_PLUGIN_PATH=/usr/local/Cellar/qt5/5.6.0/plugins${QT_PLUGIN_PATH:+:$QT_PLUGIN_PATH}
export QT_PLUGIN_PATH
exec "$@"

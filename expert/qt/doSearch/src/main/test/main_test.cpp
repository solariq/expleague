#include <QString>
#include <QSystemTrayIcon>
#include <QtTest>
#include <QQmlApplicationEngine>

#include "util/pholder_test.h"


#ifndef Q_OS_MAC
QSystemTrayIcon* trayIcon;
#endif

QQmlApplicationEngine* rootEngine;

int main(){
    PersistentPropertyHolderTest p;
    QTest::qExec(&p);
}

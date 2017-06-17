#include <QString>
#include <QtTest>
#include <QDebug>

#include "util/pholder_test.h"

int main(){
    PersistentPropertyHolderTest p;
    QTest::qExec(&p);
}

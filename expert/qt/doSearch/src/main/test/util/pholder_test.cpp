#include <QVariantList>
#include <QList>

#include "c++/util/pholder.h"
#include "pholder_test.h"

void PersistentPropertyHolderTest::readWrite_data(){
    QTest::addColumn<QString>("page_name");
    QTest::addColumn<QString>("key");
    QTest::addColumn<QVariant>("data");
    QTest::newRow("string") << "some" << "page" << QVariant(QString("data"));
    QTest::newRow("int") << "some" << "page" << QVariant(25);
    QTest::newRow("double") << "some" << "page" << QVariant(2.17);
    QTest::newRow("list") << "some" << "page" << QVariant(QVariantList({"first", "second", "third"}));
    QTest::newRow("intlist") << "some" << "page" << QVariant(QList<QVariant>({1, 2, 3}));
    QTest::newRow("mixedlist") << "some" << "page" << QVariant(QList<QVariant>({1, "two", 3.0}));
    QVariant v = QVariantHash({{"one",1},{"two",2}});
    QTest::newRow("hash") << "some" << "page" << v;
}

void PersistentPropertyHolderTest::readWrite(){
    QVariant v = QVariantHash({{"one",1},{"two",2}});
    QStringList lst = v.toStringList();
    QFETCH(QString, page_name);
    QFETCH(QString, key);
    QFETCH(QVariant, data);
    PersistentPropertyHolder h(page_name);
    h.store(key, data );
    h.save();
    QCOMPARE(data , h.value(key));
}

bool checkVisit = false;
void visit(const QVariant& value){
    if(value == QVariant(1)){
        checkVisit = true;
    }
}

void PersistentPropertyHolderTest::visitKeys(){
    PersistentPropertyHolder h("some");
    h.store("place", QVariant(1));
    h.save();
    h.visitValues("place", &visit);
    QCOMPARE(true, checkVisit);
}

void PersistentPropertyHolderTest::append(){
    PersistentPropertyHolder h("other");
    h.store("place.1", QVariantList({"data1", "data2"}));
    h.store("place.2", QVariant());
    h.save();
    h.append("place.1", QVariant(QVariantList{"data3", "data4"}));
    h.append("place.2", "data");
    h.save();
    QCOMPARE(QVariant(QVariantList({"data1", "data2", "data3", "data4"})), h.value("place.1"));
    QCOMPARE(QVariant("data"), h.value("place.2"));
}

void PersistentPropertyHolderTest::remove(){
    PersistentPropertyHolder h("some");
    h.store("place", "data");
    h.save();
    h.remove("place");
    h.save();
    QCOMPARE(QVariant(), h.value("place"));
}

bool isEvenNumber(QVariant var){
    return var.canConvert<int>() && var.toInt() % 2 == 0;
}

void PersistentPropertyHolderTest::removeFilter(){
    PersistentPropertyHolder h("some");
    h.store("numbers",QVariantList({1, 2, 3, 4, 5, 6}));
    h.store("number1", QVariant(1));
    h.store("number2", QVariant(2));
    h.store("nothing", QVariant());
    h.save();
    h.remove("numbers", &isEvenNumber);
    h.remove("number1", &isEvenNumber);
    h.remove("number2", &isEvenNumber);
    h.remove("nothing", &isEvenNumber);
    h.save();
    QCOMPARE(h.value("numbers"), QVariant(QVariantList({1, 3, 5})));
    QCOMPARE(h.value("number1"), QVariant(1));
    QCOMPARE(h.value("number2"), QVariant());
    QCOMPARE(h.value("nothing"), QVariant());
}

bool compareMod10(const QVariant& lhs, const QVariant& rhs){
    return !((lhs.toInt() - rhs.toInt())%10);
}

void PersistentPropertyHolderTest::replaceOrAppend(){
    PersistentPropertyHolder h("some");
    h.store("numbers", QVariantList({1, 2, 3, 4, 5, 6}));
    h.save();
    h.replaceOrAppend("numbers", QVariant(12), &compareMod10);
    h.save();
    QCOMPARE(h.value("numbers"), QVariant(QVariantList({1, 12, 3, 4, 5, 6})));
}

void PersistentPropertyHolderTest::count(){
    PersistentPropertyHolder h("some");
    h.store("numbers", QVariantList({1, 2, 3, 4, 5, 6}));
    h.save();
    QCOMPARE(h.count("numbers"), 6);
}


#include <QtTest>

class PersistentPropertyHolderTest : public QObject
{
    Q_OBJECT

private Q_SLOTS:
    void readWrite_data();
    void readWrite();
    void visitKeys();
    void visitValues();
    void append();
    void remove();
    void removeFilter();
    void replaceOrAppend();
    void count();
};



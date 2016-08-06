#ifndef HISTORY_H
#define HISTORY_H

#include <QObject>
#include <QSet>

class QSettings;
namespace expleague {
class doSearch;

class Context;
class Profile;

class StateSaver: public QObject {
    Q_OBJECT

public:
    void restoreState(doSearch* search);

public slots:
    void profileChanged(Profile*);
    void saveProfiles();

public:
    StateSaver(QObject* parent = 0);

private:
    QSettings* m_settings;
    QSet<QObject*> m_connected;
};
}

#endif // HISTORY_H

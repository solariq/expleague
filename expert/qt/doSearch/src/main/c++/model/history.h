#ifndef HISTORY_H
#define HISTORY_H

#include <QObject>
#include <QSet>
#include <QList>
#include <QQmlListProperty>

#include "page.h"

class QSettings;
namespace expleague {
class doSearch;

class Context;
class Profile;

class History: public Page {
    Q_OBJECT

    Q_PROPERTY(QQmlListProperty<expleague::Page> last30 READ last30 NOTIFY historyChanged)

public:
    QQmlListProperty<Page> last30() const { return QQmlListProperty<Page>(const_cast<History*>(this), const_cast<QList<Page*>&>(m_last30)); }

public:
    QList<Page*> story() const { return m_story; }
    QList<Page*> last(int depth) const;

    Q_INVOKABLE int visits(const QString& id) const;

public slots:
    void onVisited(Page* to);

signals:
    void historyChanged();

private:
    History(doSearch* parent): Page("history", "qrc:/HistoryPage.qml", "", parent) {}

    friend class doSearch;

protected:
    void interconnect();

private:
    QList<Page*> m_story;
    QList<Page*> m_last30;
};

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

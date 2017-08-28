#ifndef HISTORY_H
#define HISTORY_H

#include <QObject>
#include <QSet>
#include <QList>
#include <QQmlListProperty>

#include "page.h"
#include "context.h"

class QSettings;
namespace expleague {
class doSearch;
//class Context;
class Profile;
class PageVisit;

class History: public Page {
    Q_OBJECT

    Q_PROPERTY(QQmlListProperty<expleague::PageVisit> last30 READ last30 NOTIFY historyChanged)

public:
    QQmlListProperty<PageVisit> last30() const { return QQmlListProperty<PageVisit>(const_cast<History*>(this), const_cast<QList<PageVisit*>&>(m_last30)); }

public:
    QList<PageVisit*> story() const { return m_story; }
    QList<PageVisit*> last(int depth) const;
    PageVisit* last() const;

    Q_INVOKABLE int visits(const QString& id) const;
    Q_INVOKABLE Context* recent(Page* page) const;

    Page* current() const;

public slots:
    void onVisited(Page* to, Context* context);

signals:
    void historyChanged();

private:
    History(doSearch* parent): Page("history", "qrc:/HistoryPage.qml", parent) {}

    friend class doSearch;

protected:
    void interconnect() override;

private:
    QList<PageVisit*> m_story;
    int m_cursor;
    QList<PageVisit*> m_last30;
};

class PageVisit: public QObject {
    Q_OBJECT

    Q_PROPERTY(expleague::Page* page READ page CONSTANT)
    Q_PROPERTY(expleague::Context* context READ context CONSTANT)
    Q_PROPERTY(QString contextName READ contextName CONSTANT)
    Q_PROPERTY(time_t time READ time CONSTANT)

public:
    Page* page() const { return m_page; }
    Context* context() const { return m_context; }
    QString contextName() const { return m_context->id(); }
    time_t time() const { return m_time; }

    QVariant toVariant() const;
    static PageVisit* fromVariant(QVariant var, History* owner);

public:
    PageVisit(Page* page, Context* context, time_t time, History* parent):
        QObject(parent), m_page(page), m_context(context), m_time(time)
    {}

private:
    Page* m_page;
    Context* m_context;
    time_t m_time;
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

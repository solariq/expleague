#ifndef CONTEXT_H
#define CONTEXT_H

#include "page.h"

#include <QList>
#include <QQmlListProperty>

namespace expleague {
class Offer;
class Task;
class SearchRequest;
class Context: public Page {
    static const QString NAME_KEY;

    Q_OBJECT

    Q_PROPERTY(Task* task READ task CONSTANT)
    Q_PROPERTY(QQmlListProperty<SearchRequest> requests READ requests NOTIFY requestsChanged)
    Q_PROPERTY(expleague::SearchRequest* lastRequest READ lastRequest NOTIFY requestsChanged)

public:
    Task* task() const { return m_task; }

    QString icon() const;
    QString title() const;

    QQmlListProperty<SearchRequest> requests() const {
        return QQmlListProperty<SearchRequest>(const_cast<Context*>(this), const_cast<QList<SearchRequest*>&>(m_requests));
    }

    SearchRequest* lastRequest() const;

    bool hasTask() const;

public:
    void setTask(Task* task);

signals:
    void visitedUrl(const QUrl& url);
    void requestsChanged();

private slots:
    void onTaskFinished();
    void onActiveScreenChanged();

public:
    explicit Context(const QString& id = "unknown", doSearch* parent = 0);
    virtual ~Context();

private:
    Task* m_task = 0;
    QList<SearchRequest*> m_requests;
    mutable QString m_icon_cache;
};
}

#endif // CONTEXT_H

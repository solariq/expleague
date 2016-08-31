#ifndef CONTEXT_H
#define CONTEXT_H

#include "page.h"

#include <QList>
#include <QQmlListProperty>

namespace expleague {
class Offer;
class Task;
class SearchRequest;
class Vault;
class Context: public Page {
    Q_OBJECT

    Q_PROPERTY(Task* task READ task CONSTANT)
    Q_PROPERTY(QQmlListProperty<SearchRequest> requests READ requests NOTIFY requestsChanged)
    Q_PROPERTY(expleague::SearchRequest* lastRequest READ lastRequest NOTIFY requestsChanged)
    Q_PROPERTY(expleague::Vault* vault READ vault CONSTANT)

public:
    Task* task() const { return m_task; }

    QString icon() const;
    QString title() const;

    QQmlListProperty<SearchRequest> requests() const {
        return QQmlListProperty<SearchRequest>(const_cast<Context*>(this), const_cast<QList<SearchRequest*>&>(m_requests));
    }

    SearchRequest* lastRequest() const;

    bool hasTask() const;

    Vault* vault() const { return m_vault; }

public:
    void setTask(Task* task);
    virtual void transition(Page *from, TransitionType type);

signals:
    void visitedUrl(const QUrl& url);
    void requestsChanged();

private slots:
    void onTaskFinished();
    void onActiveScreenChanged();

protected:
    void interconnect();
public:
    explicit Context(const QString& id = "unknown", doSearch* parent = 0);
    virtual ~Context();

private:
    Task* m_task = 0;
    QList<SearchRequest*> m_requests;
    Vault* m_vault;
    mutable QString m_icon_cache;
    friend class Vault;
};
}

#endif // CONTEXT_H

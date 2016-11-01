#ifndef CONTEXT_H
#define CONTEXT_H

#include "page.h"

#include "../ir/bow.h"

#include <QList>
#include <QQmlListProperty>

namespace expleague {
class Offer;
class Task;
class SearchRequest;
class SearchSession;
class Vault;
class MarkdownEditorPage;
class Context: public CompositeContentPage {
    Q_OBJECT

    Q_PROPERTY(Task* task READ task CONSTANT)
    Q_PROPERTY(expleague::SearchRequest* lastRequest READ lastRequest NOTIFY requestsChanged)
    Q_PROPERTY(expleague::Vault* vault READ vault CONSTANT)
    Q_PROPERTY(QQmlListProperty<expleague::MarkdownEditorPage> documents READ documentsQml NOTIFY documentsChanged)
    Q_PROPERTY(expleague::MarkdownEditorPage* document READ document WRITE setActiveDocument NOTIFY activeDocumentChanged)

public:
    Task* task() const { return m_task; }

    QString title() const;
    QString icon() const;

    SearchRequest* lastRequest() const;

    QQmlListProperty<MarkdownEditorPage> documentsQml() const { return QQmlListProperty<MarkdownEditorPage>(const_cast<Context*>(this), const_cast<QList<MarkdownEditorPage*>&>(m_documents)); }
    MarkdownEditorPage* document() const { return m_active_document_index >= 0 ? m_documents[m_active_document_index] : 0; }
    void setActiveDocument(MarkdownEditorPage* active);

    Vault* vault() const { return m_vault; }

public:
    QList<MarkdownEditorPage*> documents() const { return m_documents; }

    void setTask(Task* task);
    bool hasTask() const;

    Q_INVOKABLE void setName(const QString& name);

    SearchSession* match(SearchRequest* request);
    void transition(Page* from, TransitionType type);

    Q_INVOKABLE MarkdownEditorPage* createDocument();
    void appendDocument(MarkdownEditorPage* document);
    Q_INVOKABLE void removeDocument(MarkdownEditorPage* document);

signals:
    void visitedUrl(const QUrl& url) const ;
    void requestsChanged() const;
    void documentsChanged() const;
    void activeDocumentChanged() const;
    void profileChanged() const;

private slots:
    void onTaskFinished();
    void onActiveScreenChanged();
    void onQueriesChanged() { emit requestsChanged(); }

protected:
    void interconnect();

    friend class NavigationManager;
public:
    explicit Context(const QString& id, const QString& name, doSearch* parent);
    explicit Context(const QString& id = "unknown", doSearch* parent = 0);
    virtual ~Context();

private:
    QString m_name;
    Task* m_task = 0;
    QList<SearchSession*> m_sessions;
    QList<MarkdownEditorPage*> m_documents;
    QList<CompositeContentPage*> m_composite_parts;
    int m_active_document_index = -1;
    Vault* m_vault;
    mutable QString m_icon_cache;

    friend class Vault;
};
}

#endif // CONTEXT_H

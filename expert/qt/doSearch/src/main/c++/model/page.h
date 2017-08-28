#ifndef PAGE_H
#define PAGE_H

#include <functional>

#include <QSharedPointer>
#include <QObject>
#include <QDir>
#include <QUrl>
#include <QList>

#include "../ir/bow.h"
#include "../util/pholder.h"
#include "uiowner.h"

class QQuickItem;
class QQmlContext;
namespace expleague {
struct PageImpl;
class doSearch;
class NavigationManager;

struct PageModel {
    int freq;
    time_t when;

    PageModel(): freq(0), when(0) {}

    QVariant toVariant(const QString& pageId) const;
    static PageModel fromVariant(const QVariant& var);
};


class Page: public UIOwner, protected PersistentPropertyHolder {
    Q_OBJECT

    Q_PROPERTY(QString id READ id CONSTANT)
    Q_PROPERTY(QString icon READ icon NOTIFY iconChanged)
    Q_PROPERTY(QString title READ title NOTIFY titleChanged)


    Q_PROPERTY(Page* container READ container NOTIFY containerChanged)

public:

    enum TransitionType: int {
        TYPEIN,
        REDIRECT,
        FOLLOW_LINK,
        SELECT_TAB,
        CHILD_GROUP_OPEN,
        CHANGED_SCREEN
    };
    Q_ENUMS(TransitionType)

public:
    QString id() const { return m_id; }

    virtual QString icon() const { return "qrc:/avatar.png"; }
    virtual QString title() const { return id(); }

    QList<Page*> incoming() const;
    QList<Page*> outgoing() const;

    void forgetIncoming(Page* page);
    void forgetOutgoing(Page* page);

    Page* lastVisited() const { return m_last_visited; }
    time_t lastVisitTs() const { return m_last_visit_ts; }

    Q_INVOKABLE virtual double pOut(Page*) const;
    Q_INVOKABLE virtual double pIn(Page*) const;

    Q_INVOKABLE virtual void transition(Page* to, TransitionType type);

    virtual double titleWidth() const;

    QList<Page*> children(const QString& prefix = "") const;

    /** inner element of composite should return the owner */
    virtual Page* parentPage() const { return 0; }
    /** nested elements has container */
    virtual Page* container() const { return const_cast<Page*>(this); }

signals:
    void iconChanged(const QString& icon);
    void titleChanged(const QString& title);
    void containerChanged();

public:
    Page(): Page("undefined", "", 0) {} // never ever use this constructor, it exists for compartibility purposes only!

    doSearch* parent() const;
    friend class doSearch;

protected:
    explicit Page(const QString& id, const QString& uiQml, doSearch* parent);

    virtual void interconnect();

    QDir storage() const;
    void visitChildren(const QString& prefix, std::function<void (Page*)> visitor) const;

    virtual void incomingTransition(Page* from, TransitionType type);

private:
    QString m_id;

    Page* m_last_visited = 0;
    int m_in_total = 0;
    QHash<Page*, PageModel> m_incoming;
    int m_out_total = 0;
    QHash<Page*, PageModel> m_outgoing;
    time_t m_last_visit_ts = 0;
};

class ContentPage: public Page {
    Q_OBJECT

    Q_PROPERTY(QString text READ textContent WRITE setTextContent NOTIFY textContentChanged)

public:
    virtual QString textContent() const;
    virtual void setTextContent(const QString& content);

signals:
    void textContentChanged() const;
    void changingProfile(const BoW& oldOne, const BoW& newOne) const;

public:
    virtual BoW profile() const { return m_profile; }

    void processTextContentWhenAvailable(std::function<void (const QString&)> callback) const;
    void processProfileWhenAvailable(std::function<void (const BoW&)> callback) const;

protected:
    virtual void setProfile(const BoW& profile);

public:
    explicit ContentPage(const QString& id, const QString& uiQml, doSearch* parent);

private:
    BoW m_profile;
};

class CompositeContentPage: public ContentPage {
    Q_OBJECT

public:
    int size() const { return m_parts.size(); }
    ContentPage* part(int index) const { return m_parts[index]; }
    bool contains(ContentPage* page) const { return m_parts.contains(page); }
    QList<ContentPage*> parts() const { return m_parts; }

    QString textContent() const override;
    void setTextContent(const QString& content) override;

    friend class ContentPage;

public:
    CompositeContentPage(const QString& id, const QString& uiQml, doSearch* parent);

signals:
    void partAppended(ContentPage* part);
    void partRemoved(ContentPage* part);

protected slots:
    virtual void onPartContentsChanged() { emit textContentChanged(); }
    virtual void onPartProfileChanged(const BoW& oldOne, const BoW& newOne);

protected:
    QList<ContentPage*>& partsRef() { return m_parts; } // for QML purposes ONLY!!

    bool appendPart(ContentPage* part);
    void removePart(ContentPage* part);

    void interconnect() override;

private:
    QList<ContentPage*> m_parts;
};
}

#endif // PAGE_H

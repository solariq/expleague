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


class Page: public QObject, protected PersistentPropertyHolder {
    Q_OBJECT

    Q_PROPERTY(QString id READ id CONSTANT)
    Q_PROPERTY(QString icon READ icon NOTIFY iconChanged)
    Q_PROPERTY(QString title READ title NOTIFY titleChanged)
    Q_PROPERTY(expleague::Page::State state READ state WRITE setState NOTIFY stateChanged)

public:
    enum State: int {
        ACTIVE = 0,
        INACTIVE = 1,
        CLOSED = 2,
    };
    Q_ENUMS(State)

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

    State state() const { return m_state; }

    QList<Page*> outgoing() const;
    QList<Page*> incoming() const;

    void forgetIncoming(Page* page);
    void forgetOutgoing(Page* page);

    Page* lastVisited() const { return m_last_visited; }
    time_t lastVisitTs() const { return m_last_visit_ts; }

    void setState(State closed);

    Q_INVOKABLE QQuickItem* ui(bool useCache = true) const;
    bool compareUI(QQuickItem* item) const { return m_ui == item; }

    Q_INVOKABLE virtual double pOut(Page*) const;
    Q_INVOKABLE virtual double pIn(Page*) const;

    Q_INVOKABLE virtual void transition(Page* to, TransitionType type);

    virtual double titleWidth() const;

    virtual Page* parentPage() const { return 0; }
    QList<Page*> children(const QString& prefix = "") const;

signals:
    void iconChanged(const QString& icon);
    void titleChanged(const QString& title);
    void stateChanged(Page::State closed);

public:
    Page(): Page("undefined", "", 0) {} // never ever use this constructor, it exists for compartibility purposes only!

    doSearch* parent() const;
    void transferUI(Page* other) const;
    friend class doSearch;

protected:
    explicit Page(const QString& id, const QString& uiQml, doSearch* parent);

    virtual void interconnect();
    virtual void initUI(QQuickItem*) const {}

    QDir storage() const;
    void visitChildren(const QString& prefix, std::function<void (Page*)> visitor) const;

    virtual void incomingTransition(Page* from, TransitionType type);

private:
    QString m_id;
    QUrl m_ui_url;

    mutable QQmlContext* m_context = 0;
    mutable QQuickItem* m_ui = 0;

    Page* m_last_visited = 0;
    int m_in_total;
    QHash<Page*, PageModel> m_incoming;
    int m_out_total;
    QHash<Page*, PageModel> m_outgoing;
    time_t m_last_visit_ts = 0;

    State m_state;
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
    BoW profile() const { return m_profile; }

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

    QString textContent() const;
    void setTextContent(const QString& content);

    friend class ContentPage;

public:
    CompositeContentPage(const QString& id, const QString& uiQml, doSearch* parent);

signals:
    void partAppended(ContentPage* part);
    void partRemoved(ContentPage* part);

protected slots:
    virtual void onPartContentsChanged() { emit textContentChanged(); }
    virtual void onPartProfileChanged(const BoW& oldOne, const BoW& newOne);
    virtual void onPartStateChanged(Page::State state);
protected:
    QList<ContentPage*>& parts() { return m_parts; } // for QML purposes ONLY!!

    bool appendPart(ContentPage* part);
    void removePart(ContentPage* part);

    void interconnect();

private:
    QList<ContentPage*> m_parts;
};
}

Q_DECLARE_METATYPE(expleague::Page::State)

#endif // PAGE_H

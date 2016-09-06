#ifndef PAGE_H
#define PAGE_H

#include <functional>

#include <QSharedPointer>
#include <QObject>
#include <QDir>
#include <QUrl>
#include <QList>
#include <QHash>
#include <QVariant>

class QQuickItem;
class QQmlContext;
namespace expleague {
struct PageImpl;
class doSearch;

struct PageModel {
    int freq;
    time_t when;

    PageModel(): freq(0), when(0) {}

    QVariant toVariant(const QString& pageId) const;
    static PageModel fromVariant(const QVariant& var);
};

class Page: public QObject {
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
    Page* lastVisited() const { return m_last_visited; }
    time_t lastVisitTs() const { return m_last_visit_ts; }

    Q_INVOKABLE QQuickItem* ui() const;

    Q_INVOKABLE virtual double pOut(Page*) const;
    Q_INVOKABLE virtual double pIn(Page*) const;

    Q_INVOKABLE virtual void transition(Page*, TransitionType type);

    virtual double titleWidth() const;

    void setState(State closed);

signals:
    void iconChanged(const QString& icon);
    void titleChanged(const QString& title);
    void stateChanged(Page::State closed);

public:
    Page(): Page("undefined", "", 0) {} // never ever use this constructor, it exists for compartibility purposes only!

    doSearch* parent() const;

protected:
    explicit Page(const QString& id, const QString& uiQml, doSearch* parent);
    virtual void interconnect();
    virtual void initUI(QQuickItem*) const {}
    void transferUI(Page* other) const;

    QVariant value(const QString& key) const;
    void store(const QString& key, const QVariant& value);
    void visitAll(const QString& key, std::function<void (const QVariant&)> visitor) const;
    int count(const QString& key) const;
    void append(const QString& key, const QVariant& value);
    void remove(const QString& key);
    void replaceOrAppend(const QString& key, const QVariant& value, std::function<bool (const QVariant& lhs, const QVariant& rhs)> equals);

    QDir storage() const;
    void save() const;
    virtual void incomingTransition(Page* from, TransitionType type);

private:
    friend class doSearch;
    QVariant* resolve(const QStringList& path, bool create = false);

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

    QVariant m_properties = QVariant(QHash<QString, QVariant>());
    mutable volatile int m_changes = 0;
    mutable volatile int m_saved_changes = 0;
};
}

Q_DECLARE_METATYPE(expleague::Page::State)

#endif // PAGE_H

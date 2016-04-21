#ifndef CONTEXT_H
#define CONTEXT_H

#include <QObject>
#include <QQmlListProperty>
#include <QList>
#include <QStack>
#include <QQuickPaintedItem>
#include <QQmlApplicationEngine>

#include "profile.h"

extern QQmlApplicationEngine* rootEngine;

namespace expleague {
class Bouquet;
class Screen;
class Context: public QObject {
    Q_OBJECT

    Q_PROPERTY(QQmlListProperty<expleague::Bouquet> flowers READ flowers NOTIFY flowersChanged)
    Q_PROPERTY(Bouquet* bouquet READ bouquet NOTIFY bouquetChanged)
    Q_PROPERTY(QString omniboxText READ omniboxText NOTIFY omniboxChanged)

public:
    explicit Context(const QString& id): m_id(id) {
        state.beginGroup("context/" + id);
    }

    virtual ~Context();

    Bouquet* bouquet();

    QQmlListProperty<Bouquet> flowers() {
        return QQmlListProperty<Bouquet>(this, m_groups);
    }

    QString omniboxText();

    QString id() {
        return m_id;
    }

public:
    Q_INVOKABLE void fetchOmnibox(const QString&);

signals:
    void omniboxChanged(const QString&);
    void bouquetChanged(const Bouquet*);
    void flowersChanged();

private:
    friend class Bouquet;
    QString m_id;
    QList<Bouquet*> m_groups;
    QSettings state;
};

class Bouquet: public QObject {
    Q_OBJECT

    Q_PROPERTY(QUrl icon READ icon NOTIFY iconChanged)
    Q_PROPERTY(QString caption READ caption NOTIFY captionChanged)
    Q_PROPERTY(QQmlListProperty<expleague::Screen> screens READ screens NOTIFY screensChanged)
    Q_PROPERTY(QQmlListProperty<expleague::Screen> history READ history)
    Q_PROPERTY(bool active READ active NOTIFY activeChanged)
    Q_PROPERTY(Screen* screen READ screen NOTIFY screenChanged)
    Q_PROPERTY(Context* context READ context)

public:
    QQmlListProperty<Screen> screens() {
        return QQmlListProperty<Screen>(this, m_screens);
    }

    QQmlListProperty<Screen> history() {
        return QQmlListProperty<Screen>(this, m_history);
    }

    bool active() const {
        return state.value("active", false).toBool();
    }

    QUrl icon() const {
        return QUrl("qrc:/states/play.png");
    }

    QString caption() const {
        return "Bouquet";
    }

    Screen* screen() {
        return m_history.isEmpty() ? 0 : m_history.last();
    }

    Context* context() const {
        return m_context;
    }

    void activate(Screen* screen);
    void remove(Screen* screen);

    virtual bool handleOmniboxInput(const QString&) {
        return false;
    }

    void setActive(bool active);

public:
    explicit Bouquet(const QString& id, Context* parent): m_id(id), m_context(parent){
        state.beginGroup(parent->state.group());
        state.beginGroup(id);
    }

    virtual ~Bouquet();

signals:
    void iconChanged(const QUrl&);
    void captionChanged(const QString&);
    void activeChanged(bool);
    void screenChanged(Screen* screen);
    void screensChanged();

protected:
    QList<Screen*> m_screens;

private:
    friend class Screen;
    QSettings state;

    QString m_id;
    Context* m_context;
    QList<Screen*> m_history;
};

class Screen: public QObject {
    Q_OBJECT

    Q_PROPERTY(QUrl icon READ icon NOTIFY iconChanged)
    Q_PROPERTY(QString name READ name NOTIFY nameChanged)
    Q_PROPERTY(Bouquet* bouquet READ bouquet NOTIFY bouquetChanged)
    Q_PROPERTY(bool active READ active WRITE setActive NOTIFY activeChanged)
    Q_PROPERTY(QQmlListProperty<QQuickItem> contents READ contents NOTIFY contentsChanged)

public:
    Bouquet* bouquet() {
        return m_group;
    }

    bool active() const {
        return state.value("active", false).toBool();
    }

    void setActive(bool newState);
    QString id() const {
        return m_id;
    }

    QString name() const {
        return "Screen";
    }

    QString icon() {
        return "qrc:/status/play.png";
    }

    virtual QString location() {
        return "";
    }

    QQmlListProperty<QQuickItem> contents() {
        return QQmlListProperty<QQuickItem>(this, m_children);
    }

    virtual bool handleOmniboxInput(const QString&) {
        return false;
    }

public:
    Q_INVOKABLE void bind(QQuickItem* parent) {
        qDebug() << "Setting parent to " << parent;
        QVariant v;
        v.setValue(parent);
        root()->setParentItem(parent);
        root()->setProperty("myParent", v);
    }

signals:
    void activeChanged(bool);
    void bouquetChanged(Bouquet*);
    void iconChanged(const QUrl&);
    void nameChanged(const QString&);
    void contentsChanged();

protected:
    explicit Screen(QString id, Bouquet* owner, QUrl item): m_group(owner), m_id(id) {
        QQmlComponent component(rootEngine, item);
        m_children.append(qobject_cast<QQuickItem*>(component.create()));
        contentsChanged();
        state.beginGroup(owner->state.group());
        state.beginGroup(id);
    }

    explicit Screen() {}

    QQuickItem* root() {
        return m_children.first();
    }

    template<typename T>
    T* itemById(const QString& id) {
        if (m_children.isEmpty())
            return 0;
        qDebug() << root()->objectName() << " id: " << root()->property("id");
        if (root()->objectName() == id)
            return (T*)root();
        return root()->findChild<T*>(id);
    }

    QSettings state;

private:
    Bouquet* m_group;
    QList<QQuickItem*> m_children;
    const QString m_id;
};

}

Q_DECLARE_METATYPE(expleague::Screen*)
Q_DECLARE_METATYPE(expleague::Bouquet*)
Q_DECLARE_METATYPE(expleague::Context*)
#endif // CONTEXT_H

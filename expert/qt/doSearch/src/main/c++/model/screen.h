#ifndef SCREEN_H
#define SCREEN_H

#include <QObject>
#include <QList>

#include <QQuickItem>
#include <QQmlListProperty>
#include <QQmlComponent>
#include <QQmlApplicationEngine>
#include <QQmlContext>

extern QQmlApplicationEngine* rootEngine;

namespace expleague {

class Screen: public QObject {
    Q_OBJECT

    Q_PROPERTY(QUrl icon READ icon NOTIFY iconChanged)
    Q_PROPERTY(QString name READ name NOTIFY nameChanged)
    Q_PROPERTY(bool active READ active WRITE setActive NOTIFY activeChanged)
    Q_PROPERTY(QString location READ location WRITE handleOmniboxInput NOTIFY locationChanged)

public:
    bool active() const {
        return m_active;
    }

    virtual QString name() const = 0;
    virtual QUrl icon() const = 0;
    virtual QString location() const = 0;

    virtual bool handleOmniboxInput(const QString&) = 0;

public:
    Q_INVOKABLE void bind(QQuickItem* parent);
    Q_INVOKABLE void unbind();

    Q_INVOKABLE virtual void remove() {
        setActive(false);
        deleteLater();
    }

    void setActive(bool newState) {
        if (newState != m_active) {
            m_active = newState;
            if (!m_active)
                unbind();
            activeChanged();
        }
    }

signals:
    void activeChanged();
    void iconChanged(const QUrl&);
    void nameChanged(const QString&);
    void locationChanged(const QString&);

protected:
    explicit Screen(const QUrl& item, QObject* parent = 0);
    virtual ~Screen();

    QQuickItem* root() { return m_root; }

    void setupOwner();

    template<typename T>
    T* itemById(const QString& id) {
        if (!m_root)
            return 0;
        if (m_root->objectName() == id)
            return m_root;
        T* result = m_root->findChild<T*>(id);
        if (!result)
            qDebug() << "Item was not found by objectName " << id;
        return result;
    }

private:
    friend class StateSaver;
    QQuickItem* m_root;
    QQmlContext m_context;
    bool m_active = false;
};

}

QML_DECLARE_TYPE(expleague::Screen)
#endif

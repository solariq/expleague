#ifndef SCREEN_H
#define SCREEN_H

#include <QObject>
#include <QList>

#include <QQuickItem>
#include <QQmlListProperty>
#include <QQmlComponent>
#include <QQmlApplicationEngine>

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
    Q_INVOKABLE void bind(QQuickItem* parent) {
        QVariant v;
        v.setValue(parent);
        m_root->setParentItem(parent);
    }

    Q_INVOKABLE virtual void remove() {
        setActive(false);
        deleteLater();
    }

    void setActive(bool newState) {
        if (newState != m_active) {
            m_active = newState;
            activeChanged();
        }
    }

signals:
    void activeChanged();
    void iconChanged(const QUrl&);
    void nameChanged(const QString&);
    void locationChanged(const QString&);

protected:
    explicit Screen(QUrl item, QObject* parent = 0): QObject(parent) {
        QQmlComponent component(rootEngine, item, QQmlComponent::PreferSynchronous);
        if (component.isError()) {
            qWarning() << "Error during screen load";
            foreach(QQmlError error, component.errors()) {
                qWarning() << error;
            }
        }
        QQuickItem* root = (QQuickItem*)component.create();
        root->setParent(this);
        m_root = root;
    }

    QQuickItem* root() {
        return m_root;
    }

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
    QQuickItem* m_root;
    bool m_active = false;
};

}

QML_DECLARE_TYPE(expleague::Screen)
#endif

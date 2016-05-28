#ifndef FOLDER_H
#define FOLDER_H

#include <assert.h>

#include <QObject>
#include <QQmlListProperty>
#include <QList>

#include "screen.h"

namespace expleague {
class Context;
class Folder: public QObject {
    Q_OBJECT

    Q_PROPERTY(QUrl icon READ icon NOTIFY iconChanged)
    Q_PROPERTY(QString caption READ caption NOTIFY captionChanged)
    Q_PROPERTY(QQmlListProperty<expleague::Screen> screens READ screens NOTIFY screensChanged)
    Q_PROPERTY(bool active READ active WRITE setActive NOTIFY activeChanged)
    Q_PROPERTY(Screen* screen READ screen NOTIFY screenChanged)

public:
    QQmlListProperty<Screen> screens() {
        return QQmlListProperty<Screen>(this, m_screens);
    }

    bool active() const {
        return m_active;
    }

    virtual QUrl icon() const {
        return QUrl("qrc:/md.png");
    }

    virtual QString caption() const {
        return "Folder";
    }

    Screen* screen() {
        foreach(Screen* screen, m_screens) {
            if (screen->active())
                return screen;
        }
        return 0;
    }

    virtual bool handleOmniboxInput(const QString&, bool) {
        return false;
    }

    Context* parent() {
        return (Context*)QObject::parent();
    }

    void setActive(bool value) {
        if (value != m_active) {
            m_active = value;
            activeChanged();
        }
    }

public:
    explicit Folder(QObject* parent = 0): QObject(parent) {}

private slots:
    void screenStateChanged() {
        Screen* s = (Screen*)sender();
        if (s->active()) {
            foreach (Screen* screen, m_screens) {
                if (screen != s) {
                    screen->setActive(false);
                }
            }
            screenChanged(s);
        }
        else if (!screen()) { // need to activate screen
            Screen* result = 0;
            for (int i = 0; i < m_screens.size() && m_screens.size() > 1; i++) {
                if (m_screens[i] == s) {
                    result = m_screens[i + (i > 0 ? -1 : +1)];
                    result->setActive(true);
                    break;
                }
            }
            screenChanged(result);
        }
    }

    void screenDestroyed(QObject* screen) {
        qDebug() << "Screen destroyed" << screen;
        m_screens.removeOne((Screen*)screen);
        screensChanged();
    }

signals:
    void iconChanged(const QUrl&);
    void captionChanged(const QString&);
    void activeChanged();
    void screenChanged(Screen* screen);
    void screenOpened(Screen* screen);
    void screensChanged();

protected:
    void append(Screen* screen) {
        assert(screen->parent() == this);
        connect(screen, SIGNAL(activeChanged()), SLOT(screenStateChanged()));
        connect(screen, SIGNAL(destroyed(QObject*)), SLOT(screenDestroyed(QObject*)));

        m_screens.append(screen);
        screensChanged();
        if (m_screens.length() == 1) {
            screen->setActive(true);
        }
    }

    void insert(int position, Screen* screen) {
        assert(screen->parent() == this);
        connect(screen, SIGNAL(activeChanged()), SLOT(screenStateChanged()));
        connect(screen, SIGNAL(destroyed(QObject*)), SLOT(screenDestroyed(QObject*)));

        if (position < m_screens.size())
            m_screens.insert(position, screen);
        else
            m_screens.append(screen);
        screensChanged();
    }

    Screen* at(int index) const {
        if (index < 0 || index >= m_screens.size())
            return 0;
        return m_screens[index];
    }

protected:
    QList<Screen*> m_screens;

private:
    friend class StateSaver;
    bool m_active = false;
};
}

QML_DECLARE_TYPE(expleague::Folder)
#endif // CONTEXT_H

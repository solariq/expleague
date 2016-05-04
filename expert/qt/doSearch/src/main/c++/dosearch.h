#ifndef DOSEARCH_H
#define DOSEARCH_H

#include <QQmlApplicationEngine>
#include <QQmlListProperty>

extern QQmlApplicationEngine* rootEngine;

#include "model/context.h"
#include "model/folder.h"
#include "model/screen.h"

#include "league.h"

namespace expleague {

class StateSaver;
class doSearch: public QObject {
    Q_OBJECT

    Q_PROPERTY(QQmlListProperty<expleague::Context> contexts READ contexts NOTIFY contextsChanged)
    Q_PROPERTY(Context* context READ context NOTIFY contextChanged)
    Q_PROPERTY(Folder* folder READ folder NOTIFY folderChanged)
    Q_PROPERTY(Screen* screen READ screen NOTIFY screenChanged)
    Q_PROPERTY(League* league READ league CONSTANT)
    Q_PROPERTY(QString location READ location NOTIFY locationChanged)

public:
    explicit doSearch(QObject* parent = 0);

    League* league() { return &m_league; }

    QQmlListProperty<Context> contexts() { return QQmlListProperty<Context>(this, m_contexts); }
    Context* context() {
        foreach(Context* ctxt, m_contexts) {
            if (ctxt->active()) {
                return ctxt;
            }
        }

        return 0;
    }
    Folder* folder() { return m_folder; }
    Screen* screen() { return m_screen; }
    QString location() { return m_screen ? m_screen->location() : "";}

public:
    void append(Context* context) {
        assert(context->parent() == this);
        m_contexts.append(context);
        connect(context, SIGNAL(activeChanged()), SLOT(contextStateChanged()));
        connect(context, SIGNAL(destroyed(QObject*)), SLOT(contextDestroyed(QObject*)));
        contextsChanged();
        if (m_contexts.length() == 1) {
            context->setActive(true);
        }
    }

signals:
    void contextChanged(Context*);
    void contextsChanged();
    void folderChanged(Folder*);
    void screenChanged(Screen*);
    void locationChanged(const QString&);

private slots:
    void folderChangedSlot(Folder* folder) {
//        qDebug() << "Folder changed: " << folder;
        if (m_folder == folder)
            return;
        if (m_folder)
            QObject::disconnect(m_folder, SIGNAL(screenChanged(Screen*)), this, SLOT(screenChangedSlot(Screen*)));
        if (folder)
            connect(folder, SIGNAL(screenChanged(Screen*)), SLOT(screenChangedSlot(Screen*)));
        m_folder = folder;
        folderChanged(folder);
        screenChangedSlot(m_folder ? m_folder->screen() : 0);
    }

    void screenChangedSlot(Screen* screen) {
//        qDebug() << "Screen changed: " << screen;
        if (m_screen == screen)
            return;
        if (m_screen)
            QObject::disconnect(m_screen, SIGNAL(locationChanged(QString)), this, SLOT(locationChangedSlot(QString)));
        if (screen)
            connect(screen, SIGNAL(locationChanged(QString)), SLOT(locationChangedSlot(QString)));
        m_screen = screen;
        screenChanged(screen);
        locationChangedSlot(screen ? screen->location() : "");
    }

    void locationChangedSlot(const QString& text) {
//        qDebug() << "Location changed: "<< text;
        locationChanged(text);
    }

    void contextStateChanged() {
        Context* currentContext = (Context*)sender();
//        qDebug() << "Context "<< currentContext->name() << " state changed to " << (currentContext->active() ? "active" : "inactive");
        if (currentContext->active()) {
            foreach (Context* context, m_contexts) {
                if (context != currentContext) {
                    context->setActive(false);
                }
            }
            connect(currentContext, SIGNAL(folderChanged(Folder*)), SLOT(folderChangedSlot(Folder*)));
            contextChanged(currentContext);
            folderChangedSlot(currentContext->folder());
        }
        else QObject::disconnect(currentContext, SIGNAL(folderChanged(Folder*)), this, SLOT(folderChangedSlot(Folder*)));
    }

    void contextDestroyed(QObject* obj) {
        m_contexts.removeOne(qobject_cast<Context*>(obj));
    }

public:
    QList<Context*> m_contexts;
    League m_league;
    Folder* m_folder = 0;
    Screen* m_screen = 0;
    StateSaver* m_saver;
};

class StateSaver: public QObject {
    Q_OBJECT

public:
    void restoreState(doSearch* search);

public slots:
    void profileChanged(Profile*);
    void saveProfiles();

public:
    StateSaver(QObject* parent = 0);

private:
    QSettings m_settings;
};
}

Q_DECLARE_METATYPE(expleague::doSearch*)
#endif // DOSEARCH_H

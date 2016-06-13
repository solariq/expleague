#ifndef DOSEARCH_H
#define DOSEARCH_H

#include <QQmlListProperty>
class QQmlApplicationEngine;
class QSystemTrayIcon;
class QQuickWindow;

extern QQmlApplicationEngine* rootEngine;
#ifndef Q_OS_MAC
extern QSystemTrayIcon* trayIcon;
#endif

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
    Q_PROPERTY(QQuickWindow* main READ main WRITE setMain NOTIFY mainChanged)

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
    QQuickWindow* main() { return m_main; }

public:    
    static doSearch* instance();

    void append(Context* context) {
        assert(context->parent() == this);
        m_contexts.append(context);
        connect(context, SIGNAL(activeChanged()), SLOT(contextStateChanged()));
        connect(context, SIGNAL(closed()), SLOT(contextClosed()));
        contextsChanged();
        if (m_contexts.length() == 1) {
            context->setActive(true);
        }
    }

    void restoreState();

    Q_INVOKABLE void setMain(QQuickWindow* main) {
        m_main = main;
        mainChanged(main);
    }

signals:
    void contextChanged(Context*);
    void contextsChanged();
    void folderChanged(Folder*);
    void screenChanged(Screen*);
    void locationChanged(const QString&);
    void mainChanged(QQuickWindow*);

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

    void contextClosed() {
        Context* context = qobject_cast<Context*>(QObject::sender());
        int index = m_contexts.indexOf(context);
        m_contexts.removeOne(context);
        contextsChanged();
        if (!m_contexts.empty() && context->active())
            m_contexts[index > 0 ? index - 1 : 0]->setActive(true);
    }

public:
    friend class StateSaver;
    QList<Context*> m_contexts;
    League m_league;
    Folder* m_folder = 0;
    Screen* m_screen = 0;
    StateSaver* m_saver;
    QQuickWindow* m_main = 0;
};
}

Q_DECLARE_METATYPE(expleague::doSearch*)
#endif // DOSEARCH_H

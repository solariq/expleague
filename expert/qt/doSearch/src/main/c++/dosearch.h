#ifndef DOSEARCH_H
#define DOSEARCH_H

#include <QQmlApplicationEngine>

extern QQmlApplicationEngine* rootEngine;

#include "model/context.h"
#include "model/folder.h"
#include "model/screen.h"

namespace expleague {

class doSearch: public QObject {
    Q_OBJECT

    Q_PROPERTY(Context* context READ context NOTIFY contextChanged)
    Q_PROPERTY(Folder* folder READ folder NOTIFY folderChanged)
    Q_PROPERTY(Screen* screen READ screen NOTIFY screenChanged)
    Q_PROPERTY(QString location READ location NOTIFY locationChanged)

public:
    explicit doSearch(QObject* parent = 0) : QObject(parent), m_context("test") {
        connect(&m_context, SIGNAL(folderChanged(Folder*)), SLOT(folderChangedSlot(Folder*)));
    }

    Context* context() { return &m_context; }
    Folder* folder() { return m_folder; }
    Screen* screen() { return m_screen; }
    QString location() { return m_screen ? m_screen->location() : "";}

signals:
    void contextChanged(Context*);
    void folderChanged(Folder*);
    void screenChanged(Screen*);
    void locationChanged();

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
        screenChangedSlot(m_folder->screen());
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
        locationChanged();
    }

    void locationChangedSlot(const QString& location) {
//        qDebug() << "Location changed: "<< location;
        locationChanged();
    }

public:
    Context m_context;
    Folder* m_folder = 0;
    Screen* m_screen = 0;
};
}

Q_DECLARE_METATYPE(expleague::doSearch*)
#endif // DOSEARCH_H

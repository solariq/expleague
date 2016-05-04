#ifndef CONTEXT_H
#define CONTEXT_H

#include <QObject>

#include <QStyle>
#include <QPixmap>
#include <QApplication>
#include <QStyleOptionButton>

#include <QQuickImageProvider>
#include <QQmlListProperty>

#include "folder.h"

namespace expleague {
class Offer;
class Task;
class Context: public QObject {
    Q_OBJECT

    Q_PROPERTY(QQmlListProperty<expleague::Folder> folders READ folders NOTIFY foldersChanged)
    Q_PROPERTY(Folder* folder READ folder NOTIFY folderChanged)
    Q_PROPERTY(Task* task READ task CONSTANT)
    Q_PROPERTY(QUrl icon READ icon CONSTANT)
    Q_PROPERTY(QString name READ name NOTIFY nameChanged)
    Q_PROPERTY(bool active READ active WRITE setActive NOTIFY activeChanged)

public:
    Folder* folder() {
        foreach(Folder* folder, m_folders) {
            if (folder->active())
                return folder;
        }
        return 0;
    }

    QQmlListProperty<Folder> folders() {
        return QQmlListProperty<Folder>(this, m_folders);
    }

    QString name() const {
        return m_name;
    }

    QUrl icon() const {
        return m_icon;
    }

    Task* task() const {
        return m_task;
    }

    bool active() {
        return m_active;
    }

public:
    Q_INVOKABLE void handleOmniboxInput(const QString& url, bool newTab);
    Q_INVOKABLE bool remove() {
        return false;
    }

    void setActive(bool state) {
        if (state == m_active)
            return;
        m_active = state;
        activeChanged();
    }

signals:
    void nameChanged(const QString&);
    void activeChanged();
    void foldersChanged();
    void folderChanged(Folder* folder);

private slots:
    void folderStateChanged() {
        Folder* current = (Folder*)sender();
        if (current->active()) {
            foreach (Folder* folder, m_folders) {
                if (folder != current)
                    folder->setActive(false);
            }
            folderChanged(current);
        }
    }

public:
    explicit Context(const QString& name = "", QObject* parent = 0);
    explicit Context(Offer* offer, QObject* parent = 0);

protected:
    void append(Folder* folder) {
        assert(folder->parent() == this);
        m_folders.append(folder);
        connect(folder, SIGNAL(activeChanged()), SLOT(folderStateChanged()));
        foldersChanged();
        if (m_folders.length() == 1) {
            folder->setActive(true);
        }
    }

private:
    Task* m_task = 0;
    QString m_name;
    QUrl m_icon;
    QList<Folder*> m_folders;
    bool m_active = false;
};
}

QML_DECLARE_TYPE(expleague::Context)
#endif // CONTEXT_H

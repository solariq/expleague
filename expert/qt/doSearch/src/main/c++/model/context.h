#ifndef CONTEXT_H
#define CONTEXT_H

#include <QObject>
#include <QQmlListProperty>

#include "folder.h"

namespace expleague {
class Context: public QObject {
    Q_OBJECT

    Q_PROPERTY(QQmlListProperty<expleague::Folder> folders READ folders NOTIFY foldersChanged)
    Q_PROPERTY(Folder* folder READ folder NOTIFY folderChanged)
    Q_PROPERTY(QString name READ name)

public:
    explicit Context(const QString& name): m_name(name) {}

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

public:
    Q_INVOKABLE void handleOmniboxInput(const QString& url, bool newTab);

signals:
    void activeChanged(const Folder*);
    void foldersChanged();
    void folderChanged(Folder* folder);

private slots:
    void folderStateChanged() {
        Folder* f = (Folder*)sender();
        if (f->active()) {
            foreach (Folder* folder, m_folders) {
                if (folder != f) {
                    folder->setActive(false);
                }
            }
            folderChanged(f);
        }
    }

protected:
    void append(Folder* folder) {
        assert(folder->parent() == this);
        m_folders.append(folder);
        connect(folder, SIGNAL(activeChanged()), SLOT(folderStateChanged()));
        foldersChanged();
    }

private:
    QString m_name;
    QList<Folder*> m_folders;
};

}

QML_DECLARE_TYPE(expleague::Context)
#endif // CONTEXT_H

#ifndef HISTORY_H
#define HISTORY_H

#include <QObject>
#include <QSet>

class QSettings;
namespace expleague {
class doSearch;

class Context;
class Folder;
class Screen;

class Profile;

class StateSaver: public QObject {
    Q_OBJECT

public:
    void restoreState(doSearch* search);
    void restoreContext(Context* context);

public slots:
    void profileChanged(Profile*);
    void saveProfiles();

    void saveContexts();
    void contextChanged(Context* context);

    void saveFolders();
    void activeFolderChanged(Folder* folder);

    void saveScreens();
    void activeScreenChanged(Screen* screen);

    void saveRequests();
    void locationChanged(const QString& location);
    void answerChanged(const QString& answer);

public:
    StateSaver(QObject* parent = 0);

protected:
    void save(Context*, QSettings*);
    void save(Folder*, QSettings*);
    void save(Screen*, QSettings*);

    Folder* loadFolder(Context*, QSettings*);
    Screen* loadScreen(Folder*, QSettings*);

private:
    QSettings* m_settings;
    QSet<QObject*> m_connected;
};
}

#endif // HISTORY_H

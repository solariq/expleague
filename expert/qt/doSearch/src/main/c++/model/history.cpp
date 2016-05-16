#include "history.h"

#include <QUrl>
#include <QSettings>

#include <QTimer>

#include "../util/filethrottle.h"
#include "../dosearch.h"
#include "../profile.h"

#include "web/webfolder.h"

#include "web/webscreen.h"
#include "web/websearch.h"
#include "expleague/answersfolder.h"
#include "editor.h"

namespace expleague {

const QDir CONTEXT_DIR = QDir(QStandardPaths::writableLocation(QStandardPaths::AppDataLocation) + "/doSearch/");

StateSaver::StateSaver(QObject *parent): QObject(parent) {
    m_settings = new QSettings(this);
}

void StateSaver::saveProfiles() {
    QList<Profile*> profiles = Profile::list();
//    qDebug() << "Saving "<< profiles.size() << " profiles";
    QSettings settings(m_settings->scope(), m_settings->organizationName(), m_settings->applicationName());
    settings.beginGroup("league");
    settings.beginGroup("profiles");
    foreach(const Profile* profile, profiles) {
        settings.beginGroup(profile->deviceJid());
        settings.setValue("domain", profile->domain());
        settings.setValue("login", profile->login());
        settings.setValue("password", profile->passwd());
        settings.setValue("name", profile->name());
        settings.setValue("avatar", profile->avatar().toString());
        settings.setValue("sex", QString::number(int(profile->sex())));
        settings.endGroup();
    }
}

void StateSaver::restoreState(doSearch* search) {
//    qDebug() << "Active profile changed";
    QList<Profile*>& profiles = Profile::list();
    QSettings settings(m_settings->scope(), m_settings->organizationName(), m_settings->applicationName());

    {
        settings.beginGroup("league");
        {
            settings.beginGroup("profiles");
            foreach(const QString& name, settings.childGroups()) {
                settings.beginGroup(name);
                profiles.append(
                            new Profile(settings.value("domain", "expleague.com").toString(),
                                        settings.value("login", "expert").toString(),
                                        settings.value("password", "").toString(),
                                        settings.value("name", tr("Неизвестный Эксперт")).toString(),
                                        QUrl(settings.value("avatar", "qrc:/avatar.png").toString()),
                                        (Profile::Sex)settings.value("sex", "0").toInt())
                            );

                settings.endGroup();
            }
            settings.endGroup();
        }

        qDebug() << profiles;
        QString activeProfileName = settings.value("profile", profiles.length() > 0 ? profiles.at(0)->deviceJid() : "").toString();
        foreach (const Profile* profile, profiles) {
            if (profile->deviceJid() == activeProfileName) {
                search->league()->setActive(const_cast<Profile*>(profile));
            }
        }
        settings.endGroup();
    }
    QObject::connect(search, SIGNAL(contextsChanged()), SLOT(saveContexts()));
    QObject::connect(search->league(), SIGNAL(profileChanged(Profile*)), this, SLOT(profileChanged(Profile*)));
    QObject::connect(search->league(), SIGNAL(profilesChanged()), this, SLOT(saveProfiles()));

    {
        Context* context = new Context("Главный контекст", search);
        search->append(context);
        context->setActive(true);
    }
}

void StateSaver::save(Context* context, QSettings* settings) {
    if (!m_connected.contains(context))
        restoreContext(context);
    QDir contextDataDir = CONTEXT_DIR;
    if (context->task())
        FileWriteThrottle::enqueue({contextDataDir.absoluteFilePath(context->task()->id() + "answer.txt"), context->task()->answer()});

    settings->setValue("active", context->m_folders.indexOf(context->folder()));
    settings->beginWriteArray("folder");
    for(int f = 0; f < context->m_folders.size(); f++) {
        settings->setArrayIndex(f);
        Folder* folder = context->m_folders[f];
        save(folder, settings);
    }
    settings->endArray();
}

void StateSaver::restoreContext(Context* context) {
//    qDebug() << "Restoring context: " << context->id();
    if (!m_connected.contains(context)) {
        m_connected.insert(context);
        QObject::connect(context, SIGNAL(foldersChanged()), this, SLOT(saveFolders()));
        QObject::connect(context, SIGNAL(folderChanged(Folder*)), this, SLOT(activeFolderChanged(Folder*)));
        if (context->task())
            QObject::connect(context->task(), SIGNAL(answerChanged(QString)), this, SLOT(answerChanged(QString)));
    }
    QDir contextDataDir = CONTEXT_DIR;
    contextDataDir.cd(context->id());
    if (context->task()) {
//        qDebug() << "Loading answer from " << contextDataDir.absoluteFilePath("answer.txt");
        if (contextDataDir.exists("answer.txt")) {
            QFile answer(contextDataDir.absoluteFilePath("answer.txt"));
            if (answer.open(QFile::ReadOnly)) {
                QTextStream answerStream(&answer);
                QString answerText = answerStream.readAll();
                context->task()->answerReset(answerText);
            }
        }
    }
    QSettings settings(m_settings->scope(), m_settings->organizationName(), m_settings->applicationName());
    settings.beginGroup("contexts");
    settings.beginGroup(context->id());
    int active = settings.value("active").toInt();
    int count = settings.beginReadArray("folder");
//    qDebug() << " contains " << count << " folder(s). " << active << " is active";
    for (int f = 0; f < count; f++) {
        settings.setArrayIndex(f);
        Folder* folder = loadFolder(context, &settings);
        if (folder) {
            context->append(folder);
            if (f == active)
                folder->setActive(true);
        }
    }
    settings.endArray();
}

void StateSaver::save(Folder* folder, QSettings* settings) {
    WebFolder* webf = qobject_cast<WebFolder*>(folder);

    if (!m_connected.contains(folder)) {
        m_connected.insert(folder);
        QObject::connect(folder, SIGNAL(screensChanged()), this, SLOT(saveScreens()));
        QObject::connect(folder, SIGNAL(screenChanged(Screen*)), this, SLOT(activeScreenChanged(Screen*)));
        if (webf)
            QObject::connect(webf, SIGNAL(requestsChanged()), this, SLOT(saveRequests()));
    }
    if (webf) {
        settings->setValue("type", "web");
        settings->beginWriteArray("request");
        QList<SearchRequest*> searches = webf->requests();
        for (int q = 0; q < searches.size(); q++) {
            settings->setArrayIndex(q);
            settings->setValue("text", searches[q]->query());
            settings->setValue("clicks", searches[q]->clicks());
        }
        settings->endArray();
    }
    else if (qobject_cast<AnswersFolder*>(folder)) {
        settings->setValue("type", "answers");
        return;
    }

    settings->setValue("active", folder->m_screens.indexOf(folder->screen()));
    settings->beginWriteArray("screen");
    for (int s = 0; s < folder->m_screens.size(); s++) {
        Screen* screen = folder->m_screens[s];
        settings->setArrayIndex(s);
        save(screen, settings);
    }
    settings->endArray();
}

Folder* StateSaver::loadFolder(Context* parent, QSettings* settings) {
    Folder* result = 0;
    WebFolder* webResult = 0;
    QString type = settings->value("type").toString();
    qDebug() << "Loading '" + type + "' folder";
    if (type == "web") {
        webResult = new WebFolder(parent);
        WebSearch* search = qobject_cast<WebSearch*>(webResult->m_screens[0]);
        int count = settings->beginReadArray("request");
        for (int q = 0; q < count; q++) {
            settings->setArrayIndex(q);
            search->append(settings->value("text").toString(), settings->value("clicks").toInt());
        }
        settings->endArray();
        result = webResult;
    }
    else {
        return 0;
    }

    int active = settings->value("active").toInt();
    int count = settings->beginReadArray("screen");
    qDebug() << " contains " << count << " screen(s). " << active << " is active";
    for (int s = 0; s < count; s++) {
        settings->setArrayIndex(s);
        Screen* const screen = StateSaver::loadScreen(result, settings);
        if (screen) {
            result->append(screen);
            if (active == s)
                screen->setActive(true);
        }
    }
    settings->endArray();
    if (!m_connected.contains(result)) {
        m_connected.insert(result);
        QObject::connect(result, SIGNAL(screensChanged()), this, SLOT(saveScreens()));
        QObject::connect(result, SIGNAL(screenChanged(Screen*)), this, SLOT(activeScreenChanged(Screen*)));
        if (webResult)
            QObject::connect(webResult, SIGNAL(requestsChanged()), this, SLOT(saveRequests()));
    }

    return result;
}

void StateSaver::save(Screen* screen, QSettings* settings) {
    if (!m_connected.contains(screen)) {
        m_connected.insert(screen);
        QObject::connect(screen, SIGNAL(locationChanged(QString)), this, SLOT(locationChanged(QString)));
    }
    if (qobject_cast<WebScreen*>(screen)) {
        settings->setValue("type", "web");
        settings->setValue("location", screen->location());
    }
    else if (qobject_cast<WebSearch*>(screen)) {
        settings->setValue("type", "search");
    }
    else if (qobject_cast<MarkdownEditorScreen*>(screen)) {
        settings->setValue("type", "editor");
    }
}

Screen* StateSaver::loadScreen(Folder* parent, QSettings* settings) {
    QString type = settings->value("type").toString();
    qDebug() << "Loading screen of type " << type;
    if (type == "web") {
        WebScreen* web = new WebScreen(parent);
        web->handleOmniboxInput(settings->value("location").toString());
        if (!m_connected.contains(web)) {
            m_connected.insert(web);
            QObject::connect(web, SIGNAL(locationChanged(QString)), this, SLOT(locationChanged(QString)));
        }
        return web;
    }
    return 0;
}

void StateSaver::profileChanged(Profile *profile) {
    if (profile)
        m_settings->setValue("league/profile", profile->deviceJid());
    else
        m_settings->remove("league/profile");
}

void StateSaver::saveContexts() {
    QSettings settings(m_settings->scope(), m_settings->organizationName(), m_settings->applicationName());
    doSearch* root = qobject_cast<doSearch*>(sender());
    settings.beginGroup("contexts");
    foreach (Context* context, root->m_contexts) {
        if (m_connected.contains(context))
            continue;
        settings.beginGroup(context->id());
        save(context, &settings);
        settings.endGroup();
    }
    settings.endGroup();
}


void StateSaver::contextChanged(Context* context) {
    m_settings->setValue("active", context->id());
}

void StateSaver::saveFolders() {
    Context* context = qobject_cast<Context*>(sender());
    QSettings settings(m_settings->scope(), m_settings->organizationName(), m_settings->applicationName());
    settings.beginGroup("contexts");
    settings.beginGroup(context->id());
    settings.beginWriteArray("folder");
    for(int f = 0; f < context->m_folders.size(); f++) {
        Folder* folder = context->m_folders[f];
        settings.setArrayIndex(f);
        save(folder, &settings);
    }
    settings.endArray();
}

void StateSaver::activeFolderChanged(Folder* folder) {
    Context* context = qobject_cast<Context*>(sender());
    QSettings settings(m_settings->scope(), m_settings->organizationName(), m_settings->applicationName());
    settings.beginGroup("contexts");
    settings.beginGroup(context->id());
    settings.setValue("active", context->m_folders.indexOf(folder));
}

void StateSaver::saveRequests() {
    WebFolder* folder = qobject_cast<WebFolder*>(sender());
    Context* context = folder->parent();
    QSettings settings(m_settings->scope(), m_settings->organizationName(), m_settings->applicationName());

    settings.beginGroup("contexts");
    settings.beginGroup(context->id());
    settings.beginReadArray("folder");
    settings.setArrayIndex(context->m_folders.indexOf(folder));
    settings.beginWriteArray("request");
    QList<SearchRequest*> searches = folder->requests();
    for (int q = 0; q < searches.size(); q++) {
        settings.setArrayIndex(q);
        settings.setValue("text", searches[q]->query());
        settings.setValue("clicks", searches[q]->clicks());
    }
    settings.endArray();
}

void StateSaver::saveScreens() {
    WebFolder* folder = qobject_cast<WebFolder*>(sender());
    Context* context = folder->parent();
    QSettings settings(m_settings->scope(), m_settings->organizationName(), m_settings->applicationName());

    settings.beginGroup("contexts");
    settings.beginGroup(context->id());
    settings.beginReadArray("folder");
    settings.setArrayIndex(context->m_folders.indexOf(folder));
    settings.beginWriteArray("screen");
    for (int s = 0; s < folder->m_screens.size(); s++) {
        Screen* screen = folder->m_screens[s];
        settings.setArrayIndex(s);
        save(screen, &settings);
    }
    settings.endArray();
}

void StateSaver::activeScreenChanged(Screen* screen) {
    Folder* folder = qobject_cast<Folder*>(sender());
    Context* context = folder->parent();
    QSettings settings(m_settings->scope(), m_settings->organizationName(), m_settings->applicationName());

    settings.beginGroup("contexts");
    settings.beginGroup(context->id());
    settings.beginReadArray("folder");
    settings.setArrayIndex(context->m_folders.indexOf(folder));
    settings.setValue("active", folder->m_screens.indexOf(screen));
}

void StateSaver::locationChanged(const QString& location) {
    Screen* screen = qobject_cast<Screen*>(sender());
    Folder* folder = qobject_cast<Folder*>(screen->parent());
    Context* context = qobject_cast<Context*>(folder->parent());
    QSettings settings(m_settings->scope(), m_settings->organizationName(), m_settings->applicationName());
    settings.beginGroup("contexts");
    settings.beginGroup(context->id());
    settings.beginReadArray("folder");
    settings.setArrayIndex(context->m_folders.indexOf(folder));
    settings.beginReadArray("screen");
    settings.setArrayIndex(folder->m_screens.indexOf(screen));
//    qDebug() << "Saving location " << location;
    settings.setValue("location", location);
}

void StateSaver::answerChanged(const QString& answer) {
//    qDebug() << "Saving answer " << answer;
    Task* task = qobject_cast<Task*>(sender());
    FileWriteThrottle::enqueue({CONTEXT_DIR.absoluteFilePath(task->id() + "/answer.txt"), answer});
}

}

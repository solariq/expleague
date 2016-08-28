#include "history.h"

#include <QUrl>
#include <QSettings>

#include <QTimer>

#include "../util/filethrottle.h"
#include "../dosearch.h"
#include "../profile.h"

#include "editor.h"

namespace expleague {

void History::onVisited(Page *to) {
    m_story.append(to);
    append("history", to->id());
    save();
    m_last30 = last(30);
    emit historyChanged();
}

int History::visits(const QString& pageId) const {
    Page* const page = parent()->page(pageId);
    int count = 0;
    foreach(Page* current, m_story) {
        count += current == page ? 1 : 0;
    }
    return count;
}


QList<Page*> History::last(int depth) const {
    QList<Page*> result;
    for (int i = m_story.size() - 1; i >= 0 && result.size() < depth; i--) {
        if (!result.contains(m_story[i]))
            result.append(m_story[i]);
    }
    return result;
}

void History::interconnect() {
    Page::interconnect();
    // TODO: make global history available
//    visitAll("history", [this](const QVariant& val) {
//        m_story.append(parent()->page(val.toString()));
//    });
}

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

void StateSaver::restoreState(doSearch* model) {
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

//        qDebug() << profiles;
        QString activeProfileName = settings.value("profile", profiles.length() > 0 ? profiles.at(0)->deviceJid() : "").toString();
        foreach (const Profile* profile, profiles) {
            if (profile->deviceJid() == activeProfileName) {
                model->league()->setActive(const_cast<Profile*>(profile));
            }
        }
        settings.endGroup();
    }
    QObject::connect(model->league(), SIGNAL(profileChanged(Profile*)), this, SLOT(profileChanged(Profile*)));
    QObject::connect(model->league(), SIGNAL(profilesChanged()), this, SLOT(saveProfiles()));
}

void StateSaver::profileChanged(Profile *profile) {
    if (profile)
        m_settings->setValue("league/profile", profile->deviceJid());
    else
        m_settings->remove("league/profile");
}
}

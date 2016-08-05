#include "history.h"

#include <QUrl>
#include <QSettings>

#include <QTimer>

#include "../util/filethrottle.h"
#include "../dosearch.h"
#include "../profile.h"

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

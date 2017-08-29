#include "history.h"

#include <QUrl>
#include <QSettings>

#include <QTimer>

#include <time.h>

#include "../util/filethrottle.h"
#include "../dosearch.h"
#include "../profile.h"

#include "pages/editor.h"
#include "pages/globalchat.h"

namespace expleague {

QVariant PageVisit::toVariant() const {
    QVariantHash result;
    result["page"] = page()->id();
    result["context"] = context()->id();
    result["time"] = (qlonglong)time();
    return result;
}

PageVisit* PageVisit::fromVariant(QVariant var, History *owner) {
    QVariantHash hash = var.toHash();
    Page* page = owner->parent()->page(hash["page"].toString());
    Context* context = qobject_cast<Context*>(owner->parent()->page(hash["context"].toString()));
    time_t ts = hash["time"].toLongLong();
    return new PageVisit(page, context, ts, owner);
}

void History::onVisited(Page *to, Context* context) {
    time_t ts = time(0);
    PageVisit* visit = new PageVisit(to, context, ts, this);
    m_story.append(visit);
    m_cursor = m_story.size() - 1;
    append("history", visit->toVariant());
    save();
    m_last30 = last(30);
    emit historyChanged();
}

int History::visits(const QString& pageId) const {
    Page* const page = parent()->page(pageId);
    int count = 0;
    foreach(PageVisit* current, m_story) {
        count += current->page() == page ? 1 : 0;
    }
    return count;
}

QList<PageVisit*> History::last(int depth) const {
    QSet<Page*> known;
    QList<PageVisit*> result;
    for (int i = m_story.size() - 1; i >= 0; i--) {
        if (known.contains(m_story[i]->page()))
            continue;
        if (qobject_cast<Context*>(m_story[i]->page()))
            continue;
        if (qobject_cast<MarkdownEditorPage*>(m_story[i]->page()))
            continue;
        if (qobject_cast<GlobalChat*>(m_story[i]->page()) &&
            !(League::instance()->connection() && League::instance()->role() == League::ADMIN))
            continue;

        Context* context = m_story[i]->context();
        if (context->hasTask() && !context->task())
            continue;
        if(result.size() >= depth)
            break;
        result.append(m_story[i]);
        known.insert(m_story[i]->page());
    }

    return result;
}

PageVisit* History::last() const{
  if(m_story.isEmpty())
    return nullptr;
  return m_story.last();
}

Page* History::current() const {
    return m_story[m_cursor]->page();
}

void History::interconnect() {
    Page::interconnect();
    visitValues("history", [this](const QVariant& val) {
        m_story.append(PageVisit::fromVariant(val, this));
    });
    m_cursor = m_story.size() - 1;
}

Context* History::recent(Page* page) const {
    for (int i = m_story.size() - 1; i >= 0; i--) {
        if (page == m_story.at(i)->page())
            return m_story.at(i)->context();
    }

    return 0;
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

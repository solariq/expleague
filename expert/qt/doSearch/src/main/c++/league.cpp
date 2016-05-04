
#include "league.h"
#include "dosearch.h"
#include "profile.h"
#include "protocol.h"

namespace expleague {
doSearch* League::parent() const {
    return qobject_cast<doSearch*>(QObject::parent());
}

void League::setActive(Profile *profile) {
    if (m_connection) {
        m_connection->disconnect();
        delete m_connection;
    }
    if (profile) {
//        qDebug() << "Activating " << profile->deviceJid();
        m_connection = new xmpp::ExpLeagueConnection(profile, this);
        QObject::connect(m_connection, SIGNAL(connected()), SLOT(connected()));
        QObject::connect(m_connection, SIGNAL(disconnected()), SLOT(disconnected()));
        QObject::connect(m_connection, SIGNAL(receiveAnswer(QString)), SLOT(checkReceived()));
        QObject::connect(m_connection, SIGNAL(receiveCheck(Offer*)), SLOT(checkReceived()));
        QObject::connect(m_connection, SIGNAL(receiveInvite(Offer*)), SLOT(inviteReceived(Offer*)));
        QObject::connect(m_connection, SIGNAL(receiveResume(Offer*)), SLOT(resumeReceived(Offer*)));
        QObject::connect(m_connection, SIGNAL(receiveCancel()), SLOT(cancelReceived()));
    }
    profileChanged(profile);
}

void League::resumeReceived(Offer* offer) {
    m_task = new Task(offer, this);
    taskChanged(m_task);
    m_status = LS_ON_TASK;
    statusChanged(m_status);
//    qDebug() << "Resume received: " << offer->toXml();
    Context* context = new Context(offer, parent());
    parent()->append(context);
    context->setActive(true);
}
}

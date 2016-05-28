#include "filethrottle.h"

#include <QMutex>
#include <QTimer>
#include <QFile>
#include <QFileInfo>
#include <QDir>
#include <QTextStream>
#include <QDebug>

#include "call_once.h"

FileWriteThrottle* globalQueue;
QBasicAtomicInt flag;
void createGlobalQueue() {
    globalQueue = new FileWriteThrottle;
}

void FileWriteThrottle::enqueue(const FileWriteRequest& req) {
    qCallOnce(createGlobalQueue, flag);
    globalQueue->append(req);
}

void FileWriteThrottle::append(const FileWriteRequest& req) {
    QMutexLocker locker(m_lock);
    m_requests.append(req);
}

FileWriteThrottle::FileWriteThrottle(QObject* parent): QObject(parent), m_lock(new QMutex()), m_timer(new QTimer(this)) {
    m_progress = false;
    m_timer->setInterval(1000);
    m_timer->setTimerType(Qt::TimerType::CoarseTimer);
    QObject::connect(m_timer, SIGNAL(timeout()), this, SLOT(tick()));
//    qDebug() << "Starting file throttling timer";
    m_timer->start();
}

void FileWriteThrottle::tick() {
    if (m_progress)
        return;
//    qDebug() << "FWT tick";
    m_progress = true;
    while(true) {
        FileWriteRequest request;
        {
            QMutexLocker locker(m_lock);
            if (m_requests.empty())
                break;

            request = m_requests.takeFirst();
            for (QList<FileWriteRequest>::iterator iter = m_requests.begin(); iter != m_requests.end();) {
                if (iter->file == request.file) {
                    request.content = iter->content;
                    iter = m_requests.erase(iter);
                }
                else iter++;
            }
        }
        QFile file(request.file);
//        qDebug() << "Writing file contents to " << request.file;
        if (!file.exists()) {
            QFileInfo info(file);
            if (!info.dir().exists())
                info.dir().mkpath(".");
        }

        if (file.open(QFile::WriteOnly | QFile::Truncate)) {
            QTextStream answerStream(&file);
            answerStream << request.content;
        }
    }
    m_progress = false;
}

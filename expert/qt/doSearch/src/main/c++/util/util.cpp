#include "filethrottle.h"

#include <math.h>

#include <QMutex>
#include <QTimer>
#include <QThread>
#include <QFile>
#include <QFileInfo>
#include <QDir>
#include <QDataStream>
#include <QDebug>

#include "call_once.h"

FileWriteThrottle* globalQueue;
QBasicAtomicInt flag;
void createGlobalQueue() {
    globalQueue = new FileWriteThrottle;
}

void FileWriteThrottle::enqueue(const QString& file, const QByteArray& content, std::function<void ()> callback) {
    qCallOnce(createGlobalQueue, flag);
    globalQueue->append({file, content, callback});
}

void FileWriteThrottle::enqueue(const QString& file, const QString& content, std::function<void ()> callback) {
    enqueue(file, content.toUtf8(), callback);
}

void FileWriteThrottle::append(const FileWriteRequest& req) {
    QMutexLocker locker(m_lock);
    m_requests.append(req);
}

FileWriteThrottle::FileWriteThrottle(QObject* parent): QObject(parent), m_lock(new QMutex()), m_timer(new QTimer()), m_thread(new QThread(this)) {
    m_progress = false;
    m_thread->setObjectName("FileWriteThrottle");
    m_timer->moveToThread(m_thread);
    m_timer->setInterval(1000);
    m_timer->setTimerType(Qt::TimerType::CoarseTimer);
    connect(m_timer, &QTimer::timeout, [this](){
        this->tick();
    });
    connect(m_thread, &QObject::destroyed, m_timer, &QTimer::deleteLater);
    connect(m_thread, SIGNAL(started()), m_timer, SLOT(start()));
    m_thread->start(QThread::LowPriority);
}

FileWriteThrottle::~FileWriteThrottle() {
    m_timer->stop();
    m_thread->exit();
    m_thread->wait();
    if (!m_requests.empty())
      tick();
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
            file.write(request.content);
            file.flush();
            if (request.callback)
                request.callback();
        }
        else
            qWarning() << "Unable to write contents of file: " << request.file;
    }
    m_progress = false;
}

const double EPSILON = 1e-6;

double bisection(double left, double right, std::function<double (double)> func) {
    const double fLeft = func(left);
    if (fabs(fLeft) < EPSILON)
        return left;
    const double fRight = func(right);
    if (fabs(fRight) < EPSILON)
        return right;

    if (fLeft * fRight > 0) {
        qWarning() << "Function values for left and right parameters should lay on different sides of 0";
        return nan("");
    }

    const double middle = (left + right) / 2.;
    const double fMiddle = func(middle);
    if (fLeft * fMiddle > 0)
        return bisection(middle, right, func);
    else
        return bisection(left, middle, func);
}

double optimalExpansionDP(double statPower, int classes) {
    if (statPower <= classes)
        return std::numeric_limits<double>::infinity();
    return bisection(0, 2 * classes, [statPower, classes](double x) {
        return x == 0.0 ? -classes : x * log(1 + statPower / x) - classes;
    });
}

double erlang(int k, double lambda, double x) {
    const double nom = exp(log(lambda)* k + log(x) * (k - 1) - lambda * x - lgamma(k));
    return nom;
}

#ifndef FILETHROTTLE_H
#define FILETHROTTLE_H

#include <atomic>
#include <QObject>

struct FileWriteRequest {
    QString file;
    QString content;
};

class QTimer;
class QMutex;
class FileWriteThrottle: public QObject {
    Q_OBJECT

public:
    static void enqueue(const FileWriteRequest& req);

private slots:
    void tick();

public:
    explicit FileWriteThrottle(QObject* parent = 0);
    void append(const FileWriteRequest& req);

private:
    QMutex* m_lock;
    QTimer* m_timer;
    std::atomic<bool> m_progress;
    QList<FileWriteRequest> m_requests;
};

#endif // FILETHROTTLE_H

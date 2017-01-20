#ifndef FILETHROTTLE_H
#define FILETHROTTLE_H

#include <functional>
#include <atomic>
#include <QObject>

struct FileWriteRequest {
    QString file;
    QByteArray content;
    std::function<void ()> callback;
};

class QTimer;
class QMutex;
class QThread;
class FileWriteThrottle: public QObject {
    Q_OBJECT

public:
    static void enqueue(const QString& file, const QByteArray& content, std::function<void ()> callback = 0);
    static void enqueue(const QString& file, const QString& content, std::function<void ()> callback = 0);

private slots:
    void tick();

public:
    explicit FileWriteThrottle(QObject* parent = 0);
    virtual ~FileWriteThrottle();

    void append(const FileWriteRequest& req);

private:
    QMutex* m_lock;
    QTimer* m_timer;
    QThread* m_thread;
    std::atomic<bool> m_progress;
    QList<FileWriteRequest> m_requests;
};

#endif // FILETHROTTLE_H

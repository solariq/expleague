#ifndef FILETHROTTLE_H
#define FILETHROTTLE_H

#include <atomic>
#include <QObject>
#include <QFile>

struct FileWriteRequest {
    QString file;
    QByteArray content;
    std::function<void ()> callback;
};

class QTimer;
class QMutex;
class FileWriteThrottle: public QObject {
    Q_OBJECT

public:
    static void enqueue(const QString& file, const QByteArray& content, std::function<void ()> callback = 0);
    static void enqueue(const QString& file, const QString& content, std::function<void ()> callback = 0);

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

#include "league.h"

#include <QQueue>
#include <QBuffer>

#include <QThread>
#include <QMutex>
#include <QMutexLocker>
#include <QWaitCondition>
#include <QTimer>

#include <QNetworkAccessManager>
#include <QNetworkRequest>
#include <QNetworkReply>

#include <QImageReader>
#include <QFile>

#include "util/mmath.h"
#include "dosearch.h"

namespace expleague {

class ImagesStorePrivate : public QThread {
public:
  static const QString IMAGE_STORE_MAGIC;
  static const QDir CACHE_ROOT;

  void enqueue(ImagesStoreResponse* task) {
    QMutexLocker lock(&m_lock);
    m_queue.append(task);
    m_condition.wakeOne();
  }

  void run() {
    forever {
      ImagesStoreResponse* task;
      {
        QMutexLocker lock(&m_lock);
        while (m_queue.empty()) {
          m_condition.wait(&m_lock);
        }
        task = m_queue.takeFirst();
      }
//            qDebug() << "Request for " << task->id() << " received";
      QFile cacheFile(m_ava_cache_dir.filePath(task->id()));
      if (!cacheFile.exists()) {
        m_lock.lock();
        if (!m_pending.contains(task->id())) {
          m_lock.unlock();
          emit m_facade->requestImageById(task->id());
          m_lock.lock();
        }
        m_pending.insert(task->id(), task);
        QString id = task->id();
        QObject::connect(task, &QObject::destroyed, [this, id, task]() {
          QMutexLocker locker(&m_lock);
          m_pending.remove(id, task);
        });
        m_lock.unlock();
      } else {
//                qDebug() << "Cache hit on " << task->id() << " file: " << cacheFile.fileName();
        QImageReader reader(&cacheFile);
        reader.setAutoTransform(true);

        task->setResult(reader.read());
      }
    }
  }

  void sendRequest(const QString& id) {
    QMutexLocker locker(&m_lock);
//        qDebug() << "Sending images store request " << id;
    QNetworkRequest request(QUrl(baseUrl() + id));

    auto existing = m_pending.find(id);
    if (existing != m_pending.end())
      request.setOriginatingObject(existing.value());
    m_replies.append(m_nam->get(request));
  }

  void domain(const QString& domain) {
    {
      QMutexLocker locker(&m_lock);
              foreach(QNetworkReply* reply, m_replies) {
          reply->abort();
        }
      m_replies.clear();
    }
    m_domain = domain;
    CACHE_ROOT.mkpath(domain);
    m_ava_cache_dir.setPath(CACHE_ROOT.absoluteFilePath(domain));
    {
              foreach(QString id, m_pending.keys()) {
          emit m_facade->requestImageById(id);
        }
    }
  }

  QUrl upload(const QImage& img) {
    QByteArray requestBodyData;
    QByteArray imageId = randString(10).toLatin1() + ".png";
    QByteArray boundary = randString(20).toLatin1();

    requestBodyData.append("--" + boundary + "\r\n");
    requestBodyData.append("Content-Disposition: form-data; name=\"id\"\r\n\r\n" + imageId + "\r\n");
    requestBodyData.append("--" + boundary + "\r\n");
    requestBodyData.append("Content-Disposition: form-data; name=\"image\"; filename=\"" + imageId + "\"\r\n");
    requestBodyData.append("Content-Type: image/jpeg\r\n\r\n");
    QByteArray imageBuffer;
    QBuffer buffer(&imageBuffer);
    img.save(&buffer, "PNG");
    requestBodyData.append(imageBuffer);
    requestBodyData.append("\r\n");
    requestBodyData.append("--" + boundary + "--\r\n");
    QNetworkRequest request((QUrl) baseUrl());
    request.setHeader(QNetworkRequest::ContentTypeHeader, "multipart/form-data; boundary=" + boundary);
    request.setHeader(QNetworkRequest::ContentLengthHeader, QByteArray::number(requestBodyData.length()));
    QNetworkReply* reply = m_nam->post(request, requestBodyData); //TODO delete
    QObject::connect(reply, &QNetworkReply::finished, [reply](){
      qDebug() << "upload finished";
      reply->deleteLater();
    });
    qDebug() << "uploading image" << baseUrl() + imageId;
    return QUrl(baseUrl() + imageId);
  }

  QString baseUrl() const {
    if (m_domain == "localhost")
      return "http://localhost:8067/";
    else
      return "https://img." + m_domain + "/" + IMAGE_STORE_MAGIC + "/";
  }

  QString cachePath(const QString& id) const {
    return m_ava_cache_dir.filePath(id);
  }

public:
  void requestFinished(QNetworkReply* reply) {
    ImagesStoreResponse* firstPending = dynamic_cast<ImagesStoreResponse*>(reply->request().originatingObject());
    if (!firstPending)
      return;
//        qDebug() << "Web request finished for " << firstPending->id();
    QByteArray content = reply->readAll();
    QBuffer buffer(&content);
    QImageReader reader(&buffer);
    reader.setAutoTransform(true);
    QImage img = reader.read();
    if (!img.isNull()) {
      QString id = firstPending->id();
//            qDebug() << "Saving image to " << m_ava_cache_dir.filePath(id);
      img.save(m_ava_cache_dir.filePath(id));
      {
        QMutexLocker locker(&m_lock);
                foreach (ImagesStoreResponse* response, m_pending.values(id)) {
            response->setResult(img);
          }
        m_pending.remove(id);
      }
    } else if (firstPending->needRetry()) { // retry
      QTimer::singleShot(5000, this, [this, firstPending]() {
//                qDebug() << "Retry";
        sendRequest(firstPending->id());
      });
    }
    {
      QMutexLocker locker(&m_lock);
      m_replies.removeOne(reply);
    }
  }

public:
  explicit ImagesStorePrivate(ImagesStore* facade, QNetworkAccessManager* nam) : m_facade(facade), m_nam(nam) {
    this->setObjectName("Image store thread");
    QObject::connect(m_nam, SIGNAL(finished(QNetworkReply*)), facade, SLOT(requestFinished(QNetworkReply*)));
    start();
  }

  ~ImagesStorePrivate() {
    terminate();
    wait();
  }

private:
  QMultiMap<QString, ImagesStoreResponse*> m_pending;
  QList<QNetworkReply*> m_replies;
  QMutex m_lock;
  ImagesStore* m_facade;

  QQueue<ImagesStoreResponse*> m_queue;
  QWaitCondition m_condition;

  QString m_domain;
  QDir m_ava_cache_dir;

  QNetworkAccessManager* m_nam;
};

const QString ImagesStorePrivate::IMAGE_STORE_MAGIC = "OSYpRdXPNGZgRvsY";
const QDir ImagesStorePrivate::CACHE_ROOT = QDir(
        QStandardPaths::writableLocation(QStandardPaths::CacheLocation) + "/doSearch/images/");

void ImagesStore::requestFinished(QNetworkReply* reply) {
  m_instance->requestFinished(reply);
}

ImagesStore::ImagesStore(League* parent) : QObject(parent) {
  m_instance = new ImagesStorePrivate(this, parent->parent()->sharedNAM());
  QObject::connect(parent, SIGNAL(profileChanged(Profile * )), this, SLOT(profileChanged(Profile * )));
  QObject::connect(this, SIGNAL(requestImageById(QString)), this, SLOT(imageRequested(QString)));
}

ImagesStore::~ImagesStore() {
  delete m_instance;
}

void ImagesStore::imageRequested(const QString& id) {
  m_instance->sendRequest(id);
}

void ImagesStore::profileChanged(Profile* profile) {
  m_instance->domain(profile->domain());
}

QUrl ImagesStore::upload(const QImage& image) const {
  return m_instance->upload(image);
}

QUrl ImagesStore::url(const QString& id) const {
  return QUrl("file:" + m_instance->cachePath(id));
}

QQuickImageResponse* ImagesStore::requestImageResponse(const QString& id, const QSize&) {
//    qDebug() << "Image requested: " << id;
  ImagesStoreResponse* response = new ImagesStoreResponse(id);
  QTimer::singleShot(10, [this, response]() {
    m_instance->enqueue(response);
  });
  return response;
}

QUrl League::normalizeImageUrlForUI(const QUrl& imageUrl) const {
  if (imageUrl.path().startsWith("/" + ImagesStorePrivate::IMAGE_STORE_MAGIC + "/")) {
    QString imageId = imageUrl.path().section('/', -1);
    return "image://store/" + imageId;
  }
  return imageUrl;
}
}

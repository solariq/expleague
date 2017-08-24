#ifndef DOWNDLOADS_H
#define DOWNDLOADS_H

#include <QtCore>
#include <QNetworkAccessManager>
#include <QNetworkRequest>
#include <QNetworkReply>
#include "util/pholder.h"


namespace expleague {
class Download : public QObject, public PersistentPropertyHolder {
Q_OBJECT
public:
  enum Status {
    NOT_STARTED = 0,
    DOWNLOADING,
    CANCELED,
    DELETED,
    FINISHED
  };
  Q_ENUMS(Status)

  Q_PROPERTY(qint64 receivedBytes READ receivedBytes NOTIFY receivedBytesChanged)
  Q_PROPERTY(qint64 totalBytes READ totalBytes NOTIFY totalBytesChanged)
  Q_PROPERTY(int id READ id CONSTANT)
  Q_PROPERTY(QString path READ path  CONSTANT)
  Q_PROPERTY(QString fullName READ fullName CONSTANT)
  Q_PROPERTY(Status status READ status NOTIFY statusChanged)

  Download(const QUrl &url, const QString &path, const QString& name, int id = max_id + 1);
  Download(int id);

  qint64 receivedBytes();
  qint64 totalBytes();
  int id();
  QString path();
  QString fullName();
  Status status();

  Q_INVOKABLE void cancel();
  Q_INVOKABLE void start();
  Q_INVOKABLE void open();

  void download(QUrl url, QString path, std::function<void()> callback);

signals:
  void receivedBytesChanged();
  void totalBytesChanged();
  void statusChanged();
  void completed();
  void failed();

private slots:
  void readyRead();
  void finished();
  void progress(qint64 bytesReceived, qint64 bytesTotal);

private:
  void setStatus(Status status);
  void setReceivedBytes(int recieved_bytes);
  void setTotalBytes(int total_bytes);

  int m_id;
  qint64 m_recieved_bytes;
  qint64 m_total_bytes;
  QString m_file_name;
  QString m_path;
  QUrl m_url;
  QFile m_file;
  Status m_status = NOT_STARTED;
  QNetworkReply* m_reply;
  static int max_id;
};

class DownloadManager: public QAbstractListModel{
  Q_OBJECT
public:
  QVariant data(const QModelIndex &index, int role = Qt::DisplayRole) const override;
  int rowCount(const QModelIndex &parent = QModelIndex()) const override;
  QHash<int, QByteArray> roleNames() const override;

  Q_INVOKABLE void addDownload(Download *download);
  Q_INVOKABLE void removeDownload(Download * download);

  DownloadManager(QObject* parent = nullptr);

private:
  QList<Download*> m_downloads;
};


}


#endif // DOWNDLOADS_H

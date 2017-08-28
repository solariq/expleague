#include "downloads.h"

#include "dosearch.h"
#include <QDesktopServices>

namespace expleague {
int Download::max_id = 0;

Download::Download(const QUrl &url, const QString &path, const QString& name, int id):
  PersistentPropertyHolder("downloads/" + QString::number(id)),
  m_id(id), m_path(path), m_url(url), m_file_name(name)
{
  max_id = std::max(id, max_id);
  store("path", m_path);
  store("url", m_url);
  store("file_name", m_file_name);
  store("status", m_status);
  save();
}

Download::Download(int id): PersistentPropertyHolder("downloads/" + QString::number(id)), m_id(id){
  m_path = value("path").toString();
  m_url = value("url").toString();
  m_file_name = value("file_name").toString();
  m_status = (Status)value("status").toInt();
  m_total_bytes = value("size").toInt();
  if(m_status == FINISHED){
    m_recieved_bytes = m_total_bytes;
  }
  if(m_status == DOWNLOADING){
    m_status = CANCELED;
  }
}

void Download::start() {
  qDebug() << "Download Start" << m_file_name;
  QNetworkRequest request(m_url);
  m_reply = doSearch::instance()->sharedNAM()->get(request);
  m_file.setFileName(m_path + '/' + m_file_name);
  m_file.open(QIODevice::WriteOnly);
  QObject::connect(m_reply, SIGNAL(downloadProgress(qint64, qint64)), this, SLOT(progress(qint64, qint64)));
  QObject::connect(m_reply, SIGNAL(readyRead()), this, SLOT(readyRead()));
  QObject::connect(m_reply, SIGNAL(finished()), this, SLOT(finished()));
  setStatus(DOWNLOADING);
}

void Download::open() {
  QDesktopServices::openUrl(QUrl("file:///" + fullName(), QUrl::TolerantMode));
}

qint64 Download::receivedBytes() {
  return m_recieved_bytes;
}

qint64 Download::totalBytes() {
  return m_total_bytes;
}

int Download::id() {
  return m_id;
}

void Download::cancel() {
  if (status() != DOWNLOADING) {
    return;
  }
  m_reply->abort();
  m_file.close();
  m_file.remove();
  setStatus(CANCELED);
}

QString Download::path() {
  return m_path;
}

QString Download::fullName() {
  return m_path + '/' + m_file_name;
}

Download::Status Download::status() {
  return m_status;
}

void Download::setReceivedBytes(int recieved_bytes) {
  m_recieved_bytes = recieved_bytes;
  emit receivedBytesChanged();
}

void Download::setTotalBytes(int total_bytes) {
  m_total_bytes = total_bytes;
  store("size", QVariant(m_total_bytes));
  save();
  emit totalBytesChanged();
}

void Download::progress(qint64 bytesReceived, qint64 bytesTotal) {
  if (bytesReceived != m_recieved_bytes) {
    m_recieved_bytes = bytesReceived;
    emit receivedBytesChanged();
  }
  if (bytesTotal != m_total_bytes) {
    setTotalBytes(bytesTotal);
  }
  qDebug() << m_recieved_bytes << "downloaded of" << m_total_bytes;
}

void Download::readyRead() {
  m_file.write(m_reply->readAll());
}

void Download::finished() {
  qDebug() << "finished";
  m_file.write(m_reply->readAll());
  m_file.close();
  m_reply->deleteLater();
  setStatus(FINISHED);
  emit completed();
}

void Download::setStatus(Status status) {
  store("status", QVariant(status));
  save();
  m_status = status;

  emit statusChanged();
}

DownloadManager::DownloadManager(QObject* parent): QAbstractListModel(parent){
  PersistentPropertyHolder holder("downloads");
  QList<int> ids;
  holder.visitKeys("", [&ids](const QString& id){
    bool ok;
    int i = id.toInt(&ok);
    if(ok)
      ids.append(i);
  });
  for(int id: ids){
    m_downloads.append(new Download(id));
  }
  emit layoutChanged();
}

QVariant DownloadManager::data(const QModelIndex &index, int role) const{
  if(index.row() < 0 || index.row() >= m_downloads.size()){
    return QVariant();
  }
  return qVariantFromValue(m_downloads.at(index.row()));
}

int DownloadManager::rowCount(const QModelIndex &parent) const{
  if (parent.isValid())
    return 0;
  return m_downloads.size();
}

QHash<int, QByteArray> DownloadManager::roleNames() const {
    QHash<int, QByteArray> roles;
    roles[Qt::UserRole + 1] = "modelData";
    return roles;
}

void DownloadManager::addDownload(Download *download){
  if(!download)
    return;
  if(download->status() == Download::NOT_STARTED)
    download->start();
  download->setParent(this);
  int row = m_downloads.size();
  beginInsertRows(QModelIndex(), row, row);
  m_downloads.push_back(download);
  endInsertRows();
}


void DownloadManager::removeDownload(Download * download){
  download->cancel();
  download->deleteLater();
  download->remove("");
  int row = m_downloads.size() - 1;
  beginRemoveRows(QModelIndex(), row, row);
  m_downloads.removeOne(download);
  endRemoveRows();
}

}

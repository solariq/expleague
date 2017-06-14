#include "downloads.h"
#include <QDesktopServices>

int Download::max_id = 0;

Download::Download(const QUrl& url, const QString& path, int id): m_id(id), m_url(url), m_path(path){
     m_file_name = url.fileName();
    max_id = std::max(id, max_id);
}

void Download::start(){
    qDebug() << "Download Start" << m_file_name;
    QNetworkRequest request(m_url);
    m_reply = m_network.get(request);
    m_file.setFileName(m_path + '/' + m_file_name);
    m_file.open(QIODevice::WriteOnly);
    QObject::connect(m_reply, SIGNAL(downloadProgress(qint64,qint64)), this, SLOT(progress(qint64,qint64)));
    QObject::connect(m_reply, SIGNAL(readyRead()), this, SLOT(readyRead()));
    QObject::connect(m_reply, SIGNAL(finished()), this, SLOT(finished()));
    setStatus(DOWNLOADING);
}

void Download::open(){
    QDesktopServices::openUrl(QUrl("file:///" + fullName(), QUrl::TolerantMode));
}

qint64 Download::receivedBytes(){
    return m_recieved_bytes;
}

qint64 Download::totalBytes(){
    return m_total_bytes;
}

int Download::id(){
    return m_id;
}

void Download::cancel(){
    if(status() != DOWNLOADING){
        return;
    }
    m_reply->abort();
    m_file.close();
    m_file.remove();
    setStatus(CANCLELD);
}

QString Download::path(){
    return m_path;
}

QString Download::fullName(){
    return m_path + '/' + m_file_name;
}

Download::Status Download::status(){
    return m_status;
}

void Download::setReceivedBytes(int recieved_bytes){
    m_recieved_bytes = recieved_bytes;
    emit receivedBytesChanged();
}

void Download::setTotalBytes(int total_bytes){
    m_total_bytes = total_bytes;
    emit totalBytesChanged();
}

void Download::progress(qint64 bytesReceived, qint64 bytesTotal){
    if(bytesReceived != m_recieved_bytes){
        m_recieved_bytes = bytesReceived;
        emit receivedBytesChanged();
    }
    if(bytesTotal != m_total_bytes){
        m_total_bytes = bytesTotal;
        emit totalBytesChanged();
    }
    qDebug() << m_recieved_bytes << "downloaded of" << m_total_bytes;
}

void Download::readyRead(){
    m_file.write(m_reply->readAll());
}

void Download::finished(){
    qDebug() << "finished";
    m_file.write(m_reply->readAll());
    m_file.close();
    m_reply->deleteLater();
    setStatus(FINISHED);
    emit completed();
}

void Download::setStatus(Status status){
    m_status = status;
    emit statusChanged();
}

void download(QUrl url, QString path, std::function<void()> callback){
//    Download* down = new Download(url, path);
//    down->start();
//    QObject::connect(down, &Download::completed, [down](){
//        down->deleteLater();
//        callback();
//    });
//    QObject::connect(down, &Download::faild, [down](){
//        down->deleteLater();
//    });
}

#ifndef REGION_H
#define REGION_H

#include <functional>
#include <QNetworkAccessManager>
#include <QGeoCoordinate>

class RegionQuery: public QObject {
    Q_OBJECT
public:
    void process(QNetworkReply* reply);

public:
    RegionQuery(std::function<void (const QString&)> callback, QObject* parent): QObject(parent), m_callback(callback) {}

private:
    std::function<void (const QString&)> m_callback;
};

class RegionResolver: public QObject
{
    Q_OBJECT
public:
    static void resolve(const QGeoCoordinate& coord, std::function<void (const QString&)> callback);

public slots:
    void onFinished(QNetworkReply* request);

public:
    RegionResolver();

private:
    void request(const QGeoCoordinate& coord, std::function<void (const QString&)> callback);

private:
    QNetworkAccessManager* m_nam;
};

#endif // REGION_H

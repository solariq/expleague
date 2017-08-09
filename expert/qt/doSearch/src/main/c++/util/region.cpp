#include "region.h"
#include "call_once.h"

#include <QNetworkRequest>
#include <QNetworkReply>

#include <QBuffer>

#include <QXmlQuery>
#include "../dosearch.h"
static RegionResolver* resolver;
static QBasicAtomicInt flag;

void createResolver() {
    resolver = new RegionResolver;
}

void RegionQuery::process(QNetworkReply* reply) {
    QXmlQuery query;
    QByteArray array = reply->readAll();
    QString contents = QString::fromUtf8(array);
    QBuffer buffer(&array);
    buffer.open(QIODevice::ReadOnly);
    query.bindVariable("reply", &buffer);
//    query.setQuery("doc($reply)/ymaps/GeoObjectCollection/featureMember[0]/GeoObject/metaDataProperty/GeocoderMetaData/text");
    query.setUriResolver(0);
    query.setQuery("declare namespace ym=\"http://maps.yandex.ru/ymaps/1.x\"; "
                   "declare namespace gis=\"http://www.opengis.net/gml\"; "
                   "declare namespace coder=\"http://maps.yandex.ru/geocoder/1.x\"; "
                   "doc($reply)/ym:ymaps/ym:GeoObjectCollection/gis:featureMember[1]/ym:GeoObject/gis:metaDataProperty/coder:GeocoderMetaData/coder:text/text()");
    QString result;
    bool rc = query.evaluateTo(&result);
    if (rc) {
        result = result.section(" ", 0, 2);
        m_callback(result.trimmed());
    }
}

void RegionResolver::resolve(const QGeoCoordinate& coord, std::function<void (const QString&)> callback) {
    qCallOnce(createResolver, flag);
    resolver->request(coord, callback);
}

void RegionResolver::request(const QGeoCoordinate& coord, std::function<void (const QString&)> callback) {
    QNetworkRequest request("https://geocode-maps.yandex.ru/1.x/?geocode=" + QString::number(coord.longitude()) + "," + QString::number(coord.latitude()) + "&kind=locality");
//    qDebug() << "Resolving coords: " << "https://geocode-maps.yandex.ru/1.x/?geocode=" + QString::number(coord.longitude()) + "," + QString::number(coord.latitude()) + "&kind=locality";
    RegionQuery* query = new RegionQuery(callback, this);
    request.setOriginatingObject(query);
    m_nam->get(request);
}

void RegionResolver::onFinished(QNetworkReply* reply) {
    RegionQuery* query = qobject_cast<RegionQuery*>(reply->request().originatingObject());
    query->process(reply);
    query->deleteLater();
}

RegionResolver::RegionResolver(): m_nam(expleague::doSearch::instance()->sharedNAM()) {
    QObject::connect(m_nam, SIGNAL(finished(QNetworkReply*)), this, SLOT(onFinished(QNetworkReply*)));
}

#ifndef MATH_H
#define MATH_H

#include <QString>
#include <QCryptographicHash>

inline QString md5(const QString& str) {
    QCryptographicHash md5(QCryptographicHash::Md5);
    md5.addData(str.toUtf8());
    return QString::fromLatin1(md5.result().toBase64().constData());
}

#endif // MATH_H

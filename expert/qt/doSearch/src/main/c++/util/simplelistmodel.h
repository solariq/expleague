#ifndef SIMPLELISTMODEL_H
#define SIMPLELISTMODEL_H

#include <QAbstractListModel>
#include <QMutex>

template <class T>
class SimpleListModel: public QAbstractListModel {
public:
    QHash<int, QByteArray> roleNames() const {
        QHash<int, QByteArray> result;
        result[Qt::UserRole] = "item";
        return result;
    }

    QVariant data(const QModelIndex &index, int role) const {
        QMutexLocker lock(&m_lock);
        QVariant result;
        if (role == Qt::UserRole)
            result.setValue(m_items.at(index.row()));
        return result;
    }

    int rowCount(const QModelIndex &parent) const {
        return m_items.size();
    }

    void append(const T& item) {
        QMutexLocker lock(&m_lock);
        m_items += item;
    }

private:
    QMutex m_lock;
    QList<T> m_items;
};

#endif // SIMPLELISTMODEL_H

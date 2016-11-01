#ifndef PHOLDER_H
#define PHOLDER_H

#include <functional>

#include <QHash>
#include <QVariant>

class PersistentPropertyHolder {
public:
    QVariant value(const QString& key) const;
    void store(const QString& key, const QVariant& value);
    void visitKeys(const QString& key, std::function<void (const QVariant&)> visitor) const;
    int count(const QString& key) const;
    void append(const QString& key, const QVariant& value);
    void remove(const QString& key);
    void remove(const QString& key, std::function<bool (const QVariant& value)> filter);
    void replaceOrAppend(const QString& key, const QVariant& value, std::function<bool (const QVariant& lhs, const QVariant& rhs)> equals);

    void save() const;

public:
    PersistentPropertyHolder(const QString& file);

private:
    QVariant* resolve(const QStringList& path, bool create = false);

private:
    QString m_file;
    QVariant m_properties = QVariant(QHash<QString, QVariant>());

    mutable volatile int m_changes = 0;
    mutable volatile int m_saved_changes = 0;
};

#endif // PHOLDER_H

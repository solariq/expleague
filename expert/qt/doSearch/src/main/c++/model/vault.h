#ifndef VAULT_H
#define VAULT_H

#include "context.h"
#include <QQmlListProperty>
#include <QUrl>

class QDropEvent;
namespace expleague {
class Knugget: public Page {
    Q_OBJECT

    Q_PROPERTY(expleague::Page* source READ source CONSTANT)
    Q_PROPERTY(QString md READ md CONSTANT)

public:
    Page* source() const {return m_source;}
    virtual QString md() const = 0;

protected:
    Knugget(const QString& id, Page* source, Context* owner, const QString& uiQml, doSearch* parent);
    Knugget(const QString& id, const QString& uiQml, doSearch* parent);

    void interconnect();
private:
    friend class Vault;

    Page* m_source;
    Context* m_owner;
};

class TextKnugget: public Knugget {
    Q_OBJECT

    Q_PROPERTY(QString text READ text CONSTANT)
public:
    QString text() const { return m_text; }
    QString md() const;

public:
    TextKnugget(const QString& id, doSearch* parent);
    TextKnugget(const QString& id, const QString& text, Page* source, Context* context, doSearch* parent);

private:
    QString m_text;
};

class Vault : public QObject
{
    Q_OBJECT

    Q_PROPERTY(QQmlListProperty<expleague::Page> items READ items NOTIFY itemsChanged)
public:
    QQmlListProperty<Page> items() const { return QQmlListProperty<Page>(const_cast<Vault*>(this), const_cast<QList<Page*>&>(m_items)); }

    Q_INVOKABLE bool drop(const QString& text, const QString& html, const QList<QUrl>& urls, const QString& sourceId);
    Q_INVOKABLE void remove(Page* page);

signals:
    void itemsChanged();

public:
    Vault(Context* context);

protected:
    Context* parent() const { return static_cast<Context*>(QObject::parent()); }

private:
    QList<Page*> m_items;
};
}

#endif // VAULT_H

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

    Q_INVOKABLE virtual void open() const;

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

class LinkKnugget: public Knugget {
    Q_OBJECT

    Q_PROPERTY(QString text READ text CONSTANT)
    Q_PROPERTY(QUrl url READ url CONSTANT)
    Q_PROPERTY(QString screenshot READ screenshot NOTIFY screenshotChanged)

public:
    QString text() const { return m_text; }
    QUrl url() const { return m_link; }
    QString screenshot() const;
    void open() const;

    Q_INVOKABLE bool hasScreenshot() const;
    Q_INVOKABLE QString screenshotTarget() const;

    QString md() const;

signals:
    Q_INVOKABLE void screenshotChanged();

public:
    LinkKnugget(const QString& id, doSearch* parent);
    LinkKnugget(const QString& id, const QString& text, const QUrl& link, Page* source, Context* context, doSearch* parent);

private:
    QString m_text;
    QUrl m_link;
};

class ImageKnugget: public Knugget {
    Q_OBJECT

    Q_PROPERTY(QString alt READ alt CONSTANT)
    Q_PROPERTY(QUrl src READ src CONSTANT)

public:
    QUrl src() const { return m_src; }
    QString alt() const { return m_alt; }
    QString md() const;

public:
    ImageKnugget(const QString& id, doSearch* parent);
    ImageKnugget(const QString& id, const QString& alt, const QUrl& imageUrl, Page* source, Context* context, doSearch* parent);

private:
    QString m_alt;
    QUrl m_src;
};

class Vault : public QObject {
    Q_OBJECT

    Q_PROPERTY(QQmlListProperty<expleague::Knugget> items READ items NOTIFY itemsChanged)
public:
    QQmlListProperty<Knugget> items() const { return QQmlListProperty<Knugget>(const_cast<Vault*>(this), const_cast<QList<Knugget*>&>(m_items)); }

    Q_INVOKABLE bool drop(const QString& text, const QString& html, const QList<QUrl>& urls, const QString& sourceId);
    Q_INVOKABLE void appendLink(const QUrl& url, const QString& text = "", Page* source = 0);
    Q_INVOKABLE bool paste(Page* source = 0);
    Q_INVOKABLE void remove(Knugget* page);

    Q_INVOKABLE void clearClipboard() const;

signals:
    void itemsChanged();

public:
    Vault(Context* context);

protected:
    Context* parent() const { return static_cast<Context*>(QObject::parent()); }

private:
    QList<Knugget*> m_items;
};
}

#endif // VAULT_H

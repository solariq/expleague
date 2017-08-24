#ifndef VAULT_H
#define VAULT_H

#include "context.h"
#include <QQmlListProperty>
#include <QUrl>

class QDropEvent;
namespace expleague {
class GroupKnugget;
class Knugget: public ContentPage {
    Q_OBJECT

    Q_PROPERTY(expleague::Page* source READ source CONSTANT)
    Q_PROPERTY(QString md READ md CONSTANT)
    Q_PROPERTY(expleague::GroupKnugget* group READ group WRITE setGroup NOTIFY groupChanged)

public:
    Page* source() const {return m_source;}
    virtual QString md() const = 0;

    QString title() const { return m_title.isEmpty() ? m_source->title() : m_title; }
    Q_INVOKABLE virtual void setTitle(const QString& name);

    GroupKnugget* group() const { return m_group; }
    void setGroup(GroupKnugget* group);
    Q_INVOKABLE virtual void open() const;

    virtual void interconnect() override;
signals:
    void groupChanged() const;

protected:
    Knugget(const QString& id, Page* source, const QString& uiQml, doSearch* parent);
    Knugget(const QString& id, const QString& uiQml, doSearch* parent);

    bool hasExplicitTitle() const { return !m_title.isEmpty(); }
private:
    friend class Vault;

    GroupKnugget* m_group = 0;
    Page* m_source;
    QString m_title;
};

class TextKnugget: public Knugget {
    Q_OBJECT

    Q_PROPERTY(QString text READ text CONSTANT)
public:
    QString text() const { return m_text; }
    QString md() const;
    QString icon() const { return "qrc:/vault/text.png";}

public:
    TextKnugget(const QString& id, doSearch* parent);
    TextKnugget(const QString& id, const QString& text, Page* source, doSearch* parent);

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
    QString icon() const { return "qrc:/vault/link.png";}

    QString title() const;

    Q_INVOKABLE bool hasScreenshot() const;
    Q_INVOKABLE QString screenshotTarget() const;

    QString md() const;

signals:
    Q_INVOKABLE void screenshotChanged() const;

public:
    LinkKnugget(const QString& id, doSearch* parent);
    LinkKnugget(const QString& id, const QString& text, const QUrl& link, Page* source, doSearch* parent);

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
    QString icon() const { return "qrc:/vault/image.png";}

public:
    ImageKnugget(const QString& id, doSearch* parent);
    ImageKnugget(const QString& id, const QString& alt, const QUrl& imageUrl, Page* source, doSearch* parent);

private:
    QString m_alt;
    QUrl m_src;
};

class GroupKnugget: public Knugget {
    Q_OBJECT

    Q_PROPERTY(QQmlListProperty<expleague::Knugget> items READ itemsQml NOTIFY itemsChanged)

public:
    QString title() const { return m_name; }
    QString md() const;

    Q_INVOKABLE void setTitle(const QString& name);

    QQmlListProperty<Knugget> itemsQml() const { return QQmlListProperty<Knugget>(const_cast<GroupKnugget*>(this), const_cast<QList<Knugget*>&>(m_items)); }

public:
    Q_INVOKABLE void remove(Knugget* item);
    Q_INVOKABLE void move(int from, int to);
    void insert(Knugget* item, int index = -1);
    int indexOf(Knugget* item) const { return m_items.indexOf(item); }
    void open() const;
    QList<Knugget*> items() const { return m_items; }

    virtual void interconnect() override;
signals:
    void itemsChanged() const;

public:
    GroupKnugget(const QString& id, Context* src, doSearch* parent);
    GroupKnugget(const QString& id, doSearch* parent);

private:
    QString m_name;
    QList<Knugget*> m_items;
    QObject* m_parent_group;
};

class Vault : public QObject {
    Q_OBJECT

    Q_PROPERTY(QQmlListProperty<expleague::Knugget> items READ items NOTIFY itemsChanged)
    Q_PROPERTY(QObject* activeGroup READ activeGroup WRITE setActiveGroup NOTIFY activeGroupChanged)

public:
    QQmlListProperty<Knugget> items() const { return QQmlListProperty<Knugget>(const_cast<Vault*>(this), const_cast<QList<Knugget*>&>(m_items)); }
    QObject* activeGroup() const { if (m_active_group) return const_cast<GroupKnugget*>(m_active_group); return const_cast<Vault*>(this); }

    void setActiveGroup(QObject* group) {
        m_active_group = qobject_cast<GroupKnugget*>(group);
        emit activeGroupChanged();
    }

public:
    Q_INVOKABLE void group(Knugget* left, Knugget* right);
    Q_INVOKABLE void ungroup(GroupKnugget* left);
    Q_INVOKABLE void commitVisualModel(QObject* model);

    Q_INVOKABLE bool drop(const QString& text, const QString& html, const QList<QUrl>& urls, const QString& sourceId);
    Q_INVOKABLE void appendLink(const QUrl& url, const QString& text = "", Page* source = 0);

    Q_INVOKABLE bool paste(Page* source = 0);

    Q_INVOKABLE void insert(Knugget* item, int position = -1);
    Q_INVOKABLE void remove(Knugget* page);
    Q_INVOKABLE void move(int from, int to);

    Q_INVOKABLE void clearClipboard() const;

    void append(Knugget* knugget) { m_items.append(knugget); emit itemsChanged(); }

signals:
    void itemsChanged() const;
    void activeGroupChanged() const;

private:
    QString generateKnuggetId(const QString& suffix);

public:
    Vault(Context* context);

protected:
    Context* parent() const { return static_cast<Context*>(QObject::parent()); }

private:
    QList<Knugget*> m_items;
    GroupKnugget* m_active_group = 0;
};
}

#endif // VAULT_H

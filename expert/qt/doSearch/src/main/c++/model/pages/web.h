#ifndef WEB_H
#define WEB_H

#include "../page.h"

#include <QSet>
#include <QQmlListProperty>

class QQuickItem;
namespace expleague {
class WebSite;

class WebPage: public Page {
    Q_OBJECT

    Q_PROPERTY(QUrl url READ url NOTIFY urlChanged)
    Q_PROPERTY(QUrl originalUrl READ originalUrl CONSTANT)
    Q_PROPERTY(WebPage* redirect READ redirect WRITE setRedirect NOTIFY redirectChanged)
    Q_PROPERTY(QQmlListProperty<expleague::WebPage> redirects READ redirects NOTIFY urlChanged)

public:
    QUrl url() const;

    QUrl originalUrl() const { return m_url; }
    WebPage* redirect() const { return m_redirect; }
    QQmlListProperty<WebPage> redirects() const { return QQmlListProperty<WebPage>(const_cast<WebPage*>(this), const_cast<QList<WebPage*>&>(m_redirects)); }

    QString icon() const;
    QString title() const;

    WebSite* site() const;

    Q_INVOKABLE bool accept(const QUrl& url) const;
    Q_INVOKABLE void setTitle(const QString& title);
    Q_INVOKABLE void setIcon(const QString& icon);
    void setRedirect(WebPage* target);
    void setUrl(const QUrl& url);

    Q_INVOKABLE bool forwardToWebView(int key,
                                      Qt::KeyboardModifiers modifiers,
                                      const QString& text,
                                      bool autoRepeat,
                                      ushort count,
                                      QQuickItem* view);

    Q_INVOKABLE bool forwardShortcutToWebView(const QString& shortcut, QQuickItem* view);
    Q_INVOKABLE bool dropToWebView(QObject* drop, QQuickItem* view);

    Q_INVOKABLE void reset() { setRedirect(0); }
    void transferUI(WebPage* target) const {
        Page::transferUI(target);
    }

signals:
    void redirectChanged();
    void urlChanged(const QUrl& url);

protected:
    void interconnect();
    void rebuildRedirects();

public:
    WebPage(const QString& id, const QUrl& url, doSearch* parent);
    WebPage(const QString &id, doSearch *parent);

private slots:
    void onRedirectUrlChanged(const QUrl& url);

private:
    QUrl m_url;
    QList<WebPage*> m_redirects;
    WebPage* m_redirect = 0;
};

class WebSite: public WebPage {
    Q_OBJECT

public:
    WebSite* site() const { return const_cast<WebSite*>(this); }

    bool mirrorTo(WebSite* site) const { return site == this || m_mirrors.contains(site); }
    void addMirror(WebSite* site) {
        if (m_mirrors.contains(site))
            return;
        m_mirrors += site;
        append("web.site.mirrors", site->id());
        QObject::connect(site, SIGNAL(mirrorsChanged()), this, SLOT(onMirrorsChanged()));
        emit mirrorsChanged();
    }
    QSet<WebSite*> mirrors() const { return m_mirrors; }

    QString title() const {
        QString domain = originalUrl().host();
        if (domain.startsWith("www."))
            domain = domain.mid(4);
        return originalUrl().host();
    }

    QString icon() const {
        QVariant var = value("web.favicon");
        return var.isNull() ? "" : var.toString();
    }

    void setIcon(const QString& icon) {
        store("web.favicon", icon);
        save();
        iconChanged(icon);
    }

signals:
    void mirrorsChanged();

private slots:
    void onMirrorsChanged();

public:
    WebSite(const QString& id, const QUrl& url, doSearch* parent): WebPage(id, url, parent) {}
    WebSite(const QString &id, doSearch *parent): WebPage(id, parent) {}

    void interconnect();
private:
    QSet<WebSite*> m_mirrors;
};
}
#endif // WEB_H

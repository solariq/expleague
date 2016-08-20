#ifndef WEB_H
#define WEB_H

#include "page.h"

#include <QSet>

class QQuickItem;
namespace expleague {
class WebSite;

class WebPage: public Page {
    Q_OBJECT

    Q_PROPERTY(QUrl url READ url NOTIFY urlChanged)
    Q_PROPERTY(expleague::WebPage* redirect READ redirect WRITE setRedirect NOTIFY redirectChanged)

public:
    QUrl url() const;

    QUrl originalUrl() const { return m_url; }
    WebPage* redirect() const { return m_redirect; }

    QString icon() const;

    QString title() const {
        QVariant var = value("web.title");
        return var.isNull() ? "" : var.toString();
    }

    WebSite* site() const;

    Q_INVOKABLE bool accept(const QUrl& url) const;

    Q_INVOKABLE void setTitle(const QString& title) {
        store("web.title", title);
        save();
        titleChanged(title);
    }

    Q_INVOKABLE void setIcon(const QString& icon);
    void setRedirect(WebPage* target);
    void setUrl(const QUrl& url);

    Q_INVOKABLE bool forwardToWebView(int key,
                                      Qt::KeyboardModifiers modifiers,
                                      const QString& text,
                                      bool autoRepeat,
                                      ushort count,
                                      QQuickItem* view);
signals:
    void redirectChanged();
    void urlChanged(const QUrl& url);

protected:
    void interconnect();

public:
    WebPage(const QString& id, const QUrl& url, doSearch* parent);
    WebPage(const QString &id, doSearch *parent);

private slots:
    void onRedirectUrlChanged(const QUrl& url);

private:
    QUrl m_url;
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

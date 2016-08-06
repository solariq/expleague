#ifndef WEB_H
#define WEB_H

#include "page.h"

class QQuickItem;
namespace expleague {
class WebSite;

class WebPage: public Page {
    Q_OBJECT

    Q_PROPERTY(QUrl url READ url NOTIFY redirectChanged)
    Q_PROPERTY(expleague::WebPage* redirect READ redirect WRITE setRedirect NOTIFY redirectChanged)

public:
    WebPage(const QString& id, const QUrl& url, doSearch* parent);

    WebPage(const QString &id, doSearch *parent);

    QUrl url() const;
//    QUrl url() const {
//        return m_redirect ? m_redirect->url() : m_url;
//    }

    QUrl originalUrl() const { return m_url; }
    WebPage* redirect() const { return m_redirect; }

    QString icon() const;

    QString title() const {
        QVariant var = value("web.title");
        return var.isNull() ? "" : var.toString();
    }

    WebSite* site() const;

    Q_INVOKABLE void setTitle(const QString& title) {
        store("web.title", title);
        save();
        titleChanged(title);
    }

    Q_INVOKABLE void setIcon(const QString& icon);
    void setRedirect(WebPage* target);

    Q_INVOKABLE bool forwardToWebView(int key,
                                      Qt::KeyboardModifiers modifiers,
                                      const QString& text,
                                      bool autoRepeat,
                                      ushort count,
                                      QQuickItem* view);
signals:
    void redirectChanged();

protected:
    void interconnect();

private:
    QUrl m_url;
    WebPage* m_redirect = 0;
};

class WebSite: public WebPage {
public:
    WebSite(const QString& id, const QUrl& url, doSearch* parent): WebPage(id, url, parent) {}
    WebSite(const QString &id, doSearch *parent): WebPage(id, parent) {}

    WebSite* site() const { return const_cast<WebSite*>(this); }

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
};
}
#endif // WEB_H

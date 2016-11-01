#ifndef WEB_H
#define WEB_H

#include "../page.h"

#include "../../ir/bow.h"

#include <QSet>
#include <QQmlListProperty>

class QQuickItem;
namespace expleague {
class WebSite;
class WebPage;

class WebResource {
public:
    QUrl url() const;
    bool isRoot() const;

    virtual QUrl originalUrl() const = 0;
    virtual void setOriginalUrl(const QUrl& url) = 0;

    virtual WebSite* site() const = 0;
    virtual WebPage* page() const = 0;

    virtual WebPage* redirect() const = 0;
    virtual void setRedirect(WebPage* target) = 0;

    static bool rootUrl(const QUrl& url);
};

class WebPage: public ContentPage, public WebResource {
    Q_OBJECT

    Q_PROPERTY(QUrl url READ url NOTIFY urlChanged)
    Q_PROPERTY(QUrl originalUrl READ originalUrl NOTIFY originalUrlChanged)
    Q_PROPERTY(WebPage* redirect READ redirect WRITE setRedirect NOTIFY redirectChanged)
    Q_PROPERTY(QQmlListProperty<expleague::WebPage> redirects READ redirects NOTIFY urlChanged)
    Q_PROPERTY(Page* container READ container NOTIFY containerChanged)

public: // QML
    QQmlListProperty<WebPage> redirects() const { return QQmlListProperty<WebPage>(const_cast<WebPage*>(this), const_cast<QList<WebPage*>&>(m_redirects)); }

    QString icon() const;
    QString title() const;

    virtual Page* container() const;

    Q_INVOKABLE bool forwardToWebView(int key,
                                      Qt::KeyboardModifiers modifiers,
                                      const QString& text,
                                      bool autoRepeat,
                                      ushort count,
                                      QQuickItem* view);

    Q_INVOKABLE bool forwardShortcutToWebView(const QString& shortcut, QQuickItem* view);
    Q_INVOKABLE bool dropToWebView(QObject* drop, QQuickItem* view);
    Q_INVOKABLE void open(QObject* request, bool newTab);

public: // new functionality
    Q_INVOKABLE bool accept(const QUrl& url) const;

    Q_INVOKABLE virtual void open(const QUrl& url, bool newTab, bool transferUI = true);

    Q_INVOKABLE void setTitle(const QString& title);
    Q_INVOKABLE void setIcon(const QString& icon);
    Q_INVOKABLE void reset() { setRedirect(0); }

public: // overloads
    Page* parentPage() const;

    void setProfile(const BoW& profile);

    QUrl originalUrl() const { return m_url; }
    void setOriginalUrl(const QUrl& url);

    WebPage* redirect() const { return m_redirect; }
    void setRedirect(WebPage* target);

    WebSite* site() const;
    WebPage* page() const { return const_cast<WebPage*>(this); }

signals:
    void redirectChanged(WebPage* target);
    void urlChanged(const QUrl& url);
    void originalUrlChanged(const QUrl& url);
    void containerChanged();

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
    WebPage* m_redirect = 0;

    QList<WebPage*> m_redirects;
};

class WebSite: public CompositeContentPage, public WebResource {
    Q_OBJECT

    Q_PROPERTY(expleague::WebPage* root READ page CONSTANT)

public:
    bool mirrorTo(WebSite* site) const { return site == this || m_mirrors.contains(site); }
    QSet<WebSite*> mirrors() const { return m_mirrors; }

public:
    QString title() const {
        QString domain = page()->originalUrl().host();
        if (domain.startsWith("www."))
            domain = domain.mid(4);
        return domain;
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

    QUrl originalUrl() const { return page()->originalUrl(); }
    void setOriginalUrl(const QUrl& url) { page()->setOriginalUrl(url); }

    WebSite* site() const { return const_cast<WebSite*>(this); }
    WebPage* page() const { return m_root; }

    WebPage* redirect() const { return page()->redirect(); }
    virtual void setRedirect(WebPage* target) {  page()->setRedirect(target); }

    BoW templates() const { return m_templates; }

signals:
    void mirrorsChanged();
    void rootChanged();

public slots:
    void onPageLoaded(Page* child);

private slots:
    void onMirrorsChanged();
    void onPartProfileChanged(const BoW& oldOne, const BoW& newOne);

    void onChildRedirectChanged(WebPage* target) {
        if (target)
            addMirror(target->site());
    }

public:
    WebSite(const QString& id, const QString& domain, const QUrl& rootUrl, doSearch* parent);
    WebSite(const QString &id, doSearch *parent);

    void interconnect();

private:
    void addMirror(WebSite* site);

private:
    WebPage* m_root;
    QSet<WebSite*> m_mirrors;
    BoW m_templates;
};
}
#endif // WEB_H

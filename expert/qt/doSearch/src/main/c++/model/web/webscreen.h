#ifndef WEBSCREEN_H
#define WEBSCREEN_H

#include <QUrl>

#include <QQuickItem>

#include "../screen.h"

namespace expleague {
class WebFolder;
class WebScreen: public Screen {
    Q_OBJECT

public:
    WebScreen(QObject* parent = 0): Screen(QUrl("qrc:/WebScreenView.qml"), parent), webView(itemById<QQuickItem>("webView")) {
        connect(webView, SIGNAL(urlChanged()), SLOT(urlChanged()));
        connect(webView, SIGNAL(titleChanged()), SLOT(titleChanged()));
        connect(webView, SIGNAL(iconChanged()), SLOT(iconChanged()));
        setupOwner();
    }

    bool handleOmniboxInput(const QString &text) {
        QUrl url(text);
        if (url.isValid()) {
            webView->setProperty("url", url);
        }
        return url.isValid();
    }

    QUrl icon() const {
        return webView->property("icon").toUrl();
    }

    QString location() const {
        return webView->property("url").toString();
    }

    QString name() const {
        return webView->property("title").toString();
    }

    QQuickItem* webEngine() {
        return webView;
    }

    Q_INVOKABLE QQuickItem* landing();

private slots:
    void urlChanged() {
        locationChanged(location());
    }

    void titleChanged() {
        nameChanged(name());
    }

    void iconChanged() {
        Screen::iconChanged(icon());
    }
private:
    WebFolder* owner() {
        return (WebFolder*)parent();
    }

private:
    QQuickItem* webView;
};
}

QML_DECLARE_TYPE(expleague::WebScreen)
#endif // WEBBOUQUET_H

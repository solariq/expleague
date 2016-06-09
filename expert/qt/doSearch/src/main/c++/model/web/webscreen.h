#ifndef WEBSCREEN_H
#define WEBSCREEN_H

#include <QUrl>

#include <QQuickItem>

#include "../screen.h"

namespace expleague {
class WebFolder;
class WebScreen: public Screen {
    Q_OBJECT

    Q_PROPERTY(QQuickItem* webView READ webView CONSTANT)

public:
    WebScreen(QObject* parent = 0): Screen(QUrl("qrc:/WebScreenView.qml"), parent), m_web_view(itemById<QQuickItem>("webView")) {
        connect(m_web_view, SIGNAL(urlChanged()), SLOT(urlChanged()));
        connect(m_web_view, SIGNAL(titleChanged()), SLOT(titleChanged()));
        connect(m_web_view, SIGNAL(iconChanged()), SLOT(iconChanged()));
        setupOwner();
    }

    bool handleOmniboxInput(const QString &text);

    QUrl icon() const {
        return m_web_view->property("icon").toUrl();
    }

    QString location() const {
        return m_url;
    }

    QString name() const {
        return m_web_view->property("title").toString();
    }

    QQuickItem* webView() {
        return m_web_view;
    }

    Q_INVOKABLE QQuickItem* landing();

private slots:
    void urlChanged();

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
    QQuickItem* m_web_view;
    QString m_url;
};
}

QML_DECLARE_TYPE(expleague::WebScreen)
#endif // WEBBOUQUET_H

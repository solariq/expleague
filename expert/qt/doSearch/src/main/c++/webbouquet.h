#ifndef WEBBOUQUET_H
#define WEBBOUQUET_H

#include <QWebEngineView>

#include "context.h"

namespace expleague {
class WebScreen: public Screen {
    Q_OBJECT
public:
    WebScreen(const QString& id, Bouquet* parent): Screen(id, parent, QUrl("qrc:/WebScreen.qml")), webView(itemById<QQuickItem>("webView")) {
        connect(webView, SIGNAL(urlChanged()), SLOT(urlChanged()));
    }

    bool handleOmniboxInput(const QString &text) {
        QUrl url(text);
        if (url.isValid())
            webView->setProperty("url", url);
        return url.isValid();
    }

    QString location() {
        return webView->property("url").toString();
    }

private slots:
    void urlChanged() {
        bouquet()->context()->omniboxChanged(webView->property("url").toString());
    }

private:
    QQuickItem* webView;
};

class WebBouquet: public Bouquet {
    Q_OBJECT
public:
    WebBouquet(const QString& id, Context* context): Bouquet(id, context) {}

    bool handleOmniboxInput(const QString &text) {
        QUrl url(text);
        if (url.isValid()) {
            WebScreen* active = dynamic_cast<WebScreen*>(screen());
            if (!active) {
                m_screens.append(active = new WebScreen(text, this));
                screensChanged();
                active->setActive(true);
            }
            active->handleOmniboxInput(text);
            return true;
        }
        else { // search

        }
        return false;
    }

private:
};
}
#endif // WEBBOUQUET_H

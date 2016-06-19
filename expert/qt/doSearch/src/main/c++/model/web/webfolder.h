#ifndef WEBFOLDER_H
#define WEBFOLDER_H

#include <QWebEngineView>
#include <QDnsLookup>

#include "../folder.h"

#include "webscreen.h"
#include "websearch.h"

namespace expleague {
class WebFolder: public Folder {
    Q_OBJECT

public:
    WebFolder(QObject* parent = 0): Folder(parent) {
        connect(&m_lookup, SIGNAL(finished()), SLOT(dnsRequestFinished()));
        WebSearch* searchTab = new WebSearch(this);
        append(searchTab);
        QObject::connect(searchTab, SIGNAL(queriesChanged()), SLOT(changedRequests()));
    }

    bool handleOmniboxInput(const QString &text, bool newTab);

    WebScreen* createWebTab(bool activate, Screen* source = 0) {
//        qDebug() << "Creating new tab at position: " << m_screens.indexOf(source);
        WebScreen* tab = new WebScreen(this);
        if (source)
            insert(m_screens.indexOf(source), tab);
        else if (m_screens.length() == 1)
            append(tab);
        else
            insert(1, tab);
        if (activate)
            tab->setActive(true);
        return tab;
    }

    virtual QUrl icon() const {
        return QUrl("qrc:/chromium.png");
    }

    QList<SearchRequest*> requests() const;

signals:
    void requestsChanged();

private slots:
    void dnsRequestFinished();
    void changedRequests() {
        requestsChanged();
    }

    void openUrl(const QUrl& url, bool newTab) {
        WebScreen* active = dynamic_cast<WebScreen*>(screen());
        if (!active || newTab)
            active = createWebTab(true);
        active->handleOmniboxInput(url.toString());
    }

private:
    QString m_text;
    bool m_newTab;
    QDnsLookup m_lookup;
};
}

QML_DECLARE_TYPE(expleague::WebFolder)

#endif // WEBBOUQUET_H

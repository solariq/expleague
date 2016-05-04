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
        append(new WebSearch(this));
    }

    bool handleOmniboxInput(const QString &text, bool newTab) {
        QString finalText;
        if (text.startsWith("http:") || text.startsWith("https:") || text.startsWith("ftp:") || text.startsWith("ftps:") || text.startsWith("about:")) { // protocols
            QUrl url(text);

            if (url.isValid()) {
                openUrl(url, newTab);
                return true;
            }
        }
        QString domain = text.split("/")[0];
        m_text = text;
        m_newTab = newTab;
        m_lookup.setName(domain);
        m_lookup.lookup();
        return true;
    }

    WebScreen* createWebTab() {
        WebScreen* tab = new WebScreen(this);
        append(tab);
        tab->setActive(true);
        return tab;
    }

    virtual QUrl icon() const {
        return QUrl("qrc:/chromium.png");
    }

private slots:
    void dnsRequestFinished(){
        if (m_lookup.error() == QDnsLookup::NoError) { // seems to be domain!
            openUrl(QUrl("http://" + m_text), m_newTab);
            return;
        }
        // search fallback
        WebSearch* search;
        if (!(search = dynamic_cast<WebSearch*>(at(0)))) {
            search = new WebSearch(this);
            insert(0, search);
        }

        search->search(m_text);
        search->setActive(true);
    }

    void openUrl(const QUrl& url, bool newTab) {
        WebScreen* active = dynamic_cast<WebScreen*>(screen());
        if (!active || newTab)
            active = createWebTab();
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

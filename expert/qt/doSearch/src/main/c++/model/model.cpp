#include "context.h"

#include "web/webfolder.h"

namespace expleague {

bool isSearch(const QUrl& url) {
    QString host = url.host();
    return host == "www.google.com" || host == "yandex.ru";
}

void Context::handleOmniboxInput(const QString &text, bool newTab) {
    if (!folder()) {
        WebFolder* wf = new WebFolder(this);
        append(wf);
        wf->setActive(true);
    }
    folder()->handleOmniboxInput(text, newTab);
}
}

#include "context.h"
#include "../task.h"

#include "web/webfolder.h"
#include "expleague/answersfolder.h"

#include <QImage>
#include <QPainter>
#include <QStyle>

#include <QApplication>
#include <QQmlEngine>

#include <QQuickImageProvider>
#include <QQmlExtensionPlugin>

namespace expleague {

bool isSearch(const QUrl& url) {
    QString host = url.host();
    return host == "www.google.com" || host == "yandex.ru";
}
Context::Context(const QString& name, QObject* parent): QObject(parent), m_name(name), m_icon(QUrl("qrc:/chromium.png")) {
    append(new WebFolder(this));
}

Context::Context(Offer* offer, QObject* parent): QObject(parent), m_task(new Task(offer, this)), m_name(offer->topic()), m_icon("qrc:/avatar.png") {
    append(new WebFolder(this));
    AnswersFolder* folder = new AnswersFolder(m_task, this);
    append(folder);
    folder->setActive(true);
}

void Context::handleOmniboxInput(const QString &text, bool newTab) {
    if (!folder() || !folder()->handleOmniboxInput(text, newTab)) {
        for (int i = 0; i < m_folders.size(); i++) {
            if (m_folders[i]->handleOmniboxInput(text, newTab)) {
                m_folders[i]->setActive(true);
                return;
            }
        }
        WebFolder* wf = new WebFolder(this);
        append(wf);
        wf->setActive(true);
    }
}
}

#include "context.h"
#include "../task.h"

#include "web/webfolder.h"
#include "expleague/answersfolder.h"

#include <QImage>
#include <QPainter>
#include <QStyle>

#include <QApplication>
#include <QQmlEngine>
#include <QQmlComponent>

#include <QQuickImageProvider>
#include <QQmlExtensionPlugin>

namespace expleague {

bool isSearch(const QUrl& url) {
    QString host = url.host();
    return host == "www.google.com" || host == "yandex.ru";
}
Context::Context(const QString& name, QObject* parent): QObject(parent), m_name(name), m_icon(QUrl("qrc:/chromium.png")), m_id(name) {
}

Context::Context(Task* task, QObject* parent): QObject(parent), m_task(task), m_name(task->offer()->topic()), m_icon("qrc:/avatar.png"), m_id(task->id()) {
    qDebug() << "Creating context for task " << task->id() << " (" << task << ")";
    AnswersFolder* folder = new AnswersFolder(m_task, this);
    append(folder);
    folder->setActive(true);
    QObject::connect(task, SIGNAL(finished()), SLOT(taskFinished()));
    QObject::connect(task, SIGNAL(cancelled()), SLOT(taskFinished()));
    task->setContext(this);
}

Context::~Context() {
    if (m_task)
        m_task->setContext(0);
    emit closed();
}

void Context::taskFinished() {
    deleteLater();
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
        wf->handleOmniboxInput(text, newTab);
    }
}

AnswersFolder::AnswersFolder(Task* task, QObject* parent): Folder(parent) {
    if (task) {
        MarkdownEditorScreen* const answer = new MarkdownEditorScreen(this);
        QObject::connect(answer, SIGNAL(textChanged(QString)), task, SLOT(setAnswer(QString)));
        QObject::connect(task, SIGNAL(answerReset(QString)), answer, SLOT(resetText(QString)));
        append(answer);
        QObject::connect(task, SIGNAL(receivedAnswer(ReceivedAnswer*)), this, SLOT(answerReceived(ReceivedAnswer*)));
    }
}

void AnswersFolder::answerReceived(ReceivedAnswer* answer) {
//    qDebug() << "Appending answer screen";
    MarkdownEditorScreen* const answerScreen = new MarkdownEditorScreen(answer, this);
    append(answerScreen);
}

Screen::Screen(QUrl item, QObject *parent): QObject(parent) {
    QQmlComponent component(rootEngine, item, QQmlComponent::PreferSynchronous);
    if (component.isError()) {
        qWarning() << "Error during screen load";
        foreach(QQmlError error, component.errors()) {
            qWarning() << error;
        }
    }
    QQuickItem* instance = (QQuickItem*)component.create();
    instance->setParent(this);
    m_root = instance;
}

void Screen::setupOwner() {
//    qDebug() << "setting owner property for " << m_root->objectName() << " owner: " << this;
    QVariant owner;
    owner.setValue(this);
    m_root->setProperty("owner", owner);
}
}

#include <QtCore/QUrl>
#include <QtCore/QCommandLineOption>
#include <QtCore/QCommandLineParser>
#include <QApplication>
#include <QStyleHints>
#include <QScreen>
#include <QSystemTrayIcon>
#include <QQmlApplicationEngine>
#include <QtQml/QQmlContext>
#include <QtWebView/QtWebView>
#include <QStyleOptionButton>
#include <QStyle>
#include <QTextCodec>
#include <QQuickWindow>

#include <QtWebEngine>

#include "expleague.h"
#include "model/history.h"

using namespace expleague;

void declareTypes();
void setupScreenDefaults();

QQmlApplicationEngine* rootEngine;
#ifndef Q_OS_MAC
QSystemTrayIcon* trayIcon;
#endif

int main(int argc, char *argv[]) {
    QTextCodec::setCodecForLocale(QTextCodec::codecForName("UTF-8"));
    QApplication app(argc, argv);
#ifndef Q_OS_MAC
    trayIcon = new QSystemTrayIcon();
    trayIcon->setIcon(QIcon(":/avatar.png"));
    trayIcon->show();
#endif

    QQmlApplicationEngine engine;
    rootEngine = &engine;

    QCoreApplication::setApplicationVersion(QT_VERSION_STR);
    QQmlContext* context = engine.rootContext();

    setupScreenDefaults();

    root = new doSearch(&app);

    declareTypes();
    context->setContextProperty("root", root);
    context->setContextProperty("dosearch", root);

    root->restoreState();

    engine.load(QUrl(QStringLiteral("qrc:/Main.qml")));
    if (engine.rootObjects().isEmpty())
        return -1;
    QtWebEngine::initialize();
    return app.exec();
}

void setupScreenDefaults() {
    QSettings settings;
    settings.beginGroup("MainPage");

    QRect geometry = QGuiApplication::primaryScreen()->availableGeometry();
    if (!QGuiApplication::styleHints()->showIsFullScreen()) {
        const QSize size = geometry.size() * 4 / 5;
        const QSize offset = (geometry.size() - size) / 2;
        const QPoint pos = geometry.topLeft() + QPoint(offset.width(), offset.height());
        geometry = QRect(pos, size);
    }
    if (!settings.value("x").isValid())
        settings.setValue("x", QVariant(geometry.x()));
    if (!settings.value("x").isValid())
        settings.setValue("y", QVariant(geometry.y()));
    if (!settings.value("x").isValid())
        settings.setValue("width", QVariant(geometry.width()));
    if (!settings.value("x").isValid())
        settings.setValue("height", QVariant(geometry.height()));

    qDebug() << "Main screen settings: ";
    foreach (const QString& key, settings.childKeys()) {
        qDebug() << "\t" << key << " " << settings.value(key);
    }
}

void declareTypes() {
    rootEngine->addImageProvider("store", doSearch::instance()->league()->store());

    qRegisterMetaType<Profile::Sex>("expleague::Profile::Sex");
    qRegisterMetaType<Page::State>("expleague::Page::State");

    qRegisterMetaType<Profile*>("Profile*");
    qRegisterMetaType<Context*>("Context*");
    qRegisterMetaType<MarkdownEditorPage*>("MarkdownEditorPage*");
    qRegisterMetaType<expleague::Task*>("Task*");
    qRegisterMetaType<expleague::Bubble*>("Bubble*");
    qRegisterMetaType<expleague::ChatMessage*>("ChatMessage*");
    qRegisterMetaType<Offer*>("Offer*");
    qRegisterMetaType<doSearch*>("doSearch*");
    qRegisterMetaType<WebPage*>("WebPage*");
    qRegisterMetaType<Member*>("Member*");
    qRegisterMetaType<TaskTag*>("TaskTag*");
    qRegisterMetaType<AnswerPattern*>("AnswerPattern*");
    qRegisterMetaType<expleague::NavigationManager*>("NavigationManager*");
    qRegisterMetaType<expleague::Page*>("Page*");
    qRegisterMetaType<expleague::PagesGroup*>("PagesGroup*");
    qRegisterMetaType<expleague::SearchRequest*>("SearchRequest*");

    qmlRegisterType<ProfileBuilder>("ExpLeague", 1, 0, "ProfilePreview");
    qmlRegisterType<expleague::SearchRequest>("ExpLeague", 1, 0, "SearchRequest");
    qmlRegisterType<MarkdownEditorPage>("ExpLeague", 1, 0, "MarkdownEditorScreen");
    qmlRegisterType<Context>("ExpLeague", 1, 0, "Context");
    qmlRegisterType<Offer>("ExpLeague", 1, 0, "Offer");
    qmlRegisterType<expleague::NavigationManager>("ExpLeague", 1, 0, "NavigationManager");
    qmlRegisterType<expleague::Task>("ExpLeague", 1, 0, "Task");
    qmlRegisterType<expleague::Bubble>("ExpLeague", 1, 0, "Bubble");
    qmlRegisterType<expleague::ChatMessage>("ExpLeague", 1, 0, "ChatMessage");
    qmlRegisterType<expleague::Member>("ExpLeague", 1, 0, "Member");
    qmlRegisterType<expleague::TaskTag>("ExpLeague", 1, 0, "TaskTag");
    qmlRegisterType<expleague::AnswerPattern>("ExpLeague", 1, 0, "AnswerPattern");
    qmlRegisterType<expleague::PagesGroup>("ExpLeague", 1, 0, "PagesGroup");
    qmlRegisterType<Page>("ExpLeague", 1, 0, "Page");
    qmlRegisterType<League>("ExpLeague", 1, 0, "League");

    qmlRegisterUncreatableType<Profile>("ExpLeague", 1, 0, "Profile", "Profile requires registration and can be created only by appropriate builder class");
    qmlRegisterUncreatableType<doSearch>("ExpLeague", 1, 0, "doSearch", "This type is for root property only");
}

#include <QtCore/QUrl>
#include <QtCore/QCommandLineOption>
#include <QtCore/QCommandLineParser>
#include <QGuiApplication>
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
#include "model/pages/cefpage.h"
//#include "util/crashhandler.h"

//#include "include/cef_app.h"
//#include "include/cef_client.h"
//#include "include/cef_render_process_handler.h"

#include <cmath>

using namespace expleague;

void declareTypes();
void setupScreenDefaults();

QQmlApplicationEngine* rootEngine;
#ifndef Q_OS_MAC
QSystemTrayIcon* trayIcon;
#endif


int main(int argc, char *argv[]) {
    CefMainArgs main_args(GetModuleHandle(NULL));
    CefRefPtr<CefApp> cefapp;
    CefSettings settings;
    CefString(&settings.browser_subprocess_path).FromASCII(
                "C:\\pr1\\expleague\\expert\\qt\\build-doSearch-Desktop_Qt_5_9_0_MSVC2015_32bit-Debug\\src\\CEF\\debug\\CEF.exe");
    CefInitialize(main_args, settings, cefapp, NULL);
   //-------------------------
    //PersistentPropertyHolder::debugPrintAll();
    QTextCodec::setCodecForLocale(QTextCodec::codecForName("UTF-8"));
    QLocale::setDefault(QLocale(QLocale::English, QLocale::UnitedStates));
//  QCoreApplication::setAttribute(Qt::AA_UseSoftwareOpenGL);
//    QGuiApplication::setAttribute(Qt::AA_UseHighDpiPixmaps);
    QCoreApplication::setOrganizationName("Experts League");
    QCoreApplication::setOrganizationDomain("expleague.com");
#ifdef QT_DEBUG
    QCoreApplication::setApplicationName("doSearch-debug");
#else
    QCoreApplication::setApplicationName("doSearch");
#endif
    QCoreApplication::setApplicationVersion(EL_DOSEARCH_VERSION);

    { // making dirs
        QDir appDir(QStandardPaths::writableLocation(QStandardPaths::AppLocalDataLocation));
        appDir.mkpath("crash");
        appDir.mkpath("dictionary");
        appDir.mkpath("pages");
    }
#ifndef DEBUG
//    Atomix::CrashHandler::instance()->Init(QStandardPaths::writableLocation(QStandardPaths::AppLocalDataLocation) + "/crash");
#endif
    QGuiApplication app(argc, argv);
//    QCoreApplication::setAttribute(Qt::AA_UseDesktopOpenGL);
//    QCoreApplication::setAttribute(Qt::AA_UseSoftwareOpenGL);
#ifndef Q_OS_MAC
    trayIcon = new QSystemTrayIcon();
    trayIcon->setIcon(QIcon(":/avatar.png"));
    trayIcon->show();
#endif

    QQmlApplicationEngine engine;
    QtWebEngine::initialize();
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
    qRegisterMetaType<PagesGroup::Type>("expleague::PagesGroup::Type");
    qRegisterMetaType<League::Role>("expleague::League::Role");
    qRegisterMetaType<BoW>("BoW");

    qRegisterMetaType<Profile*>("Profile*");
    qRegisterMetaType<Context*>("Context*");
    qRegisterMetaType<MarkdownEditorPage*>("MarkdownEditorPage*");
    qRegisterMetaType<Task*>("Task*");
    qRegisterMetaType<Bubble*>("Bubble*");
    qRegisterMetaType<ChatMessage*>("ChatMessage*");
    qRegisterMetaType<Offer*>("Offer*");
    qRegisterMetaType<doSearch*>("doSearch*");
    qRegisterMetaType<WebPage*>("WebPage*");
    qRegisterMetaType<SERPage*>("SERPage*");
    qRegisterMetaType<Member*>("Member*");
    qRegisterMetaType<TaskTag*>("TaskTag*");
    qRegisterMetaType<History*>("History*");
    qRegisterMetaType<AnswerPattern*>("AnswerPattern*");
    qRegisterMetaType<NavigationManager*>("NavigationManager*");
    qRegisterMetaType<Page*>("Page*");
    qRegisterMetaType<PageVisit*>("PageVisit*");
    qRegisterMetaType<PagesGroup*>("PagesGroup*");
    qRegisterMetaType<SearchRequest*>("SearchRequest*");
    qRegisterMetaType<Vault*>("Vault*");
    qRegisterMetaType<Knugget*>("Knugget*");
    qRegisterMetaType<GroupKnugget*>("GroupKnugget*");
    qRegisterMetaType<RoomStatus*>("RoomState*");
    qRegisterMetaType<GlobalChat*>("GlobalChat*");
    qRegisterMetaType<CefItem*>("CefItem*");

    qmlRegisterType<ProfileBuilder>("ExpLeague", 1, 0, "ProfilePreview");
    qmlRegisterType<SearchRequest>("ExpLeague", 1, 0, "SearchRequest");
    qmlRegisterType<MarkdownEditorPage>("ExpLeague", 1, 0, "MarkdownEditorScreen");
    qmlRegisterType<Context>("ExpLeague", 1, 0, "Context");
    qmlRegisterType<Offer>("ExpLeague", 1, 0, "Offer");
    qmlRegisterType<NavigationManager>("ExpLeague", 1, 0, "NavigationManager");
    qmlRegisterType<Task>("ExpLeague", 1, 0, "Task");
    qmlRegisterType<Bubble>("ExpLeague", 1, 0, "Bubble");
    qmlRegisterType<ChatMessage>("ExpLeague", 1, 0, "ChatMessage");
    qmlRegisterType<Member>("ExpLeague", 1, 0, "Member");
    qmlRegisterType<TaskTag>("ExpLeague", 1, 0, "TaskTag");
    qmlRegisterType<AnswerPattern>("ExpLeague", 1, 0, "AnswerPattern");
    qmlRegisterType<PagesGroup>("ExpLeague", 1, 0, "PagesGroup");
    qmlRegisterType<Page>("ExpLeague", 1, 0, "Page");
    qmlRegisterType<League>("ExpLeague", 1, 0, "League");
    qmlRegisterType<CefItem>("ExpLeague", 1, 0, "CefItem");

    qmlRegisterUncreatableType<Profile>("ExpLeague", 1, 0, "Profile", "Profile requires registration and can be created only by appropriate builder class");
    qmlRegisterUncreatableType<doSearch>("ExpLeague", 1, 0, "doSearch", "This type is for root property only");
    qmlRegisterUncreatableType<Knugget>("ExpLeague", 1, 0, "Knugget", "Knuggets are created by vault only");
    qmlRegisterUncreatableType<GroupKnugget>("ExpLeague", 1, 0, "GroupKnugget", "Knuggets are created by vault only");
    qmlRegisterUncreatableType<PageVisit>("ExpLeague", 1, 0, "PageVisit", "Visits are created automatically");
    qmlRegisterUncreatableType<WebPage>("ExpLeague", 1, 0, "WebPage", "pages suppored to be created inside c++");
    qmlRegisterUncreatableType<SERPage>("ExpLeague", 1, 0, "SERPPage", "pages suppored to be created inside c++");
    qmlRegisterUncreatableType<RoomStatus>("ExpLeague", 1, 0, "RoomState", "rooms states are images of serverside object");
}

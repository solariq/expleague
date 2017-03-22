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
//#include "CrashHandler.h"

#include <cmath>

using namespace expleague;

void declareTypes();
void setupScreenDefaults();

QQmlApplicationEngine* rootEngine;
#ifndef Q_OS_MAC
QSystemTrayIcon* trayIcon;
#endif



//#include <QtCore/QDir>
//#include <QtCore/QProcess>
//#include <QtCore/QCoreApplication>
//#include <QString>
//#if defined(Q_OS_MAC)
//#include "client/mac/handler/exception_handler.h"
//#elif defined(Q_OS_LINUX)
//#include "client/linux/handler/exception_handler.h"
//#elif defined(Q_OS_WIN32)
//#include "client/windows/handler/exception_handler.h"
//#endif
//namespace Atomix
//{
//    /************************************************************************/
//    /* CrashHandlerPrivate                                                  */
//    /************************************************************************/
//    class CrashHandlerPrivate
//    {
//    public:
//        CrashHandlerPrivate()
//        {
//            pHandler = NULL;
//        }
//        ~CrashHandlerPrivate()
//        {
//            delete pHandler;
//        }
//        void InitCrashHandler(const QString& dumpPath);
//        static google_breakpad::ExceptionHandler* pHandler;
//        static bool bReportCrashesToSystem;
//    };
//    google_breakpad::ExceptionHandler* CrashHandlerPrivate::pHandler = NULL;
//    bool CrashHandlerPrivate::bReportCrashesToSystem = false;
//    /************************************************************************/
//    /* DumpCallback                                                         */
//    /************************************************************************/
//#if defined(Q_OS_WIN32)
//    bool DumpCallback(const wchar_t* _dump_dir,const wchar_t* _minidump_id,void* context,EXCEPTION_POINTERS* exinfo,MDRawAssertionInfo* assertion,bool success)
//#elif defined(Q_OS_LINUX)
//    bool DumpCallback(const google_breakpad::MinidumpDescriptor &md,void *context, bool success)
//#elif defined(Q_OS_MAC)
//    bool DumpCallback(const char* _dump_dir,const char* _minidump_id,void *context, bool success)
//#endif
//    {
//        Q_UNUSED(context);
//#if defined(Q_OS_WIN32)
//        Q_UNUSED(_dump_dir);
//        Q_UNUSED(_minidump_id);
//        Q_UNUSED(assertion);
//        Q_UNUSED(exinfo);
//#endif
//        qDebug() << "BreakpadQt crash" << _dump_dir;
//        /*
//        NO STACK USE, NO HEAP USE THERE !!!
//        Creating QString's, using qDebug, etc. - everything is crash-unfriendly.
//        */
//        return CrashHandlerPrivate::bReportCrashesToSystem ? success : true;
//    }
//    void CrashHandlerPrivate::InitCrashHandler(const QString& dumpPath)
//    {
//        if ( pHandler != NULL )
//            return;
//#if defined(Q_OS_WIN32)
//        std::wstring pathAsStr = (const wchar_t*)dumpPath.utf16();
//        pHandler = new google_breakpad::ExceptionHandler(
//            pathAsStr,
//            /*FilterCallback*/ 0,
//            DumpCallback,
//            /*context*/
//            0,
//            true
//            );
//#elif defined(Q_OS_LINUX)
//        std::string pathAsStr = dumpPath.toStdString();
//        google_breakpad::MinidumpDescriptor md(pathAsStr);
//        pHandler = new google_breakpad::ExceptionHandler(
//            md,
//            /*FilterCallback*/ 0,
//            DumpCallback,
//            /*context*/ 0,
//            true,
//            -1
//            );
//#elif defined(Q_OS_MAC)
//        std::string pathAsStr = dumpPath.toStdString();
//        pHandler = new google_breakpad::ExceptionHandler(
//            pathAsStr,
//            /*FilterCallback*/ 0,
//            DumpCallback,
//            /*context*/
//            0,
//            true,
//            NULL
//            );
//#endif
//    }
//    /************************************************************************/
//    /* CrashHandler                                                         */
//    /************************************************************************/
//    CrashHandler* CrashHandler::instance()
//    {
//        static CrashHandler globalHandler;
//        return &globalHandler;
//    }
//    CrashHandler::CrashHandler()
//    {
//        d = new CrashHandlerPrivate();
//    }
//    CrashHandler::~CrashHandler()
//    {
//        delete d;
//    }
//    void CrashHandler::setReportCrashesToSystem(bool report)
//    {
//        d->bReportCrashesToSystem = report;
//    }
//    bool CrashHandler::writeMinidump()
//    {
//        bool res = d->pHandler->WriteMinidump();
//        if (res) {
//            qDebug("BreakpadQt: writeMinidump() successed.");
//        } else {
//            qWarning("BreakpadQt: writeMinidump() failed.");
//        }
//        return res;
//    }
//    void CrashHandler:: Init( const QString& reportPath )
//    {
//        d->InitCrashHandler(reportPath);
//    }
//}

//#include "CrashHandler.h"
//int buggyFunc()
//{
//    delete reinterpret_cast<QString*>(0xFEE1DEAD);
//    return 0;
//}


int main(int argc, char *argv[]) {
    QTextCodec::setCodecForLocale(QTextCodec::codecForName("UTF-8"));
    QLocale::setDefault(QLocale(QLocale::English, QLocale::UnitedStates));
    QCoreApplication::setAttribute(Qt::AA_UseSoftwareOpenGL);
//    QGuiApplication::setAttribute(Qt::AA_UseHighDpiPixmaps);
    QCoreApplication::setOrganizationName("Experts League");
    QCoreApplication::setOrganizationDomain("expleague.com");
#ifdef QT_DEBUG
    QCoreApplication::setApplicationName("doSearch-debug");
#else
    QCoreApplication::setApplicationName("doSearch");
#endif
    QCoreApplication::setApplicationVersion(EL_DOSEARCH_VERSION);

//    Atomix::CrashHandler::instance()->Init(QStandardPaths::writableLocation(QStandardPaths::AppLocalDataLocation) + "/crash");
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

    qmlRegisterUncreatableType<Profile>("ExpLeague", 1, 0, "Profile", "Profile requires registration and can be created only by appropriate builder class");
    qmlRegisterUncreatableType<doSearch>("ExpLeague", 1, 0, "doSearch", "This type is for root property only");
    qmlRegisterUncreatableType<Knugget>("ExpLeague", 1, 0, "Knugget", "Knuggets are created by vault only");
    qmlRegisterUncreatableType<GroupKnugget>("ExpLeague", 1, 0, "GroupKnugget", "Knuggets are created by vault only");
    qmlRegisterUncreatableType<PageVisit>("ExpLeague", 1, 0, "PageVisit", "Visits are created automatically");
    qmlRegisterUncreatableType<WebPage>("ExpLeague", 1, 0, "WebPage", "pages suppored to be created inside c++");
    qmlRegisterUncreatableType<SERPage>("ExpLeague", 1, 0, "SERPPage", "pages suppored to be created inside c++");
    qmlRegisterUncreatableType<RoomStatus>("ExpLeague", 1, 0, "RoomState", "rooms states are images of serverside object");
}

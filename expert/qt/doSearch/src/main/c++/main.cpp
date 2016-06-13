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

#include "expleague.h"
#include "model/history.h"

using namespace expleague;

void declareTypes();
void setupScreenDefaults();

QQmlApplicationEngine* rootEngine;
#ifndef Q_OS_MAC
QSystemTrayIcon* trayIcon;
#endif

doSearch* root;

int main(int argc, char *argv[]) {
    QTextCodec::setCodecForLocale(QTextCodec::codecForName("UTF-8"));
    QApplication app(argc, argv);
#ifndef Q_OS_MAC
    trayIcon = new QSystemTrayIcon();
    trayIcon->setIcon(QIcon(":/avatar.png"));
    trayIcon->show();
#endif

    QtWebEngine::initialize();
    QQmlApplicationEngine engine;
    rootEngine = &engine;

    QCoreApplication::setApplicationVersion(QT_VERSION_STR);
    QQmlContext* context = engine.rootContext();

    setupScreenDefaults();

    root = new doSearch(&app);

    declareTypes();
    context->setContextProperty("root", root);
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

class StandardImageProvider: public QQuickImageProvider {
public:
    StandardImageProvider(): QQuickImageProvider(QQuickImageProvider::Pixmap) {}

    QPixmap requestPixmap(const QString& id, QSize*, const QSize &requestedSize) {
        QIcon icon;

        if (id.startsWith("SP_MessageBoxWarning")) {
             icon = m_style->standardIcon(QStyle::SP_MessageBoxWarning);
        }
        else if (id.startsWith("SP_TitleBarCloseButton")) {
             icon = m_style->standardIcon(QStyle::SP_TitleBarCloseButton);
        }
        else if (id.startsWith("SP_TitleBarMaxButton")) {
             icon = m_style->standardIcon(QStyle::SP_TitleBarMaxButton);
        }
        else if (id.startsWith("SP_TitleBarMinButton")) {
             icon = m_style->standardIcon(QStyle::SP_TitleBarMinButton);
        }

        if(icon.isNull())
            return QPixmap(requestedSize);

        QIcon::Mode mode = id.endsWith("_h") ? QIcon::Active : QIcon::Normal;
        QIcon::State state = id.endsWith("_d") ? QIcon::Off : QIcon::On;
        if (id.endsWith("_a"))
            mode = QIcon::Selected;

//        qDebug() << "Received request on " << id << " of size " << requestedSize << " mode: " << mode << " state: " << state <<  ". Found " << icon << " pixmap: " << icon.pixmap(requestedSize);
        return requestedSize.isValid() ? icon.pixmap(requestedSize, mode, state) : icon.pixmap(16, mode, state);
    }
private:
    QStyle* m_style = QApplication::style();
};

void declareTypes() {
    rootEngine->addImageProvider("standard", new StandardImageProvider);
    rootEngine->addImageProvider("store", doSearch::instance()->league()->store());

    qRegisterMetaType<Profile::Sex>("expleague::Profile::Sex");
    qRegisterMetaType<Profile*>("Profile*");
    qRegisterMetaType<Context*>("Context*");
    qRegisterMetaType<Folder*>("Folder*");
    qRegisterMetaType<Screen*>("Screen*");
    qRegisterMetaType<MarkdownEditorScreen*>("MarkdownEditorScreen*");
    qRegisterMetaType<expleague::Task*>("Task*");
    qRegisterMetaType<expleague::Bubble*>("Bubble*");
    qRegisterMetaType<expleague::ChatMessage*>("ChatMessage*");
    qRegisterMetaType<Offer*>("Offer*");
    qRegisterMetaType<doSearch*>("doSearch*");
    qRegisterMetaType<WebSearch*>("WebSearch*");
    qRegisterMetaType<Member*>("Member*");
    qRegisterMetaType<TaskTag*>("TaskTag*");
    qRegisterMetaType<AnswerPattern*>("AnswerPattern*");

    qmlRegisterType<ProfileBuilder>("ExpLeague", 1, 0, "ProfilePreview");
    qmlRegisterType<SearchRequest>("ExpLeague", 1, 0, "SearchRequest");
    qmlRegisterType<WebScreen>("ExpLeague", 1, 0, "WebScreen");
    qmlRegisterType<WebSearch>("ExpLeague", 1, 0, "WebSearch");
    qmlRegisterType<MarkdownEditorScreen>("ExpLeague", 1, 0, "MarkdownEditorScreen");
    qmlRegisterType<Context>("ExpLeague", 1, 0, "Context");
    qmlRegisterType<Offer>("ExpLeague", 1, 0, "Offer");
    qmlRegisterType<expleague::Task>("ExpLeague", 1, 0, "Task");
    qmlRegisterType<expleague::Bubble>("ExpLeague", 1, 0, "Bubble");
    qmlRegisterType<expleague::ChatMessage>("ExpLeague", 1, 0, "ChatMessage");
    qmlRegisterType<expleague::ReceivedAnswer>("ExpLeague", 1, 0, "ReceivedAnswer");
    qmlRegisterType<expleague::Member>("ExpLeague", 1, 0, "Member");
    qmlRegisterType<expleague::TaskTag>("ExpLeague", 1, 0, "TaskTag");
    qmlRegisterType<expleague::AnswerPattern>("ExpLeague", 1, 0, "AnswerPattern");
    qmlRegisterType<League>("ExpLeague", 1, 0, "League");

    qmlRegisterUncreatableType<Profile>("ExpLeague", 1, 0, "Profile", "Profile requires registration and can be created only by appropriate builder class");
    qmlRegisterUncreatableType<doSearch>("ExpLeague", 1, 0, "doSearch", "This type is for root property only");
    qmlRegisterUncreatableType<Screen>("ExpLeague", 1, 0, "Screen", "This is root class for all screens, please specify concrete screen type.");
    qmlRegisterUncreatableType<Folder>("ExpLeague", 1, 0, "Folder", "This is root class for all folders, please specify concrete folder type.");
}

namespace expleague {

doSearch::doSearch(QObject* parent) : QObject(parent), m_league(this) {
    QCoreApplication::setOrganizationName("Experts League");
    QCoreApplication::setOrganizationDomain("expleague.com");
    QCoreApplication::setApplicationName("doSearch");
    QApplication::setApplicationVersion(EL_DOSEARCH_VERSION);
    m_saver = new StateSaver(this);
}

doSearch* doSearch::instance() {
    return &*root;
}

void doSearch::restoreState() {
    m_saver->restoreState(this);
}
}

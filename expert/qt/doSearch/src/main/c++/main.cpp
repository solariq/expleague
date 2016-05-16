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

#include "expleague.h"
#include "model/history.h"

using namespace expleague;

void declareTypes();
void setupScreenDefaults();

QQmlApplicationEngine* rootEngine;
QSystemTrayIcon* trayIcon;

std::unique_ptr<doSearch> root;

int main(int argc, char *argv[]) {
    QApplication app(argc, argv);

    QtWebEngine::initialize();
    QQmlApplicationEngine engine;
    rootEngine = &engine;
//    QSystemTrayIcon myTrayIcon;
//    trayIcon = &myTrayIcon;
//    trayIcon->setVisible(false);
//    trayIcon->showMessage("Hello ", "World");

    QCoreApplication::setApplicationVersion(QT_VERSION_STR);
    QQmlContext* context = engine.rootContext();

    setupScreenDefaults();

    root.reset(new doSearch(&app));
    declareTypes();
    context->setContextProperty("root", root.get());
    root->restoreState();
    engine.load(QUrl(QStringLiteral("qrc:/Main.qml")));
    qDebug() << engine.rootObjects().first();
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
        QStyleOptionButton option;
        option.state = QStyle::State_Sunken/* : QStyle::State_Raised*/;
        option.features |= QStyleOptionButton::Flat;

        if (id == "SP_MessageBoxWarning") {
             icon = m_style->standardIcon(QStyle::SP_MessageBoxWarning, &option);
        }
        else if (id == "SP_TitleBarCloseButton") {
             icon = m_style->standardIcon(QStyle::SP_TitleBarCloseButton, &option);
        }
        else if (id == "SP_TitleBarMaxButton") {
             icon = m_style->standardIcon(QStyle::SP_TitleBarShadeButton);
        }
        else if (id == "SP_TitleBarMinButton") {
             icon = m_style->standardIcon(QStyle::SP_TitleBarUnshadeButton);
        }

        if(icon.isNull())
            return QPixmap(requestedSize);

//        qDebug() << "Received request on " << id << " of size " << requestedSize << ". Found " << icon << " pixmap: " << icon.pixmap(requestedSize);
        return requestedSize.isValid() ? icon.pixmap(requestedSize) : icon.pixmap(16);
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

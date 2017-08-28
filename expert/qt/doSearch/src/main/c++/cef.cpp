
#include "cef.h"

#include "model/pages/cefpage.h"

#if _MSC_VER && !__INTEL_COMPILER
#pragma warning (push, 0)
#else
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wall"
#endif

#include "include/cef_app.h"
#include "include/cef_client.h"
#include "include/cef_render_process_handler.h"
#include "include/cef_browser.h"

#if _MSC_VER && !__INTEL_COMPILER
#pragma warning (pop)
#else
#pragma GCC diagnostic pop
#endif


#include <QtCore>
#include <QSet>
#include <QStandardPaths>
#include <QApplication>
#include <chrono>

namespace expleague {


class QrcResourceHandler : public CefResourceHandler {
  virtual bool ProcessRequest(CefRefPtr<CefRequest> request,
                              CefRefPtr<CefCallback> callback) OVERRIDE //url format is qrc:///filename
  {
    QString str = "qrc:/" + (QString::fromStdString(request->GetURL().ToString())).mid(7);
    QUrl url = QUrl(str);
    m_file.setFileName(":/" + url.path());
    if (!m_file.open(QIODevice::ReadOnly)) {
      return false;
    }
    callback->Continue();
    return true;
  }

  virtual void GetResponseHeaders(CefRefPtr<CefResponse> response,
                                  int64& response_length,
                                  CefString& redirectUrl) OVERRIDE
  {
    response_length = m_file.size();
    QMimeDatabase db;
    //qDebug() << "get headers" << db.mimeTypeForFile(QFileInfo(m_file)).name() <<  response_length;
    response->SetMimeType(db.mimeTypeForFile(QFileInfo(m_file)).name().toStdString());
    response->SetStatus(200);
    response->SetStatusText("OK");
  }

  virtual bool ReadResponse(void* data_out,
                            int bytes_to_read,
                            int& bytes_read,
                            CefRefPtr<CefCallback> callback) OVERRIDE
  {
    //qDebug() << "readResponse " << bytes_to_read;
    bytes_read = m_file.read((char*) data_out, bytes_to_read);
    if (bytes_read <= 0) {
      m_file.close();
      return false;
    }
    return true;
  }

  virtual bool CanGetCookie(const CefCookie& cookie) OVERRIDE { return false; }

  virtual bool CanSetCookie(const CefCookie& cookie) OVERRIDE { return false; }

  virtual void Cancel() OVERRIDE {
    m_file.close();
  }

  QFile m_file;
IMPLEMENT_REFCOUNTING(CefResourceHandler)
};


class SchemeFactory : public CefSchemeHandlerFactory {
  virtual CefRefPtr<CefResourceHandler> Create(CefRefPtr<CefBrowser> browser,
                                               CefRefPtr<CefFrame> frame, const CefString& scheme_name,
                                               CefRefPtr<CefRequest> request) OVERRIDE {
    return new QrcResourceHandler();
  }



IMPLEMENT_REFCOUNTING(SchemeFactory)

};

static auto nextTimeToCallCef = std::chrono::high_resolution_clock::now();
QWaitCondition waitCondition;


class ProccessHandler: public CefBrowserProcessHandler{

  virtual void OnContextInitialized() {
    CefRegisterSchemeHandlerFactory("qrc", "", new SchemeFactory());
  }

  virtual void OnScheduleMessagePumpWork(int64 delay_ms) override {
//    if (delay_ms <= 0 || delay_ms > 1000)
//      return;
//    auto next = std::chrono::high_resolution_clock::now();
//    next += std::chrono::milliseconds(delay_ms);
//    nextTimeToCallCef = next;

    if(delay_ms >= 0)
    QMetaObject::invokeMethod(&worker, "doWork", Qt::QueuedConnection, Q_ARG(int, delay_ms));
  }

  CefWorker worker;
  IMPLEMENT_REFCOUNTING(ProccessHandler)
};

QSet<ShutDownGCItem*> GCStorage;

QSet<ShutDownGCItem*> GCDestroyed;
bool GCStorageBusy = false;
QTimer* cefTimer = nullptr;

void ShutDownGCItem::addToShutDownGC() {
  GCStorage.insert(this);
//  qDebug() << "insert" << GCStorage.size();
}

void ShutDownGCItem::removeFromShutDownGC() {
//  qDebug() << "remove" << GCStorage.size() << GCDestroyed.size() << GCStorageBusy;
  if (GCStorageBusy) {
    GCDestroyed.insert(this);
    return;
  }
  GCStorage.remove(this);
}

QString cachePath() {
  QString appLocalPath = QStandardPaths::writableLocation(QStandardPaths::AppLocalDataLocation);
  QDir cache_dir(appLocalPath + "/" + "BrowserCache");
  if (!cache_dir.exists()) {
    QDir(appLocalPath).mkdir("BrowserCache");
  }
  return cache_dir.absolutePath();
}

class CefAppImpl: public CefApp{
  virtual void OnBeforeCommandLineProcessing(const CefString& process_type, CefRefPtr<CefCommandLine> command_line){
    command_line->AppendSwitch("--enable-system-flash");
    command_line->AppendSwitch("--disable-gpu-compositing");
  }

  virtual CefRefPtr<CefBrowserProcessHandler> GetBrowserProcessHandler() {
    return new ProccessHandler();
  }

  virtual void OnRegisterCustomSchemes(CefRawPtr<CefSchemeRegistrar> registrar) OVERRIDE { //TODO try is_local true and is_cors_enabled fasle
    #if defined(__unix__) || (defined (__APPLE__) && defined (__MACH__))
    registrar->AddCustomScheme("qrc", false, false, false, false, true, true);
    #else
    registrar->AddCustomScheme("qrc", false, false, false, false, true);
    #endif
  }

  IMPLEMENT_REFCOUNTING(CefAppImpl)
};

CefWorker::CefWorker(){
  m_timer.setSingleShot(false);
  QObject::connect(&m_timer, SIGNAL(timeout()), this, SLOT(doWork()));
};

void CefWorker::doWork(int delay) {
  if(delay == 0) {
    CefDoMessageLoopWork();
    return;
  }
  m_timer.stop();
  m_timer.setInterval(qMin(20, delay));
  m_timer.start();
}

void initCef(int argc, char* argv[]) {
  CefRefPtr<CefApp> cefapp(new CefAppImpl());
  CefSettings settings;
  settings.external_message_pump = 1;

  CefString cache_path(&settings.cache_path);
  QString appLocalPath = QStandardPaths::writableLocation(QStandardPaths::AppLocalDataLocation);
  QDir cache_dir(appLocalPath);
  cache_dir.mkdir("browserCache");
  cache_dir.cd("browserCache");
  cache_path = cache_dir.absolutePath().toStdString();

  #ifdef Q_OS_MAC
  CefMainArgs main_args(argc, argv);
//    CefString(&settings.browser_subprocess_path).FromASCII("cef/cef-instance");
  #else
  CefMainArgs main_args(GetModuleHandle(NULL));
  CefString(&settings.browser_subprocess_path).FromASCII("cef-exec.exe");
  #endif

  CefInitialize(main_args, settings, cefapp, NULL);

  cefTimer = new QTimer();
  cefTimer->setInterval(1);
  cefTimer->setSingleShot(true);
  QObject::connect(cefTimer, &QTimer::timeout, []() {
      CefDoMessageLoopWork();
//    qDebug() << "Work Done";
      cefTimer->setInterval(1);
      cefTimer->start();
  });
  cefTimer->start();
}

//class FlushCallBack: public CefCompletionCallback{
//public:
//    virtual void OnComplete(){
//        CefShutdown();
//    }
//    IMPLEMENT_REFCOUNTING(FlushCallBack)
//};

QTimer* shutDownTimer;

void shutDownCef(std::function<void()> callback) {
  GCStorageBusy = true;
  for (auto browser: GCStorage) {
    browser->shutDown();
  }
  GCStorageBusy = false;
  for (auto browser: GCDestroyed) {
    GCStorage.remove(browser);
  }
  shutDownTimer = new QTimer();
  shutDownTimer->setInterval(0);
  QObject::connect(shutDownTimer, &QTimer::timeout, [callback]() {
    if (GCStorage.empty()) {
      CefShutdown();
      callback();
      cefTimer->stop();
      shutDownTimer->stop();
      cefTimer->deleteLater();
      shutDownTimer->deleteLater();
    }
  });
  shutDownTimer->start();
}

}


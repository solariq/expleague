
#include "cef.h"

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

#include <chrono>

namespace expleague {


class QrcResourceHandler: public CefResourceHandler{
    virtual bool ProcessRequest(CefRefPtr<CefRequest> request,
                                CefRefPtr<CefCallback> callback) OVERRIDE //url format is qrc:///filename
    {
        QString str = "qrc:/" + (QString::fromStdString(request->GetURL().ToString())).mid(7);
        QUrl url = QUrl(str);
        m_file.setFileName(":/" + url.path());
        if(!m_file.open(QIODevice::ReadOnly)){
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
        bytes_read = m_file.read((char*)data_out, bytes_to_read);
        if(bytes_read <= 0){
            m_file.close();
            return false;
        }
        return true;
    }

    virtual bool CanGetCookie(const CefCookie& cookie) OVERRIDE { return false; }

    virtual bool CanSetCookie(const CefCookie& cookie) OVERRIDE { return false; }

    virtual void Cancel() OVERRIDE{
        m_file.close();
    }
    QFile m_file;
    IMPLEMENT_REFCOUNTING(CefResourceHandler)
};


class SchemeFactory: public CefSchemeHandlerFactory{
    virtual CefRefPtr<CefResourceHandler> Create(CefRefPtr<CefBrowser> browser,
        CefRefPtr<CefFrame> frame, const CefString& scheme_name,
        CefRefPtr<CefRequest> request) OVERRIDE
    {
        return new QrcResourceHandler();
    }
    IMPLEMENT_REFCOUNTING(SchemeFactory)
};

//class CefRenderProcessHandlerImpl: public CefRenderProcessHandler{
//    virtual void OnContextCreated(CefRefPtr<CefBrowser> browser,
//                                  CefRefPtr<CefFrame> frame,
//                                  CefRefPtr<CefV8Context> context) {
//        if(!frame->IsMain()){
//            return;
//        }
//        CefRefPtr<CefV8Value> object = context->GetGlobal();
//        CefRefPtr<CefV8Value> str = CefV8Value::CreateString("");
//        object->SetValue("text", str, V8_PROPERTY_ATTRIBUTE_NONE);

//    }
//};


QSet<Browser*> GCStorage;
QSet<Browser*> GCDestroyed;
bool GCStorageBusy = false;

QTimer* cefTimer = nullptr;

void Browser::addCefBrowserToGC(){
    GCStorage.insert(this);
    qDebug() << "insert" << GCStorage.size();
}

void Browser::removeCefBrowserFromGC(){
    qDebug() << "remove" << GCStorage.size() << GCDestroyed.size() << GCStorageBusy;
    if(GCStorageBusy){
        GCDestroyed.insert(this);
        return;
    }
    GCStorage.remove(this);
}

QString cachePath(){
    QString appLocalPath = QStandardPaths::writableLocation(QStandardPaths::AppLocalDataLocation);
    QDir cache_dir(appLocalPath + "/" + "BrowserCache");
    if(!cache_dir.exists()){
        QDir(appLocalPath).mkdir("BrowserCache");
    }
    return cache_dir.absolutePath();
}


void initCef(int argc, char *argv[]) {
    CefRefPtr<CefApp> cefapp;
    CefSettings settings;

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
    CefString(&settings.browser_subprocess_path).FromASCII("cef\\cef-exec.exe");
  #endif

    CefInitialize(main_args, settings, cefapp, NULL);
    CefRegisterSchemeHandlerFactory("qrc", "", new SchemeFactory());

    cefTimer = new QTimer();
    cefTimer->setInterval(1);
    cefTimer->setSingleShot(false);
    QObject::connect(cefTimer, &QTimer::timeout, [](){
        auto start = std::chrono::high_resolution_clock::now();
        CefDoMessageLoopWork();
        auto finish = std::chrono::high_resolution_clock::now();
        std::chrono::duration<_int64, std::nano> dif = std::chrono::duration_cast<std::chrono::nanoseconds>(finish-start);
        int interval = cefTimer->interval();
        if(dif.count() < 10000){
            if(interval < 100){
                cefTimer->setInterval(cefTimer->interval());
            }
        }else if(dif.count() > 100000 && cefTimer->interval() > 0){
            cefTimer->setInterval(interval == 1 ? 1 : interval/2);
        }
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

void shutDownCef(std::function<void()> callback){
    GCStorageBusy = true;
    for(Browser* browser: GCStorage){
        browser->shutDown();
    }
    GCStorageBusy = false;
    for(Browser* browser: GCDestroyed){
        GCStorage.remove(browser);
    }
    shutDownTimer = new QTimer();
    shutDownTimer->setInterval(0);
    QObject::connect(shutDownTimer, &QTimer::timeout, [callback](){
      if(GCStorage.empty()){
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


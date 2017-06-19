
#include "include/cef_app.h"
#include "include/cef_client.h"
#include "include/cef_render_process_handler.h"

#include "model/pages/cefpage.h"

#include "cef.h"
#include "model/pages/cefpage.h"

#include <QSet>

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

void Browser::shutDownCallBack(){
    qDebug() << "callback" << GCStorage.size() << GCDestroyed.size() << GCStorageBusy;
    if(GCStorageBusy){
        GCDestroyed.insert(this);
        return;
    }
    GCStorage.remove(this);
}


void initCef(int argc, char *argv[]) {
    CefRefPtr<CefApp> cefapp;
    CefSettings settings;
  #ifdef Q_OS_MAC
    CefMainArgs main_args(argc, argv);
//    CefString(&settings.browser_subprocess_path).FromASCII("cef/cef-instance");
  #else
    CefMainArgs main_args(GetModuleHandle(NULL));
    CefString(&settings.browser_subprocess_path).FromASCII("cef/cef-instance.exe");
  #endif
    CefInitialize(main_args, settings, cefapp, NULL);
    CefRegisterSchemeHandlerFactory("qrc", "", new SchemeFactory());

    cefTimer = new QTimer();
    cefTimer->setInterval(0);
    cefTimer->setSingleShot(false);
    QObject::connect(cefTimer, &QTimer::timeout, [](){
      CefDoMessageLoopWork();
    });
    cefTimer->start();
}

void shutDownCef(){
    GCStorageBusy = true;
    for(Browser* browser: GCStorage){
        browser->shutDown();
    }
    GCStorageBusy = false;
    for(Browser* browser: GCDestroyed){
        GCStorage.remove(browser);
    }
    while(!GCStorage.empty()){
        CefDoMessageLoopWork();
    }
    CefShutdown();
    if(cefTimer) delete cefTimer;
}

}


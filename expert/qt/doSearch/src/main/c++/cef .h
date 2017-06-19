#ifndef CEF_H
#define CEF_H

#if _MSC_VER && !__INTEL_COMPILER
#pragma warning (push, 0)
#endif

#include "include/cef_app.h"
#include "include/cef_client.h"
#include "include/cef_render_process_handler.h"
#include "include/cef_browser.h"

#if _MSC_VER && !__INTEL_COMPILER
#pragma warning (pop)
#endif

#include <QtCore>
#include <QQuickWindow>

namespace expleague {



class Browser{
public:
    struct InitSettings;
    class Listener;

public:
    CefRefPtr<CefBrowser> operator->();
    void init(const InitSettings& settings);
    void stop();
    void release();
    operator bool();
    static void stopAll();
private:
    void addToGC();
    void removeFromGC();
    CefRefPtr<CefBrowser> m_browser = nullptr;
};

struct Browser::InitSettings{
    QUrl url;
    QString html;
    bool cookies;
    bool redirect;
    CefRefPtr<CefClient> client;
    QQuickWindow* window;
};

void initCef();

void shutDownCef();

}

#endif // CEF_H

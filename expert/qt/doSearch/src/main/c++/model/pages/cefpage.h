#ifndef CEFPAGE_H
#define CEFPAGE_H

#include "../page.h"

#include <QQuickFramebufferObject>
#include <QTimer>
#include <QtOpenGL>
#include <mutex>

#include "include/cef_app.h"
#include "include/cef_client.h"
#include "include/cef_render_process_handler.h"
#include "include/cef_browser.h"

class CefItem;
class CefPageRenderer;

struct image_buffer{
    int width;
    int height;
    const void* data;
};


class QTPageRenderer: public QQuickFramebufferObject::Renderer{
public:
    QTPageRenderer(CefRefPtr<CefPageRenderer> renderer);
    //Qt methods
    virtual void render();
    virtual QOpenGLFramebufferObject *createFramebufferObject(const QSize &size);
    virtual void synchronize(QQuickFramebufferObject *obj);
    //Cef methods

    virtual bool GetScreenPoint(CefRefPtr<CefBrowser> browser, int viewX, int viewY,
                                int& screenX, int& screenY);
    void clearBuffer();
    virtual ~QTPageRenderer();
private:
    CefRefPtr<CefPageRenderer> m_renderer;
    QQuickWindow* m_window;
    GLuint m_screen_tex = 0;
};

class CefPageRenderer: public CefRenderHandler{
public:
    CefPageRenderer();
    void setSize(int height, int width);
    void clearBuffer();
    void initTexture();
    virtual bool GetViewRect(CefRefPtr<CefBrowser> browser, CefRect& rect);
    virtual void OnPaint(CefRefPtr<CefBrowser> browser, PaintElementType type,
                         const RectList& dirtyRects, const void* buffer,
                         int width, int height);
    IMPLEMENT_REFCOUNTING(CefPageRenderer)
private:
    image_buffer m_buffer;
    int m_new_height;
    int m_new_width;
    bool isValid;
    std::mutex m_mutex;
};


class BrowserListener: public CefRequestHandler {
public:
    BrowserListener(CefItem* owner): m_owner(owner){}
    virtual bool OnBeforeBrowse(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame,
                                CefRefPtr<CefRequest> request, bool is_redirect);
    virtual bool OnOpenURLFromTab(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame,
                                  const CefString& target_url, WindowOpenDisposition target_disposition,
                                  bool user_gesture);
    IMPLEMENT_REFCOUNTING(CefRequestHandler)
private:
    bool m_first = true;
    CefItem* m_owner;
};



class CefItem: public QQuickFramebufferObject{
    Q_OBJECT
    Q_PROPERTY(QUrl url READ url WRITE setUrl NOTIFY urlChanged)

public:
    CefItem(QQuickItem *parent = 0);
    ~CefItem();
    virtual Renderer *createRenderer() const;
    virtual void releaseResources();
private:
    CefRefPtr<CefPageRenderer> m_renderer;
    CefRefPtr<CefBrowser> m_browser;
    QTimer* m_timer;

    virtual void mouseMoveEvent(QMouseEvent *event);
    virtual void mousePressEvent(QMouseEvent *event);
    virtual void mouseReleaseEvent(QMouseEvent *event);
    virtual void wheelEvent(QWheelEvent* event);
    cef_mouse_button_type_t m_last_mouse_button;

    virtual void keyPressEvent(QKeyEvent *event);
    virtual void keyReleaseEvent(QKeyEvent *event);

private:
    QUrl m_url;

public:
    QUrl url() const;
    void setUrl(const QUrl& url);

signals:
    void urlChanged(const QUrl& url);
    void requestPage(QUrl url, bool newTab);

private slots:
    void resize();
    void destroy();
    void initBrowser(QQuickWindow* window);
    void doCefWork();
};

//class CefPage: public Page{
//public:
//    CefPage();
//    virtual QQuickItem* ui(bool useCache = true) const;
//private:
//    QQuickItem* cef_ui;
//};



#endif // CEFPAGE_H

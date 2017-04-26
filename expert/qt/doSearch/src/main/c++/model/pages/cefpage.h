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


class CefPageRenderer: public QQuickFramebufferObject::Renderer, public CefRenderHandler{
public:
    CefPageRenderer(int item_width, int item_height);
    //Qt methods
    virtual void render();
    virtual QOpenGLFramebufferObject *createFramebufferObject(const QSize &size);
    virtual void synchronize(QQuickFramebufferObject *obj);
    //Cef methods
    virtual bool GetViewRect(CefRefPtr<CefBrowser> browser, CefRect& rect);
    virtual void OnPaint(CefRefPtr<CefBrowser> browser, PaintElementType type,
                         const RectList& dirtyRects, const void* buffer,
                         int width, int height);
    virtual bool GetScreenPoint(CefRefPtr<CefBrowser> browser, int viewX, int viewY,
                                int& screenX, int& screenY);
    IMPLEMENT_REFCOUNTING(CefPageRenderer)
private:
    std::mutex m_mutex;
    QQuickItem* m_owner;
    const void* m_buffer = 0;
    int m_buffer_width;
    int m_buffer_height;
    int m_item_width;
    int m_item_height;
    GLuint m_screen_tex = 0;
};



class CefItem: public QQuickFramebufferObject{
    Q_OBJECT
public:
    CefItem(QQuickItem *parent = 0);
    ~CefItem();
    virtual Renderer *createRenderer() const;
private:
    CefRefPtr<CefPageRenderer> m_renderer;
    CefRefPtr<CefClient> m_cef_client;
    CefRefPtr<CefBrowser> m_browser;
    QTimer* m_timer;

    virtual void mouseMoveEvent(QMouseEvent *event);
    virtual void mousePressEvent(QMouseEvent *event);
    virtual void mouseReleaseEvent(QMouseEvent *event);
    virtual void wheelEvent(QWheelEvent* event);
    cef_mouse_button_type_t m_last_mouse_button;

    virtual void keyPressEvent(QKeyEvent *event);
    virtual void keyReleaseEvent(QKeyEvent *event);

private slots:
    void initBrowser();
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

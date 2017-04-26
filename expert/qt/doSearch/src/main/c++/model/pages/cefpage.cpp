#include "cefpage.h"
#include "include/cef_app.h"
#include "include/cef_client.h"
#include "include/cef_render_process_handler.h"
#include "include/wrapper/cef_helpers.h"
#include <QtOpenGL>
#include <QQuickWindow>



CefPageRenderer::CefPageRenderer(int item_width, int item_height): m_item_width(item_width), m_item_height(item_height){

}
QOpenGLFramebufferObject *CefPageRenderer::createFramebufferObject(const QSize &size){
    return new QOpenGLFramebufferObject(size);
}

//QT reder thread
void CefPageRenderer::render(){
    //qDebug() << "buffer:" << m_buffer;
    m_owner->window()->resetOpenGLState();
    this->framebufferObject()->bind();
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
    if(!m_screen_tex){
        glGenTextures(1, &m_screen_tex);
        glBindTexture(GL_TEXTURE_2D, m_screen_tex);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_MIRRORED_REPEAT);
    }
    glEnable(GL_TEXTURE_2D);
    glBindTexture(GL_TEXTURE_2D, m_screen_tex);
    m_mutex.lock();
    if(m_buffer){
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, m_buffer_width, m_buffer_height, 0, GL_BGRA, GL_UNSIGNED_BYTE, m_buffer);
    }
    m_mutex.unlock();
    glBegin(GL_QUADS);
    glTexCoord2f(0.f, 0.f); glVertex2f(-1.f,-1.f);
    glTexCoord2f(0.f, 1.f); glVertex2f(-1.f, 1.f);
    glTexCoord2f(1.f, 1.f); glVertex2f(1.f, 1.f);
    glTexCoord2f(1.f, 0.f); glVertex2f(1.f, -1.f);
    glEnd();

    glDisable(GL_TEXTURE_2D);
    m_owner->window()->resetOpenGLState();
}

//Qt render thread,  ui (onpaint, CefDoMessageLoopWork, QQuickFramebufferObject) blocked
void CefPageRenderer::synchronize(QQuickFramebufferObject *obj){
    m_item_height = (int)obj->height();
    m_item_width = (int)obj->width();
    m_owner = obj;
}

//ui thread
bool CefPageRenderer::GetViewRect(CefRefPtr<CefBrowser> browser, CefRect& rect){
    rect = CefRect(0, 0, m_item_width, m_item_height);
    return true;
}

//ui thread
void CefPageRenderer::OnPaint(CefRefPtr<CefBrowser> browser,
                     PaintElementType type,
                     const RectList& dirtyRects,
                     const void* buffer,
                     int width, int height){
    if((m_item_width != width) || (m_item_height != height)){
        m_mutex.lock();
        m_buffer = 0;
        m_mutex.unlock();
        browser.get()->GetHost().get()->WasResized();
        return;
    }

    m_mutex.lock();
    m_buffer = buffer;
    m_buffer_width = width;
    m_buffer_height = height;
    m_mutex.unlock();
    update(); //call render
}

bool CefPageRenderer::GetScreenPoint(CefRefPtr<CefBrowser> browser, int viewX, int viewY,
                                     int& screenX, int& screenY){
    screenX = viewX;
    screenY = viewY;
    return true;
}


CefItem::CefItem(QQuickItem *parent): QQuickFramebufferObject(parent){
    CEF_REQUIRE_UI_THREAD()
    qDebug() << "Thread:" << QThread::currentThread();
    qDebug() << "Object live in:" << thread();
    m_timer = new QTimer(this);
    m_timer->setSingleShot(false);
    m_timer->setInterval(5);
    m_timer->setTimerType(Qt::TimerType::CoarseTimer);
    QObject::connect(m_timer, SIGNAL(timeout()), this, SLOT(doCefWork()));
    QObject::connect(this, SIGNAL(windowChanged(QQuickWindow*)), this, SLOT(initBrowser()), Qt::QueuedConnection);
    m_timer->start();
}

void CefItem::doCefWork(){
    CefDoMessageLoopWork();
}

CefItem::~CefItem(){
}

QQuickFramebufferObject::Renderer* CefItem::createRenderer() const{
    return m_renderer.get();
}


class ACefClient: public CefClient{
public:
    void set(CefRefPtr<CefRenderHandler> renderer){
        m_renderer = renderer;
    }
    virtual CefRefPtr<CefRenderHandler> GetRenderHandler(){
        return m_renderer;
    }
private:
    CefRefPtr<CefRenderHandler> m_renderer;
    IMPLEMENT_REFCOUNTING(ACefClient)
};

void CefItem::initBrowser(){
    QQuickWindow* w = window();
    assert(w);
    m_renderer = new CefPageRenderer(w->width(), w->height());
    CefRefPtr<ACefClient> acefclient = new ACefClient();
    acefclient->set(m_renderer);
    m_cef_client = acefclient;
    CefWindowInfo mainWindowInfo;
    mainWindowInfo.SetAsWindowless((HWND) w->winId(), false);
    CefString url;
    url.FromASCII("https://www.google.com");
    CEF_REQUIRE_UI_THREAD()
    m_browser = CefBrowserHost::CreateBrowserSync(mainWindowInfo, m_cef_client, url, CefBrowserSettings(), nullptr);
    this->setAcceptedMouseButtons(Qt::LeftButton | Qt::RightButton | Qt::MiddleButton);
}


void CefItem::mouseMoveEvent(QMouseEvent *event){
    cef_mouse_event_t cef_event;
    cef_event.x = event->x();
    cef_event.y = event->y();
    cef_event.modifiers = 0;
    CefMouseEvent event_container;
    event_container.Set(cef_event, true);
    m_browser->GetHost()->SendMouseMoveEvent(event_container, false);
}

void CefItem::mousePressEvent(QMouseEvent *event){
    cef_mouse_event_t cef_event;
    cef_event.x = event->x();
    cef_event.y = event->y();
    cef_event.modifiers = 0;
    CefMouseEvent event_container;
    event_container.Set(cef_event, true);
    if(event->buttons() & Qt::LeftButton){
        m_last_mouse_button = MBT_LEFT;
    }
    else if(event->buttons() & Qt::RightButton){
        m_last_mouse_button = MBT_RIGHT;
    }
    else if(event->buttons() & Qt::MiddleButton){
        m_last_mouse_button = MBT_MIDDLE;
    }else{
        return;
    }
    m_browser->GetHost()->SendMouseClickEvent(event_container, m_last_mouse_button, false, 1);
}

void CefItem::mouseReleaseEvent(QMouseEvent *event){
    cef_mouse_event_t cef_event;
    cef_event.x = event->x();
    cef_event.y = event->y();
    cef_event.modifiers = 0;
    CefMouseEvent event_container;
    event_container.Set(cef_event, true);
    m_browser->GetHost().get()->SendMouseClickEvent(event_container, m_last_mouse_button, true, 1);
}

const float WHEEL_SENSITIVITY = 1;

void CefItem::wheelEvent(QWheelEvent* event){
    event->accept();
    cef_mouse_event_t cef_event;
    cef_event.x = event->x();
    cef_event.y = event->y();
    cef_event.modifiers = 0;
    CefMouseEvent event_container;
    m_browser->GetHost()->SendMouseWheelEvent(event_container, event->angleDelta().x(), event->angleDelta().y());
}

void CefItem::keyPressEvent(QKeyEvent *event){
    cef_key_event_t cef_event;
    cef_event.type = KEYEVENT_KEYDOWN;
    cef_event.modifiers = 0;
    cef_event.native_key_code = event->key();
    CefKeyEvent event_container;
    event_container.Set(cef_event, true);
    m_browser->GetHost()->SendKeyEvent(event_container);
}

void CefItem::keyReleaseEvent(QKeyEvent *event){
    cef_key_event_t cef_event;
    cef_event.type = KEYEVENT_KEYUP;
    cef_event.modifiers = 0;
    cef_event.native_key_code = event->key();
    CefKeyEvent event_container;
    event_container.Set(cef_event, true);
    m_browser->GetHost()->SendKeyEvent(event_container);
}

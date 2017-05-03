#include "cefpage.h"
#include "include/cef_app.h"
#include "include/cef_client.h"
#include "include/cef_render_process_handler.h"
#include "include/wrapper/cef_helpers.h"
#include <QtOpenGL>
#include <QQuickWindow>

int browser_count;



QOpenGLFramebufferObject *QTPageRenderer::createFramebufferObject(const QSize &size){
    return new QOpenGLFramebufferObject(size);
}

QTPageRenderer::QTPageRenderer(CefRefPtr<CefPageRenderer> renderer): m_renderer(renderer){

}

QTPageRenderer::~QTPageRenderer(){
    qDebug() << "Destroy renderer";
}

//QT reder thread
void QTPageRenderer::render(){
    m_window->resetOpenGLState();
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
    m_renderer->initTexture();
    glBegin(GL_QUADS);
    glTexCoord2f(0.f, 0.f); glVertex2f(-1.f,-1.f);
    glTexCoord2f(0.f, 1.f); glVertex2f(-1.f, 1.f);
    glTexCoord2f(1.f, 1.f); glVertex2f(1.f, 1.f);
    glTexCoord2f(1.f, 0.f); glVertex2f(1.f, -1.f);
    glEnd();
    glDisable(GL_TEXTURE_2D);
    m_window->resetOpenGLState();
    update(); //call render next frame
}

CefPageRenderer::CefPageRenderer(): isValid(true){
    m_buffer.data = 0;
}

//Qt render thread,  ui (onpaint, CefDoMessageLoopWork, QQuickFramebufferObject) blocked
void QTPageRenderer::synchronize(QQuickFramebufferObject *obj){
    m_window = obj->window();
}

//ui thread
void CefPageRenderer::setSize(int width, int height){
    CEF_REQUIRE_UI_THREAD()
    this->clearBuffer();
    m_new_height = height;
    m_new_width = width;
}

void CefPageRenderer::clearBuffer(){
    m_mutex.lock();
    m_buffer.data = 0;
    m_mutex.unlock();
}

void CefPageRenderer::initTexture(){
    m_mutex.lock();
    if(m_buffer.data){
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, m_buffer.width, m_buffer.height, 0, GL_BGRA, GL_UNSIGNED_BYTE, m_buffer.data);
    }
    m_mutex.unlock();
}

//ui thread
bool CefPageRenderer::GetViewRect(CefRefPtr<CefBrowser> browser, CefRect& rect){
    CEF_REQUIRE_UI_THREAD()

    rect = CefRect(0, 0, m_new_width, m_new_height);
    return true;
}



//ui thread
void CefPageRenderer::OnPaint(CefRefPtr<CefBrowser> browser,
                     PaintElementType type,
                     const RectList& dirtyRects,
                     const void* buffer,
                     int width, int height){
    CEF_REQUIRE_UI_THREAD()
    if(m_new_height != height || m_new_width != width){
        return;
    }
    m_mutex.lock();
    m_buffer.data = buffer;
    m_buffer.width = width;
    m_buffer.height = height;
    m_mutex.unlock();
}

bool QTPageRenderer::GetScreenPoint(CefRefPtr<CefBrowser> browser, int viewX, int viewY,
                                     int& screenX, int& screenY){
    screenX = viewX;
    screenY = viewY;
    return true;
}


bool BrowserListener::OnBeforeBrowse(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame,
                                   CefRefPtr<CefRequest> request, bool is_redirect){
    if(m_first){
        m_first = false;
        return false;
    }
    QString url = QString::fromStdString(request->GetURL().ToString());
    QUrl qurl(url, QUrl::TolerantMode);
    qDebug() << "On before browse" << qurl;
    emit m_owner->requestPage(qurl, false);
    return true;
}
bool BrowserListener::OnOpenURLFromTab(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame,
                                      const CefString &target_url, WindowOpenDisposition target_disposition,
                                      bool user_gesture){

    QUrl url(QString::fromStdString(target_url.ToString()), QUrl::TolerantMode);
    qDebug() << "OnOpenURLFromTab" << url;
    emit m_owner->requestPage(url, true);
    return true;
}


CefItem::CefItem(QQuickItem *parent): QQuickFramebufferObject(parent){
    CEF_REQUIRE_UI_THREAD()
    qDebug() << "construct item" << this << "with parent" << parent;
    m_renderer = new CefPageRenderer();
    m_timer = new QTimer(this);
    m_timer->setSingleShot(false);
    m_timer->setInterval(5);
    m_timer->setTimerType(Qt::TimerType::CoarseTimer);
    QObject::connect(m_timer, SIGNAL(timeout()), this, SLOT(doCefWork()));
    QObject::connect(this, SIGNAL(windowChanged(QQuickWindow*)), this, SLOT(initBrowser(QQuickWindow*)), Qt::QueuedConnection);
    //QObject::connect(this, SIGNAL(destroyed(QObject*)), this, SLOT(destroyBrowser()));
    QObject::connect(this, SIGNAL(widthChanged()), this, SLOT(resize()));
    QObject::connect(this, SIGNAL(heightChanged()), this, SLOT(resize()));
    QObject::connect(this, SIGNAL(destroyed(QObject*)), this, SLOT(destroy()));
    m_timer->start();
}

void CefItem::doCefWork(){
    CefDoMessageLoopWork();
}

CefItem::~CefItem(){
}

QQuickFramebufferObject::Renderer* CefItem::createRenderer() const{
    return new QTPageRenderer(m_renderer);
}


class ACefClient: public CefClient{
public:
    void set(CefRefPtr<CefRenderHandler> renderer){
        m_renderer = renderer;
    }
    void setBrowserListener(CefRefPtr<BrowserListener> listener){
        m_listener = listener;
    }

    virtual CefRefPtr<CefRequestHandler> GetRequestHandler(){
        return m_listener;
    }
    virtual CefRefPtr<CefRenderHandler> GetRenderHandler(){
        return m_renderer;
    }

private:
    CefRefPtr<CefRenderHandler> m_renderer;
    CefRefPtr<BrowserListener> m_listener;
    IMPLEMENT_REFCOUNTING(ACefClient)
};

void CefItem::resize(){
    if(m_browser){
        m_renderer->setSize((int)width(), (int)height());
        m_browser->GetHost()->WasResized();
    }
}

void CefItem::releaseResources(){
    CEF_REQUIRE_UI_THREAD()
    qDebug() << "Destroying Browser " << m_url;
    m_renderer->clearBuffer();
    m_browser->GetHost()->CloseBrowser(true);
    m_browser = nullptr;
}

void CefItem::destroy(){
    qDebug() << "destroy CefItem";
}

void CefItem::initBrowser(QQuickWindow* window){
    CEF_REQUIRE_UI_THREAD()
    if(!window){
        return;
    }
    m_renderer->setSize(width(), height());
    qDebug() << "Init Browser " << m_url;
    CefRefPtr<ACefClient> acefclient = new ACefClient();
    acefclient->set(m_renderer);
    acefclient->setBrowserListener(new BrowserListener(this));
    CefWindowInfo mainWindowInfo;
    mainWindowInfo.SetAsWindowless((HWND) window->winId(), false);
    CefString url(m_url.toString().toStdString());
    m_browser = CefBrowserHost::CreateBrowserSync(mainWindowInfo, acefclient, url, CefBrowserSettings(), NULL);
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


QUrl CefItem::url() const{
    return m_url;
}

void CefItem::setUrl(const QUrl &url){
    qDebug() << "emit url change" << url;
    if(m_browser.get()){
        m_browser->GetMainFrame()->LoadURL(CefString(url.toString().toStdString()));
    }
    m_url = url;
    emit urlChanged(url);
}

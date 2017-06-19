#include "cefpage.h"

#include "include/wrapper/cef_helpers.h"
#include <QtQuick/private/qquickdroparea_p.h>
#include <QtOpenGL>
#include <QQuickWindow>
#include <QQmlEngine>

namespace expleague {

//void downloadSmallFile(const QUrl& url, const QString& destination, std::function<void()> callback){
//    QNetworkAccessManager manager;
//    QNetworkReply* replay = manager.get(QNetworkRequest(url));
//    QObject::connect(replay, &QNetworkReply::finished, [replay, callback, destination](){
//        QFile file(destination);
//        if(!file.open(QFile::WriteOnly)){
//            qDebug() << "ERROR: unable download file, wrong name";
//            return;
//        }
//        file.write(replay->readAll());
//        file.close();
//        replay->deleteLater();
//        callback();
//    });
//}

CefString fromUrl(const QUrl& url){
    QString surl  = url.toString();
    if(surl.isEmpty()){
        return "about:blank";
    }
    if(surl.startsWith("qrc:/")){
        //qDebug() << "convert Url"  << "qrc:///" + surl.mid(5);
        return ("qrc:///" + surl.mid(5)).toStdString();
    }
    return surl.toStdString();
}

QOpenGLFramebufferObject *QTPageRenderer::createFramebufferObject(const QSize &size){
    return new QOpenGLFramebufferObject(size);
}

QTPageRenderer::QTPageRenderer(CefRefPtr<CefPageRenderer> renderer): m_renderer(renderer){

}

QTPageRenderer::~QTPageRenderer(){
    //qDebug() << "Destroy renderer";
}

//QT reder thread
void QTPageRenderer::render(){
    //    qDebug() << "render";
    m_window->resetOpenGLState();
    framebufferObject()->bind();
    m_renderer->bind();
    glBegin(GL_QUADS);
    glTexCoord2f(0.f, 0.f); glVertex2f(-1.f,-1.f);
    glTexCoord2f(0.f, 1.f); glVertex2f(-1.f, 1.f);
    glTexCoord2f(1.f, 1.f); glVertex2f(1.f, 1.f);
    glTexCoord2f(1.f, 0.f); glVertex2f(1.f, -1.f);
    glEnd();
    glDisable(GL_TEXTURE_2D);
    m_window->resetOpenGLState();
}

CefPageRenderer::CefPageRenderer(CefItem *owner): m_owner(owner){
    m_buffer.data = 0;
}

//Qt render thread,  ui (onpaint, CefDoMessageLoopWork, QQuickFramebufferObject) blocked
void QTPageRenderer::synchronize(QQuickFramebufferObject *obj){
    m_window = obj->window();
    m_renderer->synchronize(obj);
}

void CefPageRenderer::synchronize(QQuickFramebufferObject *obj){
    QPointF c = obj->mapToGlobal(QPointF(0, 0));
    m_x =  (int)(c.x());
    m_y =  (int)(c.y());
}

void CefPageRenderer::processNextFrame(std::function<void (const void *, int, int)> f){
    m_next_frame_func = f;
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

void CefPageRenderer::pause(){
    m_mutex.lock();
    m_pause = true;
    m_buffer.data = 0;
    m_mutex.unlock();
}

void CefPageRenderer::resume(){
    m_mutex.lock();
    m_pause = false;
    m_mutex.unlock();
    //qDebug() <<"resume";
}

void CefPageRenderer::stop(){
    m_enable = false;
}

void CefPageRenderer::start(){
    m_enable = true;
}

void CefPageRenderer::bind(){
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
    if(m_buffer.data && !m_pause){
        if(m_texture_width != m_buffer.width || m_texture_height != m_buffer.height){
            m_texture_width = m_buffer.width;
            m_texture_height = m_buffer.height;
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, m_buffer.width, m_buffer.height, 0, GL_BGRA, GL_UNSIGNED_BYTE, m_buffer.data);
        }else{
            const void* data = 0;
            for(CefRect rect: m_dirty_rects){
                data = (char*)m_buffer.data + (rect.y * m_buffer.width + rect.x)*4;
                glPixelStorei(GL_UNPACK_ROW_LENGTH, m_buffer.width);
                glTexSubImage2D(GL_TEXTURE_2D, 0, rect.x, rect.y, rect.width, rect.height, GL_BGRA, GL_UNSIGNED_BYTE, data);
                glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            }
        }
    }
    m_buffer.data = 0;
    m_mutex.unlock();
}

//ui thread
bool CefPageRenderer::GetViewRect(CefRefPtr<CefBrowser> browser, CefRect& rect) {
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
            if(m_new_height != height || m_new_width != width || m_pause){
        return;
    }
    if(m_next_frame_func){
        m_next_frame_func(buffer, width, height);
        m_next_frame_func = nullptr;
    }
    //qDebug() << "onPaint" << buffer;
    m_mutex.lock();
    m_buffer.data = buffer;
    m_buffer.width = width;
    m_buffer.height = height;
    m_dirty_rects = dirtyRects;
    m_mutex.unlock();
    if(m_enable){
        m_owner->update();
    }
}

bool CefPageRenderer::GetScreenPoint(CefRefPtr<CefBrowser> browser, int viewX, int viewY, int &screenX, int &screenY) {
    screenX = m_x + viewX;
    screenY = m_y + viewY;
    return true;
}

bool CefPageRenderer::StartDragging(CefRefPtr<CefBrowser> browser, CefRefPtr<CefDragData> drag_data, DragOperationsMask allowed_ops, int x, int y){
    if(drag_data->IsFile()){
        QString fileDir = QDir::tempPath() + "/" + QString::fromStdString(drag_data->GetFileName().ToString());
        //qDebug() << "drag file" << fileDir;
        drag_data->GetFileContents(CefStreamWriter::CreateForFile(fileDir.toStdString()));
        QList<QUrl> urls;
        urls.append(QUrl::fromLocalFile(fileDir));
        m_owner->startUrlsDrag(urls);
        QFile::remove(fileDir);
    }
    else if(drag_data->IsFragment()){
        //qDebug() << "drag text" << QString::fromStdString(drag_data->GetFragmentText());
        m_owner->startTextDarg(QString::fromStdString(drag_data->GetFragmentText()), QString::fromStdString(drag_data->GetFragmentHtml()));
    }
    else if(drag_data->IsLink()){
        //qDebug() << "drag Link";
        m_owner->startTextDarg(QString::fromStdString(drag_data->GetFragmentText()), "");
    }
    return true;
}

Qt::CursorShape toQCursor(CefPageRenderer::CursorType type){
    switch (type){
    case CT_POINTER:
        return Qt::ArrowCursor;
    case CT_CROSS:
        return Qt::CrossCursor;
    case CT_WAIT:
        return Qt::WaitCursor;
    case CT_IBEAM:
        return Qt::IBeamCursor;
    case CT_NORTHRESIZE:
        return Qt::SizeVerCursor;
    case CT_NORTHEASTRESIZE:
        return Qt::SizeBDiagCursor;
    case CT_NORTHWESTRESIZE:
        return Qt::SizeFDiagCursor;
    case CT_SOUTHRESIZE:
        return Qt::SizeVerCursor;
    case CT_SOUTHEASTRESIZE:
        return Qt::SizeFDiagCursor;
    case CT_SOUTHWESTRESIZE:
        return Qt::SizeBDiagCursor;
    case CT_WESTRESIZE:
        return Qt::SizeHorCursor;
    case CT_NORTHSOUTHRESIZE:
        return Qt::SizeVerCursor;
    case CT_EASTWESTRESIZE:
        return Qt::SizeHorCursor;
    case CT_NORTHEASTSOUTHWESTRESIZE:
        return Qt::SizeAllCursor;
    case CT_NORTHWESTSOUTHEASTRESIZE:
        return Qt::SizeAllCursor;
    case CT_COLUMNRESIZE:
        return Qt::SplitHCursor;
    case CT_ROWRESIZE:
        return Qt::SplitVCursor;
    case CT_HAND:
        return Qt::PointingHandCursor;
    case CT_NODROP:
        return Qt::ForbiddenCursor;
    case CT_HELP:
        return Qt::WhatsThisCursor;
    case CT_PROGRESS:
        return Qt::BusyCursor;
    default:
        return Qt::ArrowCursor;
    }
}

void CefPageRenderer::OnCursorChange(CefRefPtr<CefBrowser> browser, HCURSOR cursor,
                                     CursorType type, const CefCursorInfo &custom_cursor_info)
{
    if(m_enable){
        emit m_owner->cursorChanged(toQCursor(type));
    }
}

bool BrowserListener::OnBeforeBrowse(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame,
                                     CefRefPtr<CefRequest> request, bool is_redirect)
{

    if(!m_enable){
        return true;
    }

    if(request->GetResourceType() != RT_MAIN_FRAME) { //request page resources
        return false;
    }

    QString url = QString::fromStdString(request->GetURL().ToString());
    QUrl qurl(url, QUrl::TolerantMode);
    //qDebug() << "on Before browse" << qurl << m_owner->m_url;
    if (qurl == m_owner->m_url) { //onbeforebrowse called from QT
        return false;
    }

    qint64 now = QDateTime::currentMSecsSinceEpoch();
    if (is_redirect || now - m_last_event_time > 5000) { //redirect link would be opened from qt
        if(m_redirect_enable){
            emit m_owner->redirect(qurl);
            //emit m_owner->iconChanged(qurl.scheme() + "://" + qurl.host() + "/favicon.ico");
            return false;
        }
        return true;
    }
    if(m_allow_link_trans){ //handle open new links with CEF
        m_owner->m_url = qurl;
        emit m_owner->urlChanged(qurl);
        return false;
    }
    emit m_owner->requestPage(qurl, false);
    return true;
}

bool BrowserListener::OnOpenURLFromTab(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame,
                                       const CefString& target_url, CefRequestHandler::WindowOpenDisposition target_disposition,
                                       bool user_gesture)
{
    if(!m_enable){
        return true;
    }
    QString url = QString::fromStdString(target_url);
    QUrl qurl(url, QUrl::TolerantMode);
    //qDebug() << "OnOpenURLFromTab" << qurl;
    emit m_owner->requestPage(qurl, true);
    return true;
}


bool BrowserListener::OnBeforePopup(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame,
                                    const CefString &target_url, const CefString &target_frame_name,
                                    CefLifeSpanHandler::WindowOpenDisposition target_disposition, bool user_gesture,
                                    const CefPopupFeatures &popupFeatures, CefWindowInfo &windowInfo,
                                    CefRefPtr<CefClient> &client, CefBrowserSettings &settings,
                                    bool *no_javascript_access)
{
    if(!m_enable){
        return true;
    }
    QUrl url(QString::fromStdString(target_url.ToString()), QUrl::TolerantMode);
    emit m_owner->requestPage(url, false);
    return true;
}

void BrowserListener::OnTitleChange(CefRefPtr<CefBrowser> browser, const CefString &title) {
    if(!m_enable){
        return;
    }
    QString str = QString::fromStdString(title.ToString());
    //qDebug() << "title changed" << str;
    emit m_owner->titleChanged(str); //TODO crash here
}

void BrowserListener::OnFaviconURLChange(CefRefPtr<CefBrowser> browser, const std::vector<CefString> &icon_urls) { 
    if(!m_enable || icon_urls.empty()) {
        return;
    }
    emit m_owner->iconChanged(QString::fromStdString(icon_urls[0].ToString())); //TODO crash here
}

void BrowserListener::OnLoadingStateChange(CefRefPtr<CefBrowser> browser, bool isLoading,
                                           bool canGoBack, bool canGoForward)
{
    if(!isLoading && m_enable){
        emit m_owner->loadEnd();
    }

}

void BrowserListener::OnBeforeDownload(CefRefPtr<CefBrowser> browser, CefRefPtr<CefDownloadItem> download_item,
                                       const CefString& suggested_name, CefRefPtr<CefBeforeDownloadCallback> callback)
{
    m_owner->download(QString::fromStdString(download_item->GetURL().ToString()));
}

enum{
    MENU_USER_NEW_TAB = MENU_ID_USER_FIRST,
    MENU_USER_OPEN_IMAGGE,
    MENU_USER_SAVE_LINK_TO_STORAGE,
    MENU_USER_SAVE_IMAGE_TO_STORAGE,
    MENU_USER_DOWNLOAD
};

void BrowserListener::OnBeforeContextMenu(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame,
                                          CefRefPtr<CefContextMenuParams> params, CefRefPtr<CefMenuModel> model)
{
    if(params->GetMediaType() == CM_MEDIATYPE_IMAGE){
        model->Clear();
        model->AddItem(MENU_USER_OPEN_IMAGGE, "Открыть картинку");
        model->AddItem(MENU_USER_SAVE_IMAGE_TO_STORAGE, "Сохранить картинку в хранилище");
        model->AddItem(MENU_USER_DOWNLOAD, "Скачать картинку");
        return;
    }
    if(params->GetLinkUrl().size() > 0){
        model->Clear();
        model->AddItem(MENU_USER_NEW_TAB, "Открыть в suggest группе");
        model->AddItem(MENU_USER_SAVE_LINK_TO_STORAGE, "Сохранить ссылку в хранилище");
        return;
    }

}

void BrowserListener::OnBeforeClose(CefRefPtr<CefBrowser> browser){
    m_owner->onBrowserDestroyed();
}

bool BrowserListener::OnContextMenuCommand(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame,
                                           CefRefPtr<CefContextMenuParams> params, int command_id, EventFlags event_flags)
{
    if(!m_enable){
        return false;
    }
    switch(command_id){
    case MENU_USER_NEW_TAB:
        emit m_owner->requestPage(QString::fromStdString(params->GetLinkUrl().ToString()), true);
        return true;
    case MENU_USER_OPEN_IMAGGE:
        emit m_owner->requestPage(QString::fromStdString(params->GetSourceUrl().ToString()), false);
        return true;
    case MENU_USER_SAVE_IMAGE_TO_STORAGE:{
//        QString path = QStandardPaths::TempLocation();
//        Download::download(url, path, [this](){
//            m_owner->savedToStorage(path);
//        });
        return true;
    }
    case MENU_USER_SAVE_LINK_TO_STORAGE:
        emit m_owner->savedToStorage(QString::fromStdString((params->GetLinkUrl().ToString())));
        return true;
    case MENU_USER_DOWNLOAD:
        m_owner->download(QString::fromStdString(params->GetSourceUrl().ToString()));
        return true;
    }
    return true;
}


void BrowserListener::userEventOccured(){
    m_last_event_time = QDateTime::currentMSecsSinceEpoch();
}

void BrowserListener::OnLoadStart(CefRefPtr<CefBrowser> browser,
                                  CefRefPtr<CefFrame> frame,
                                  TransitionType transition_type){
    if(m_enable && frame->IsMain()){
        m_owner->m_renderer->resume();
        browser->GetHost()->WasResized();
    }
}

CefItem::CefItem(QQuickItem *parent): QQuickFramebufferObject(parent) {
    CEF_REQUIRE_UI_THREAD()
            //qDebug() << "construct item" << this << "with parent" << parent;
            m_listener = new BrowserListener(this);
    m_renderer = new CefPageRenderer(this);
//    m_timer = new QTimer(this);
//    m_timer->setSingleShot(false);
//    m_timer->setInterval(5);
//    m_timer->setTimerType(Qt::TimerType::CoarseTimer);
//    QObject::connect(m_timer, SIGNAL(timeout()), this, SLOT(doCefWork()));
    QObject::connect(this, SIGNAL(windowChanged(QQuickWindow*)), this, SLOT(initBrowser(QQuickWindow*)), Qt::QueuedConnection);
    //QObject::connect(this, SIGNAL(destroyed(QObject*)), this, SLOT(destroyBrowser()));
//    m_timer->start();
    m_text_callback = new TextCallback();
    m_text_callback->setOwner(this);
}

void CefItem::doCefWork() {
    CefDoMessageLoopWork();
}

void CefItem::updateVisible(){
    if(m_browser){
        m_browser->GetHost()->WasHidden(!isVisible());
    }
}

CefItem::~CefItem(){
    //CEF_REQUIRE_UI_THREAD()
            destroyBrowser();
}

QQuickFramebufferObject::Renderer* CefItem::createRenderer() const {
    return new QTPageRenderer(m_renderer);
}

class EmptyCookieManager: public CefCookieManager{
public:
    virtual void SetSupportedSchemes(
            const std::vector<CefString>& schemes,
            CefRefPtr<CefCompletionCallback> callback){}

    virtual bool VisitAllCookies(CefRefPtr<CefCookieVisitor> visitor)
    {
        return true;
    }
    virtual bool VisitUrlCookies(const CefString& url,
                                 bool includeHttpOnly,
                                 CefRefPtr<CefCookieVisitor> visitor)
    {
        return true;
    }
    virtual bool SetCookie(const CefString& url,
                           const CefCookie& cookie,
                           CefRefPtr<CefSetCookieCallback> callback){
        return true;
    }
    virtual bool DeleteCookies(const CefString& url,
                               const CefString& cookie_name,
                               CefRefPtr<CefDeleteCookiesCallback> callback)
    {
        return true;
    }
    virtual bool SetStoragePath(const CefString& path,
                                bool persist_session_cookies,
                                CefRefPtr<CefCompletionCallback> callback)
    {
        return true;
    }
    virtual bool FlushStore(CefRefPtr<CefCompletionCallback> callback)
    {
        qDebug() << "flush storage";
        return true;
    }
    IMPLEMENT_REFCOUNTING(EmptyCookieManager)
};

class CookieContextHandler: public CefRequestContextHandler{
public:
    CookieContextHandler(bool enable_cookie){
//        if(enable_cookie){
            m_manager = CefCookieManager::GetGlobalManager(NULL); /*new EmptyCookieManager();*/
//        }else{
//            m_manager = new EmptyCookieManager();
//        }
    }
    virtual CefRefPtr<CefCookieManager> GetCookieManager() {
        return m_manager;
    }
private:
    CefRefPtr<CefCookieManager> m_manager;
    IMPLEMENT_REFCOUNTING(CookieContextHandler)
};

class ACefClient: public CefClient{
public:
    void set(CefRefPtr<CefRenderHandler> renderer){
        m_renderer = renderer;
    }
    void setBrowserListener(CefRefPtr<BrowserListener> listener) {
        m_listener = listener;
    }

    virtual CefRefPtr<CefRequestHandler> GetRequestHandler() OVERRIDE{
        return m_listener;
    }
    virtual CefRefPtr<CefRenderHandler> GetRenderHandler() OVERRIDE{
        return m_renderer;
    }
    virtual CefRefPtr<CefLifeSpanHandler> GetLifeSpanHandler() OVERRIDE{
        return m_listener;
    }
    virtual CefRefPtr<CefDisplayHandler> GetDisplayHandler() OVERRIDE{
        return m_listener;
    }
    virtual CefRefPtr<CefKeyboardHandler> GetKeyboardHandler() OVERRIDE{
        return m_listener;
    }
    virtual CefRefPtr<CefDragHandler> GetDragHandler() OVERRIDE{
        return m_listener;
    }
    virtual CefRefPtr<CefLoadHandler> GetLoadHandler() OVERRIDE{
        return m_listener;
    }
    virtual CefRefPtr<CefDownloadHandler> GetDownloadHandler() OVERRIDE{
        return m_listener;
    }
    virtual CefRefPtr<CefContextMenuHandler> GetContextMenuHandler() OVERRIDE{
        return m_listener;
    }

private:
    CefRefPtr<CefRenderHandler> m_renderer;
    CefRefPtr<BrowserListener> m_listener;
    IMPLEMENT_REFCOUNTING(ACefClient)
};

void CefItem::resize() {
    if(m_browser){
        m_renderer->setSize((int)width(), (int)height());
        m_browser->GetHost()->WasResized();
    }
}

void CefItem::releaseResources(){
    destroyBrowser();
}

void CefItem::shutDown(){
    if(m_browser){
        destroyBrowser();
    }else{
        removeCefBrowserFromGC();
    }
}

void CefItem::onBrowserDestroyed(){
    removeCefBrowserFromGC();
}

void CefItem::destroyBrowser(){
    if(m_browser){
        m_iobuffer.setBrowser(nullptr);
        m_renderer->clearBuffer();
        m_renderer->stop();
        m_listener->setEnable(false);
        m_browser->GetHost()->CloseBrowser(true);
        m_browser = nullptr;
        m_text_callback->setOwner(nullptr);
    }
}

void CefItem::suspendBrowser(){
#ifdef Q_OS_WIN32
#endif
}

void CefItem::resumeBrowser(){
#ifdef Q_OS_WIN32
#endif
}

void CefItem::initBrowser(QQuickWindow* window) {
    if(!window || !m_running || m_url.isEmpty() && m_html.isEmpty()){
        return;
    }
    CEF_REQUIRE_UI_THREAD()
    m_renderer->setSize(width(), height());
    m_renderer->start();
    CefRefPtr<ACefClient> acefclient = new ACefClient();
    acefclient->set(m_renderer);

    m_text_callback->setOwner(this);
    m_listener->setEnable(true);
    acefclient->setBrowserListener(m_listener);

    CefWindowInfo mainWindowInfo;
    mainWindowInfo.SetAsWindowless((HWND) window->winId(), false);

    //qDebug() << "Init Browser " << QString::fromStdString(fromUrl(m_url)) << m_url;

    CefRefPtr<CefRequestContext> requestContext =
            CefRequestContext::CreateContext(CefRequestContextSettings(), new CookieContextHandler(m_cookies_enable));

    m_browser = CefBrowserHost::CreateBrowserSync(mainWindowInfo, acefclient, fromUrl(m_url), CefBrowserSettings(), requestContext);

    QObject::connect(this, SIGNAL(visibleChanged()), this, SLOT(updateVisible())); //TODO make update visible
    QObject::connect(this, SIGNAL(widthChanged()), this, SLOT(resize()));
    QObject::connect(this, SIGNAL(heightChanged()), this, SLOT(resize()));

    if(m_url.isEmpty()){
        m_browser->GetMainFrame()->LoadString(m_html.toStdString(), "about:blank");
    }
    m_iobuffer.setBrowser(m_browser);
    addCefBrowserToGC();
}

void IOBuffer::setBrowser(CefRefPtr<CefBrowser> browser){
    m_browser = browser;
}

void CefItem::mouseMove(int x, int y, int buttons) {
    m_iobuffer.mouseMove(x, y, buttons);
}

void CefItem::mousePress(int x, int y, int buttons){
    m_iobuffer.mousePress(x, y, buttons);
}

void CefItem::mouseRelease(int x, int y, int buttons) {
    m_listener->userEventOccured();
    m_iobuffer.mouseRelease(x, y, buttons);
}

void CefItem::mouseWheel(int x, int y, int buttons,  QPoint angle) {
    m_iobuffer.mouseWheel(x, y, buttons, angle);
}


uint32 convertModifires(int mouseButtons){
    uint32 result = EVENTFLAG_NONE;
    if(mouseButtons & Qt::LeftButton){
        result |= EVENTFLAG_LEFT_MOUSE_BUTTON;
    }
    if(mouseButtons & Qt::RightButton){
        result |= EVENTFLAG_RIGHT_MOUSE_BUTTON;
    }
    if(mouseButtons & Qt::MiddleButton){
        result |= EVENTFLAG_MIDDLE_MOUSE_BUTTON;
    }
    return result;
}

cef_mouse_button_type_t getButton(int mouseButtons){
    if(mouseButtons & Qt::LeftButton){
        return MBT_LEFT;
    }
    if(mouseButtons & Qt::RightButton){
        return MBT_RIGHT;
    }
    if(mouseButtons & Qt::MiddleButton){
        return MBT_MIDDLE;
    }
    return MBT_LEFT;
}

void IOBuffer::mouseMove(int x, int y, int buttons) {
    if(m_browser){
        CefMouseEvent event;
        event.x = x;
        event.y = y;
        event.modifiers = convertModifires(buttons);;
        m_browser->GetHost()->SendMouseMoveEvent(event, false);
    }
}

void IOBuffer::mousePress(int x, int y, int buttons){
    if(m_browser){
        int time = QTime::currentTime().msecsSinceStartOfDay();
        if(time - m_last_click_time < 200){
            m_click_count++;
        }else{
            m_click_count = 1;
        }
        m_last_click_time = time;
        CefMouseEvent event;
        event.x = x;
        event.y = y;
        m_key_flags &= convertModifires(buttons);
        event.modifiers = m_key_flags;
        m_browser->GetHost()->SendMouseClickEvent(event, getButton(buttons), false, m_click_count);
    }
}

void IOBuffer::mouseRelease(int x, int y, int buttons) {
    if(m_browser){
        CefMouseEvent event;
        event.x = x;
        event.y = y;
        m_key_flags &= ~convertModifires(buttons);
        event.modifiers = m_key_flags;
        m_browser->GetHost().get()->SendMouseClickEvent(event, getButton(buttons), true, m_click_count);
    }
}

void IOBuffer::mouseWheel(int x, int y, int buttons,  QPoint angle) {
    if(m_browser){
        CefMouseEvent event;
        event.x = x;
        event.y = y;
        event.modifiers = m_key_flags;
        //qDebug() << "scroll x" << angle.x() << "y" << angle.y();
        m_browser->GetHost()->SendMouseWheelEvent(event, angle.x(), angle.y());
    }
}

int toWindows(int key){
    switch (key) {
    case 16777234: return 0x25; //Left arrow
    case 16777235: return 0x26; //Up arrow
    case 16777236: return 0x27; //Right arrow
    case 16777237: return 0x28; //Down arrow
    case 16777217: return 0x09; //Tab
    case 16777216: return 0x1B; //Esc
    case 16777249: return 0x11; //Ctrl key
    case 16777223: return 0x2E; //Delete
    case 16777222: return 0x2D; //Insert
    case 16777232: return 0x24; //Home
    case 16777238: return 0x21; //Page Up
    case 16777239: return 0x22; //Page Down
    case 16777251: return 0x12; //alt
    case 16777250: return 0x26; //win
    case 16777233: return 0x23; //end
    case 16777219: return 0x08; //backspace
    //case 16777220: return 0x0D; //Enter
    default: return 0;
    }
}

cef_event_flags_t getFlagFromKey(int key){
    switch (key) {
    case 16777249: return EVENTFLAG_CONTROL_DOWN; //Ctrl key
    case 16777251: return EVENTFLAG_ALT_DOWN; //alt
    default: return EVENTFLAG_NONE;
    }
}

int getWinVirtualKeyCodeFromUtf16Char(char16 c){
    if(c >= 'a' && c <= 'z'){
        return c - 'a' + 'A';
    }
    return c;
}

bool IOBuffer::keyPress(int key, Qt::KeyboardModifiers modifiers, const QString& tex, bool autoRepeat, ushort count) {
    if(!m_browser){
        return false;
    }
    m_key_flags |= getFlagFromKey(key);
    qDebug() << "keyFlas" << m_key_flags;

    CefKeyEvent charEvent;
    charEvent.type = KEYEVENT_CHAR;
    charEvent.modifiers = 0;
    charEvent.is_system_key = 0;
    CefKeyEvent pressEvent;
    pressEvent.type = KEYEVENT_KEYDOWN;
    pressEvent.modifiers = 0;
    pressEvent.is_system_key = 0;

    int wkey = toWindows(key); //not chars
    if(wkey > 0){
        pressEvent.windows_key_code = wkey;
        qDebug() << "key pressed" << key << tex << wkey;
        m_browser->GetHost()->SendKeyEvent(pressEvent);
        return true;
    }

    if(tex.length() > 0){
        int c = tex.utf16()[0];
        charEvent.windows_key_code = c;
        pressEvent.windows_key_code = getWinVirtualKeyCodeFromUtf16Char(c);
        qDebug() << "key pressed !" << key << tex << c;
        m_browser->GetHost()->SendKeyEvent(pressEvent);
        m_browser->GetHost()->SendKeyEvent(charEvent);
        return true;
    }
    return false;
}

bool IOBuffer::keyRelease(int key, Qt::KeyboardModifiers modifiers, const QString& tex, bool autoRepeat, ushort count) {
    if(!m_browser){
        return false;
    }
    m_key_flags &= ~getFlagFromKey(key);
    qDebug() << "keyFlas" << m_key_flags;

    int wkey = toWindows(key);
    if(!wkey && tex.length() > 0){
        wkey = getWinVirtualKeyCodeFromUtf16Char(tex.utf16()[0]);
    }
    if(!wkey){
        return false;
    }
    //qDebug() << "key released" << key << tex << wkey;
    CefKeyEvent keyEvent;
    keyEvent.modifiers = 0;
    keyEvent.is_system_key = 0;
    keyEvent.type = KEYEVENT_KEYUP;
    keyEvent.windows_key_code = wkey;
    m_browser->GetHost()->SendKeyEvent(keyEvent);
    return true;
}

bool CefItem::sendKeyPress(int key, Qt::KeyboardModifiers modifiers, const QString &tex, bool autoRepeat, ushort count){
    return m_iobuffer.keyPress(key, modifiers, tex, autoRepeat, count);
}

bool CefItem::sendKeyRelease(int key, Qt::KeyboardModifiers modifiers, const QString &tex, bool autoRepeat, ushort count){
    return m_iobuffer.keyRelease(key, modifiers, tex, autoRepeat, count);
}

CefMouseEvent createMouseEvent(double x, double y){
    CefMouseEvent event;
    event.x = (int)x;
    event.y = (int)y;
    event.modifiers = 0;
    return event;
}

CefBrowserHost::DragOperationsMask translateAction(Qt::DropAction action){
    switch (action) {
    case Qt::DropAction::CopyAction:
        return DRAG_OPERATION_COPY;
    case Qt::DropAction::LinkAction:
        return DRAG_OPERATION_LINK;
    case Qt::DropAction::MoveAction:
        return DRAG_OPERATION_MOVE;
    default:
        return DRAG_OPERATION_NONE;
    }
}

bool CefItem::dragEnterUrls(double x, double y, QList<QUrl> urls, Qt::DropAction action){

    CefRefPtr<CefDragData> dragData  = CefDragData::Create();
    for(QUrl url: urls){
        QString surl = url.toString();
        if(surl.startsWith("file:///")){
            surl = surl.mid(8);
        }
        //qDebug() << "enter" << x << y << surl << action;
        dragData->AddFile(surl.toStdString(), "some data"); //TODO change some data on smth
    }
    m_browser->GetHost()->DragTargetDragEnter(dragData, createMouseEvent(x, y), translateAction(action));
    return true;
}

bool CefItem::dragEnterText(double x, double y, QString text, Qt::DropAction action){
    //qDebug() << "enter text" << x << y << text;
    CefRefPtr<CefDragData> dragData  = CefDragData::Create();
    dragData->SetFragmentText(text.toStdString());
    m_browser->GetHost()->DragTargetDragEnter(dragData, createMouseEvent(x, y), translateAction(action));
    return true;
}

bool CefItem::dragEnterHtml(double x, double y, QString html, Qt::DropAction action){
    //qDebug() << "enter html" << x << y << html;
    CefRefPtr<CefDragData> dragData  = CefDragData::Create();
    dragData->SetFragmentHtml(html.toStdString());
    m_browser->GetHost()->DragTargetDragEnter(dragData, createMouseEvent(x, y), translateAction(action));
    return true;

}

bool CefItem::dragExit(){
    //qDebug() << "exit";
    m_browser->GetHost()->DragTargetDragLeave();
    return true;
}

bool CefItem::dragMove(double x, double y, Qt::DropAction action){
    //qDebug() << "move" << x << y;
    m_browser->GetHost()->DragTargetDragOver(createMouseEvent(x, y), translateAction(action));
    return true;
}

bool CefItem::dragDrop(double x, double y){
    //qDebug() << "drop" << x << y;
    m_browser->GetHost()->DragTargetDrop(createMouseEvent(x, y));
    return true;
}

void CefItem::setBrowserFocus(bool focus){
    if(m_browser){
        m_browser->GetHost()->SetFocus(focus);
    }
}

void CefItem::finishDrag(){
    m_browser->GetHost()->DragSourceSystemDragEnded();
}

CefBrowserHost::DragOperationsMask toDragOperationsMask(Qt::DropAction dropAction){
    if(dropAction & Qt::DropAction::CopyAction){
        return DRAG_OPERATION_COPY;
    }
    if(dropAction & Qt::DropAction::MoveAction){
        return DRAG_OPERATION_MOVE;
    }
    if(dropAction & Qt::DropAction::LinkAction){
        return DRAG_OPERATION_LINK;
    }
    return DRAG_OPERATION_NONE;
}

void CefItem::startDrag(QMimeData* mimeData){
    QDrag* drag = new QDrag(this);
    drag->setMimeData(mimeData);
    emit dragStarted();
    Qt::DropAction dropAction = drag->exec();
    m_browser->GetHost()->DragSourceEndedAt(0, 0, toDragOperationsMask(dropAction));
    m_browser->GetHost()->DragSourceSystemDragEnded();
}

void CefItem::startTextDarg(const QString &text, const QString&  html){
    QMimeData *mimeData = new QMimeData;
    mimeData->setText(text);
    mimeData->setHtml(html);
    startDrag(mimeData);
}

void CefItem::startImageDrag(const QImage& img){
    QMimeData *mimeData = new QMimeData;
    mimeData->setImageData(img);
    startDrag(mimeData);
}

void CefItem::startUrlsDrag(const QList<QUrl>& urls){
    QMimeData *mimeData = new QMimeData;
    mimeData->setUrls(urls);
    startDrag(mimeData);
}

void CefItem::download(const QUrl& url){
    Download* item = new Download(url, QStandardPaths::writableLocation(QStandardPaths::DownloadLocation));
    item->start();
    QQmlEngine::setObjectOwnership(item, QQmlEngine::JavaScriptOwnership);
    emit downloadStarted(item);
}


void CefItem::findText(const QString& text, bool findForward){
    if(text.length() == 0){
        m_browser->GetHost()->StopFinding(true);
        return;
    }
    m_current_search_id++;
    m_browser->GetHost()->Find(m_current_search_id, text.toStdString(), findForward, false, false);
}

void CefItem::selectAll(){
    m_browser->GetFocusedFrame()->SelectAll();
}

void CefItem::paste(){
    m_browser->GetFocusedFrame()->Paste();
}

void CefItem::cut(){
    m_browser->GetFocusedFrame()->Cut();
}

void CefItem::undo(){
    m_browser->GetFocusedFrame()->Undo();
}

void CefItem::copy(){
    m_browser->GetFocusedFrame()->Copy();
}

void CefItem::redo(){
    m_browser->GetFocusedFrame()->Redo();
}

void CefItem::reload(){
    m_browser->Reload();
}

void CefItem::loadHtml(const QString &html){
    if(!html.size()){
        return;
    }
    m_html = html;
    if(m_running && m_browser){
        m_browser->GetMainFrame()->LoadStringW(m_html.toStdString(), "about:blank");
    }else{
        initBrowser(window());
    }
}

void CefItem::saveScreenshot(const QString &fileName, int x, int y, int w, int h){
    m_renderer->processNextFrame([this, fileName, x, y, w, h](const void* buffer, int width, int heigth){
        QImage img((uchar*)buffer + (width*y + x)*4, w, h, width*4, QImage::Format_RGBA8888);
        QImage res = img.rgbSwapped();
        res.save(fileName);
        emit screenShotSaved();
    });
    update();
}

void TextCallback::setOwner(CefItem *item){
    m_owner = item;
}

void TextCallback::Visit(const CefString &string){
    if(m_owner){
        emit m_owner->textRecieved(QString::fromStdString(string.ToString()));
    }
}

void CefItem::getText(){
    if(m_browser){
        m_browser->GetMainFrame()->GetText(m_text_callback);
    }
}

void CefItem::clearCookies(const QString &url){
    m_browser->GetHost()->GetRequestContext()->GetHandler()->GetCookieManager()->DeleteCookies(url.toStdString(), "", nullptr);
}

void CefItem::redirectEnable(bool redirect){
    m_listener->redirectEnable(redirect);
}

void BrowserListener::redirectEnable(bool redirect){
    m_redirect_enable = redirect;
}

void BrowserListener::setEnable(bool enable){
    m_enable = enable;
}

double CefItem::zoomFactor(){
    return m_zoom_factor;
}

void CefItem::setZoomFactor(double zoomFactor){
    m_zoom_factor = zoomFactor;
    if(m_browser){
        m_browser->GetHost()->SetZoomLevel(zoomFactor);
    }
}

QUrl CefItem::url() const{
    return m_url;
}

void CefItem::setUrl(const QUrl &url) {
    //qDebug() << "set url" << url;
    if(m_browser){
        m_renderer->pause();
        m_browser->GetMainFrame()->LoadURL(url.toString().toStdString());
    }else if(!url.isEmpty()){
        m_url = url;
        initBrowser(window());
    }
    emit urlChanged(url);
}

bool CefItem::running(){
    return m_running;
}

void CefItem::setRunning(bool running){
    if(running && !m_running){
        m_running = true;
        initBrowser(window());
        return;
    }
    if(!running && m_running){
        destroyBrowser();
        m_running = false;
        return;
    }
    if(running && m_running && !m_browser){
        initBrowser(window());
    }
}

bool CefItem::allowLinkTtransitions(){
    return m_listener->m_allow_link_trans;
}

void CefItem::setAllowLinkTtransitions(bool allow){
    m_listener->m_allow_link_trans = allow;
}

void CefItem::setCookiesEnable(bool cookies){
    m_cookies_enable = cookies;
}

bool CefItem::cookiesEnable(){
    return m_cookies_enable;
}

}

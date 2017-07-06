
#include "cefpage.h"
#include "include/wrapper/cef_helpers.h"
#include <QQuickWindow>
#include <QQmlEngine>
#include "dosearch.h"

#include <QOpenGLFunctions_3_1>

namespace expleague {

CefString fromUrl(const QUrl& url) {
  QString surl = url.toString();
  if (surl.isEmpty()) {
    return "about:blank";
  }
  if (surl.startsWith("qrc:/")) {
    //qDebug() << "convert Url"  << "qrc:///" + surl.mid(5);
    return ("qrc:///" + surl.mid(5)).toStdString();
  }
  return surl.toStdString();
}

QOpenGLFunctions_3_1* glfunc = 0;

QOpenGLFramebufferObject* QTPageRenderer::createFramebufferObject(const QSize& size) {
  CefPageRenderer* renderer = m_owner->renderer();
  if (size.height() != renderer->height() || size.width() != renderer->width()) {
    GLuint oldBuffer = m_buffer;
    //    const GLubyte* string = glfunc->glGetString(GL_VERSION);
    glfunc->glGenBuffers(1, &m_buffer);
    glfunc->glBindBuffer(GL_PIXEL_UNPACK_BUFFER, m_buffer);
    glfunc->glBufferData(GL_PIXEL_UNPACK_BUFFER, size.width() * size.height() * 4, 0, GL_STREAM_DRAW);
    {
      std::lock_guard<std::mutex> guard(m_owner->m_mutex);

      if (oldBuffer) {
        const int height = std::min(renderer->height(), size.height());
        const int width = std::min(renderer->width(), size.width());

        glfunc->glBindBuffer(GL_UNIFORM_BUFFER, oldBuffer);
        glfunc->glUnmapBuffer(GL_UNIFORM_BUFFER);
        for (int i = 0; i < height; i++) {
          glfunc->glCopyBufferSubData(GL_UNIFORM_BUFFER, GL_PIXEL_UNPACK_BUFFER, i * renderer->width() * 4, i * size.width() * 4, width * 4);
        }
        glfunc->glBindBuffer(GL_UNIFORM_BUFFER, 0);
        glfunc->glDeleteBuffers(1, &oldBuffer);
      }

      renderer->setBuffer(glfunc->glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL_WRITE_ONLY), size.width(), size.height());
    }
    glfunc->glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);

    const CefRefPtr<CefBrowser>& browser = m_owner->browser();
    if (browser.get())
      browser->GetHost()->WasResized();
  }
  QOpenGLFramebufferObject* fbo = new QOpenGLFramebufferObject(size);
  {
    //    m_window->resetOpenGLState();
    //    fbo->bind();
    //    glfunc->glColor3f(1, 0, 0);
    //    glfunc->glBegin(GL_QUADS);
    //    glfunc->glVertex2f(-1.f, -1.f);
    //    glfunc->glVertex2f(-1.f, 1.f);
    //    glfunc->glVertex2f(1.f, 1.f);
    //    glfunc->glVertex2f(1.f, -1.f);
    //    glfunc->glEnd();
    //    fbo->release();
    //    m_window->resetOpenGLState();
  }
  return fbo;
}

QTPageRenderer::QTPageRenderer(CefItem* owner): m_owner(owner) {
  glfunc = QOpenGLContext::currentContext()->versionFunctions<QOpenGLFunctions_3_1>();
}

QTPageRenderer::~QTPageRenderer() {
  if (m_buffer) {
    glfunc->glDeleteBuffers(1, &m_buffer);
  }
}

//QT reder thread
void QTPageRenderer::render() {
  assert(QOpenGLContext::currentContext());

  QOpenGLFramebufferObject* fbo = framebufferObject();
  const int height = fbo->height();
  const int width = fbo->width();

  const bool texturesEnabled = glfunc->glIsEnabled(GL_TEXTURE_2D);
  if (!texturesEnabled)
    glfunc->glEnable(GL_TEXTURE_2D);

  glfunc->glBindTexture(GL_TEXTURE_2D, fbo->texture());

  //  auto prev = std::chrono::high_resolution_clock::now();
  glfunc->glBindBuffer(GL_PIXEL_UNPACK_BUFFER, m_buffer);
  {
    std::lock_guard<std::mutex> guard(m_owner->m_mutex);
    glfunc->glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER);
    glfunc->glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_BGRA, GL_UNSIGNED_BYTE, 0);
    m_owner->renderer()->setBuffer(glfunc->glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL_WRITE_ONLY), width, height);
  }
  glfunc->glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
  //  auto now = std::chrono::high_resolution_clock::now();
  //  std::chrono::duration<int64, std::nano> dif = std::chrono::duration_cast<std::chrono::nanoseconds>(now - prev);
  //  qDebug() << "Copy for " << dif.count() << "usec";

  glfunc->glBindTexture(GL_TEXTURE_2D, 0);
  if (texturesEnabled)
    glfunc->glDisable(GL_TEXTURE_2D);
}

//Qt render thread,  ui (onpaint, CefDoMessageLoopWork, QQuickFramebufferObject) blocked
void QTPageRenderer::synchronize(QQuickFramebufferObject* obj) {
  m_window = obj->window();
  m_window->setClearBeforeRendering(true);
}

CefPageRenderer::CefPageRenderer(CefItem* owner): m_owner(owner) {
}

void CefPageRenderer::processNextFrame(std::function<void(const void*, int, int)> f) {
  m_next_frame_func = f;
}

void CefPageRenderer::disable() {
  m_enable = false;
}

void CefPageRenderer::enable() {
  m_enable = true;
}

//ui thread
bool CefPageRenderer::GetViewRect(CefRefPtr<CefBrowser> browser, CefRect& rect) {
  CEF_REQUIRE_UI_THREAD()
      rect = CefRect(0, 0, (int)(m_width/m_scale_factor), (int)(m_height/m_scale_factor));
  return true;
}

//ui thread
void CefPageRenderer::OnPaint(CefRefPtr<CefBrowser> browser, PaintElementType type, const RectList& dirtyRects,
                              const void* buffer, int width, int height) {
  CEF_REQUIRE_UI_THREAD()
      if (m_width != width || m_height != height || !m_gpu_buffer || !m_enable)
      return;
  {

    //    auto prev = std::chrono::high_resolution_clock::now();
    std::lock_guard<std::mutex> guard(m_owner->m_mutex);
    if (m_gpu_buffer) {
      auto prevt = std::chrono::high_resolution_clock::now();
      if (!m_clean) {
        for (CefRect rect: dirtyRects) {
          if (rect.width < width) {
            for (int i = 0; i < rect.height; i++) {
              const int offset = ((i + rect.y) * width + rect.x) * 4;
              memcpy((char*) m_gpu_buffer + offset, (char*) buffer + offset, (size_t) rect.width * 4);
            }
          }
          else  {
            const int offset = (rect.y * width + rect.x) * 4;
            memcpy((char*) m_gpu_buffer + offset, (char*) buffer + offset, (size_t) rect.width * rect.height * 4);
          }
        }
      }
      else memcpy(m_gpu_buffer, buffer, (size_t) width * height * 4);
//      auto now = std::chrono::high_resolution_clock::now();
//      std::chrono::duration<int64, std::nano> dif = std::chrono::duration_cast<std::chrono::nanoseconds>(now - prevt);
//      qDebug() << "memcpy time" <<  dif.count()/1000;
      m_clean = false;
    }
    //    auto now = std::chrono::high_resolution_clock::now();
    //    std::chrono::duration<int64, std::nano> dif = std::chrono::duration_cast<std::chrono::nanoseconds>(now - prev);
    //    qDebug() << "Copy for " << dif.count() << "usec";
  }
  if (m_next_frame_func) {
    m_next_frame_func(buffer, width, height);
    m_next_frame_func = nullptr;
  }
  //  qDebug() << "onPaint" << buffer;
  m_owner->update();
}
cef_screen_info_t a;
bool CefPageRenderer::GetScreenInfo(CefRefPtr<CefBrowser> browser,
                                    CefScreenInfo& screen_info) {
  if(!m_enable)
    return false;
  m_scale_factor = (float) m_owner->window()->devicePixelRatio();
  screen_info.device_scale_factor = m_scale_factor;
  screen_info.depth = 24;
  screen_info.depth_per_component = 8;
  screen_info.is_monochrome = 0;
  //  screen_info.rect = CefRect(0, 0, m_width, m_height);
  //  screen_info.available_rect = CefRect(0, 0, m_width, m_height);
  return true;
}

bool CefPageRenderer::GetScreenPoint(CefRefPtr<CefBrowser> browser, int viewX, int viewY, int& screenX, int& screenY) {
  const QPointF& global = m_owner->mapToGlobal(QPointF(viewX, viewY));
  screenX = global.x();
#ifdef Q_OS_MAC
  QScreen* screen = doSearch::instance()->main()->screen();
  const QRect& all = screen->geometry();
  screenY = all.bottom() - global.y();
#else
  screenY = global.y();
#endif
  //  qDebug() << global.y() << screenY << viewY;
  return true;
}

bool CefPageRenderer::StartDragging(CefRefPtr<CefBrowser> browser, CefRefPtr<CefDragData> drag_data,
                                    DragOperationsMask allowed_ops, int x, int y) {
  if (drag_data->IsFile()) {
    QString fileDir = QDir::tempPath() + "/" + QString::fromStdString(drag_data->GetFileName().ToString());
    //qDebug() << "drag file" << fileDir;
    drag_data->GetFileContents(CefStreamWriter::CreateForFile(fileDir.toStdString()));
    QList<QUrl> urls;
    urls.append(QUrl::fromLocalFile(fileDir));
    m_owner->startUrlsDrag(urls);
    QFile::remove(fileDir);
  }
  else if (drag_data->IsFragment()) {
    //qDebug() << "drag text" << QString::fromStdString(drag_data->GetFragmentText());
    m_owner->startTextDarg(QString::fromStdString(drag_data->GetFragmentText()),
                           QString::fromStdString(drag_data->GetFragmentHtml()));
  }
  else if (drag_data->IsLink()) {
    //qDebug() << "drag Link";
    m_owner->startTextDarg(QString::fromStdString(drag_data->GetFragmentText()), "");
  }
  return true;
}

Qt::CursorShape toQCursor(CefPageRenderer::CursorType type) {
  switch (type) {
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

#ifdef Q_OS_WIN
void CefPageRenderer::OnCursorChange(CefRefPtr<CefBrowser> browser, HCURSOR cursor,
                                     CursorType type, const CefCursorInfo &custom_cursor_info)
{
  if(m_enable){
    emit m_owner->cursorChanged(toQCursor(type));
  }
}
#elif defined(Q_OS_MAC)

void CefPageRenderer::OnCursorChange(CefRefPtr<CefBrowser> browser, CefCursorHandle cursor,
                                     CursorType type, const CefCursorInfo& custom_cursor_info) {
  if (m_enable) {
    qDebug() << "cursor changed" << toQCursor(type);
    emit m_owner->cursorChanged(toQCursor(type));
  }
}

#endif

void CefPageRenderer::setBuffer(void* pVoid, int width, int height) {
  m_gpu_buffer = pVoid;
  m_clean = m_height != height || m_width != width;
  m_height = height;
  m_width = width;
}

const void* CefPageRenderer::buffer() {
  return m_gpu_buffer;
}

bool BrowserListener::OnBeforeBrowse(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame, CefRefPtr<CefRequest> request,
                                     bool is_redirect) {
  if (request->GetResourceType() != RT_MAIN_FRAME) { //request page resources
    return false;
  }
  if (!m_enable) {
    return true;
  }

  QString url = QString::fromStdString(request->GetURL().ToString());
  QUrl qurl(url, QUrl::TolerantMode);
  //qDebug() << "on Before browse" << qurl << m_owner->m_url;
  if (qurl == m_owner->m_url) { //onbeforebrowse called from QT
    return false;
  }

  qint64 now = QDateTime::currentMSecsSinceEpoch();
  if (is_redirect || now - m_last_event_time > 5000) {
    if (m_redirect_enable) {
      emit m_owner->redirect(qurl);
      return false;
    }
    return true;
  }
  if (m_allow_link_trans) { //handle open new links with CEF
    m_owner->m_url = qurl;
    emit m_owner->urlChanged(qurl);
    return false;
  }
  emit m_owner->requestPage(qurl, false);
  return true;
}

bool BrowserListener::OnOpenURLFromTab(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame,
                                       const CefString& target_url,
                                       CefRequestHandler::WindowOpenDisposition target_disposition,
                                       bool user_gesture) {
  if (!m_enable) {
    return true;
  }
  QString url = QString::fromStdString(target_url);
  QUrl qurl(url, QUrl::TolerantMode);
  //qDebug() << "OnOpenURLFromTab" << qurl;
  emit m_owner->requestPage(qurl, true);
  return true;
}


bool BrowserListener::OnBeforePopup(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame,
                                    const CefString& target_url, const CefString& target_frame_name,
                                    CefLifeSpanHandler::WindowOpenDisposition target_disposition, bool user_gesture,
                                    const CefPopupFeatures& popupFeatures, CefWindowInfo& windowInfo,
                                    CefRefPtr<CefClient>& client, CefBrowserSettings& settings,
                                    bool* no_javascript_access) {
  if (!m_enable) {
    return true;
  }
  if(target_disposition == WOD_CURRENT_TAB){
    return false;
  }
  QUrl url(QString::fromStdString(target_url.ToString()), QUrl::TolerantMode);
  emit m_owner->requestPage(url, target_disposition == WOD_NEW_BACKGROUND_TAB);
  return true;
}

void BrowserListener::OnTitleChange(CefRefPtr<CefBrowser> browser, const CefString& title) {
  if (!m_enable) {
    return;
  }
  QString str = QString::fromStdString(title.ToString());
  //qDebug() << "title changed" << str;
  emit m_owner->titleChanged(str); //TODO crash here
}

void BrowserListener::OnFaviconURLChange(CefRefPtr<CefBrowser> browser, const std::vector<CefString>& icon_urls) {
  if (!m_enable || icon_urls.empty()) {
    return;
  }
  emit m_owner->iconChanged(QString::fromStdString(icon_urls[0].ToString())); //TODO crash here
}

void BrowserListener::OnLoadingStateChange(CefRefPtr<CefBrowser> browser, bool isLoading,
                                           bool canGoBack, bool canGoForward) {
  if (!isLoading && m_enable) {
    emit m_owner->loadEnd();
  }
}

void BrowserListener::OnBeforeDownload(CefRefPtr<CefBrowser> browser, CefRefPtr<CefDownloadItem> download_item,
                                       const CefString& suggested_name, CefRefPtr<CefBeforeDownloadCallback> callback) {
  m_owner->download(QString::fromStdString(download_item->GetURL().ToString()));
}

enum {
  MENU_USER_NEW_TAB = MENU_ID_USER_FIRST,
  MENU_USER_OPEN_IMAGGE,
  MENU_USER_SAVE_LINK_TO_STORAGE,
  MENU_USER_SAVE_IMAGE_TO_STORAGE,
  MENU_USER_DOWNLOAD
};

void BrowserListener::OnBeforeContextMenu(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame,
                                          CefRefPtr<CefContextMenuParams> params, CefRefPtr<CefMenuModel> model) {
  if (params->GetMediaType() == CM_MEDIATYPE_IMAGE) {
    model->Clear();
    model->AddItem(MENU_USER_OPEN_IMAGGE, "Открыть картинку");
    model->AddItem(MENU_USER_SAVE_IMAGE_TO_STORAGE, "Сохранить картинку в хранилище");
    model->AddItem(MENU_USER_DOWNLOAD, "Скачать картинку");
    return;
  }
  if (params->GetLinkUrl().size() > 0) {
    model->Clear();
    model->AddItem(MENU_USER_NEW_TAB, "Открыть в новой вкладке");
    model->AddItem(MENU_USER_SAVE_LINK_TO_STORAGE, "Сохранить ссылку в хранилище");
    return;
  }
}

//bool BrowserListener::RunContextMenu(CefRefPtr<CefBrowser> browser,
//                            CefRefPtr<CefFrame> frame,
//                            CefRefPtr<CefContextMenuParams> params,
//                            CefRefPtr<CefMenuModel> model,
//                            CefRefPtr<CefRunContextMenuCallback> callback) {

//  return false;
//}

void BrowserListener::OnBeforeClose(CefRefPtr<CefBrowser> browser) {
  if(m_enable)
    m_owner->onBrowserDestroyed();
}

bool BrowserListener::OnContextMenuCommand(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame,
                                           CefRefPtr<CefContextMenuParams> params, int command_id,
                                           EventFlags event_flags) {
  if (!m_enable) {
    return false;
  }
  switch (command_id) {
  case MENU_USER_NEW_TAB:
    emit m_owner->requestPage(QString::fromStdString(params->GetLinkUrl().ToString()), true);
    return true;
  case MENU_USER_OPEN_IMAGGE:
    emit m_owner->requestPage(QString::fromStdString(params->GetSourceUrl().ToString()), false);
    return true;
  case MENU_USER_SAVE_IMAGE_TO_STORAGE: {
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


void BrowserListener::userEventOccured() {
  m_last_event_time = QDateTime::currentMSecsSinceEpoch();
}

void BrowserListener::OnLoadStart(CefRefPtr<CefBrowser> browser,
                                  CefRefPtr<CefFrame> frame, TransitionType transition_type) {
  //  if (m_enable && frame->IsMain()) {
  //    browser->GetHost()->WasResized();
  //  }
}

void BrowserListener::OnAddressChange(CefRefPtr<CefBrowser> browser,
                                      CefRefPtr<CefFrame> frame,
                                      const CefString& url){
  if(m_enable && frame->IsMain()){
    QUrl qurl = QUrl(QString::fromStdString(url));
    emit m_owner->urlChanged(qurl);
  }
}

CefItem::CefItem(QQuickItem* parent): QQuickFramebufferObject(parent),
  m_listener(new BrowserListener(this)), m_renderer(new CefPageRenderer(this)),
  m_text_callback(new TextCallback(this)) {
  CEF_REQUIRE_UI_THREAD()
      QObject::connect(this, SIGNAL(windowChanged(QQuickWindow * )), this, SLOT(initBrowser(QQuickWindow * )),
                       Qt::QueuedConnection);
  setKeepMouseGrab(true);
  setKeepTouchGrab(true);
  m_listener->enable();
}

void CefItem::updateVisible() {
  if (m_browser) {
    m_browser->GetHost()->WasHidden(!isVisible());
  }
}

CefItem::~CefItem() {
  //CEF_REQUIRE_UI_THREAD()
  if(m_browser)
    removeFromShutDownGC();
  m_listener->disable();
  destroyBrowser();
}

QQuickFramebufferObject::Renderer* CefItem::createRenderer() const {
  return new QTPageRenderer(const_cast<CefItem*>(this));
}

class CookieContextHandler : public CefRequestContextHandler {
public:
  CookieContextHandler(bool enable_cookie) {
    m_manager = CefCookieManager::GetGlobalManager(NULL); /*new EmptyCookieManager();*/
  }

  virtual CefRefPtr<CefCookieManager> GetCookieManager() OVERRIDE {
    return m_manager;
  }

private:
  CefRefPtr<CefCookieManager> m_manager;
  IMPLEMENT_REFCOUNTING(CookieContextHandler)
};

class ACefClient : public CefClient {
public:
  void set(CefRefPtr<CefRenderHandler> renderer) {
    m_renderer = renderer;
  }

  void setBrowserListener(CefRefPtr<BrowserListener> listener) {
    m_listener = listener;
  }

  void setIO(CefRefPtr<IOBuffer> io) {
    m_io = io;
  }

  virtual CefRefPtr<CefRequestHandler> GetRequestHandler() OVERRIDE {
    return m_listener;
  }

  virtual CefRefPtr<CefRenderHandler> GetRenderHandler() OVERRIDE {
    return m_renderer;
  }

  virtual CefRefPtr<CefLifeSpanHandler> GetLifeSpanHandler() OVERRIDE {
    return m_listener;
  }

  virtual CefRefPtr<CefDisplayHandler> GetDisplayHandler() OVERRIDE {
    return m_listener;
  }

  virtual CefRefPtr<CefKeyboardHandler> GetKeyboardHandler() OVERRIDE {
    return m_io;
  }

  virtual CefRefPtr<CefDragHandler> GetDragHandler() OVERRIDE {
    return m_listener;
  }

  virtual CefRefPtr<CefLoadHandler> GetLoadHandler() OVERRIDE {
    return m_listener;
  }

  virtual CefRefPtr<CefDownloadHandler> GetDownloadHandler() OVERRIDE {
    return m_listener;
  }

  virtual CefRefPtr<CefContextMenuHandler> GetContextMenuHandler() OVERRIDE {
    return m_listener;
  }

private:
  CefRefPtr<CefRenderHandler> m_renderer;
  CefRefPtr<BrowserListener> m_listener;
  CefRefPtr<IOBuffer> m_io;
  IMPLEMENT_REFCOUNTING(ACefClient)
};

void CefItem::releaseResources() {
  destroyBrowser();
}

void CefItem::shutDown() {
  if (m_browser) {
    destroyBrowser();
  }
  else {
    removeFromShutDownGC();
  }
}

void CefItem::onBrowserDestroyed() {
  removeFromShutDownGC();
}


void CefItem::destroyBrowser() {
  if (m_browser) {
    m_renderer->disable();
    m_text_callback->disable();
    m_iobuffer->setBrowser(nullptr);
    m_browser->GetHost()->CloseBrowser(true);
    m_browser = nullptr;
  }
}

void CefItem::initBrowser(QQuickWindow* window) {
  if (!window || !m_running || (m_url.isEmpty() && m_html.isEmpty()))
    return;

  CEF_REQUIRE_UI_THREAD()
      m_iobuffer = new IOBuffer();
  m_renderer->enable();
  m_text_callback->enable();

  CefWindowInfo mainWindowInfo;
#ifdef Q_OS_WIN
  mainWindowInfo.SetAsWindowless((HWND) window->winId(), false);
#elif defined(Q_OS_MAC)
  mainWindowInfo.SetAsWindowless(reinterpret_cast<NSView*>(window->winId()));
  //  mainWindowInfo.SetAsChild(reinterpret_cast<NSView*>(window->winId()), x(), y(), width(), height());
#endif

  //qDebug() << "Init Browser " << QString::fromStdString(fromUrl(m_url)) << m_url;

  CefRefPtr<CefRequestContext> requestContext =
      CefRequestContext::CreateContext(CefRequestContextSettings(), new CookieContextHandler(m_cookies_enable));
  CefRefPtr<ACefClient> acefclient = new ACefClient();
  {
    acefclient->set(m_renderer);
    acefclient->setIO(m_iobuffer);
    acefclient->setBrowserListener(m_listener);
  }
  CefBrowserSettings settings;
  settings.windowless_frame_rate = 60;
  m_browser = CefBrowserHost::CreateBrowserSync(mainWindowInfo, acefclient, fromUrl(m_url), settings,
                                                requestContext);

  QObject::connect(this, SIGNAL(visibleChanged()), this, SLOT(updateVisible()));

  if (m_url.isEmpty()) {
    m_browser->GetMainFrame()->LoadString(m_html.toStdString(), "about:blank");
  }
  m_iobuffer->setBrowser(m_browser);
  addToShutDownGC();
}

void IOBuffer::setBrowser(CefRefPtr<CefBrowser> browser) {
  m_browser = browser;
}

void CefItem::mouseMove(int x, int y, int buttons, int modifiers) {
  if (!m_iobuffer) {
    return;
  }
  m_iobuffer->mouseMove(x, y, buttons, modifiers);
}

void CefItem::mousePress(int x, int y, int buttons, int modifiers) {
  if (!m_iobuffer) {
    return;
  }
  m_iobuffer->mousePress(x, y, buttons, modifiers);
}

void CefItem::mouseRelease(int x, int y, int buttons, int modifiers) {
  if (!m_iobuffer) {
    return;
  }
  m_listener->userEventOccured();
  m_iobuffer->mouseRelease(x, y, buttons, modifiers);
}

void CefItem::mouseWheel(int x, int y, int buttons, QPoint angle, int modifiers) {
  if (!m_iobuffer) {
    return;
  }
  m_iobuffer->mouseWheel(x, y, buttons, angle, modifiers);
}


cef_mouse_button_type_t getButton(int mouseButtons) {
  if (mouseButtons & Qt::LeftButton) {
    return MBT_LEFT;
  }
  if (mouseButtons & Qt::RightButton) {
    return MBT_RIGHT;
  }
  if (mouseButtons & Qt::MiddleButton) {
    return MBT_MIDDLE;
  }
  return MBT_LEFT;
}

void IOBuffer::mouseMove(int x, int y, int buttons, int modifiers) {
  if (m_browser) {
    CefMouseEvent event;
    event.x = x;
    event.y = y;
    event.modifiers = CefEventFactory::modifiersFromQtKeyBoardModifiers(modifiers) | CefEventFactory::mouseEventFlags(buttons);
    //    if(!(x % 20) || !(y % 20))
    //      qDebug() << "Move event" << x << y << "modifiers" << event.modifiers;
    m_browser->GetHost()->SendMouseMoveEvent(event, false);
  }
}

void IOBuffer::mousePress(int x, int y, int buttons, int modifiers) {
  if (m_browser) {
    int time = QTime::currentTime().msecsSinceStartOfDay();
    if (time - m_last_click_time < 200) {
      m_click_count++;
    }
    else {
      m_click_count = 1;
    }
    m_last_click_time = time;
    CefMouseEvent event;
    event.x = x;
    event.y = y;
    event.modifiers = CefEventFactory::modifiersFromQtKeyBoardModifiers(modifiers) | CefEventFactory::mouseEventFlags(buttons);
    qDebug() << "press Event" << x << y << "modifiers" << event.modifiers << "buttons" << buttons;
    m_browser->GetHost()->SendMouseClickEvent(event, getButton(buttons), false, m_click_count);
    m_key_flags |= CefEventFactory::mouseEventFlags(buttons);
  }
}

void IOBuffer::mouseRelease(int x, int y, int buttons, int modifiers) {
  if (m_browser) {
    CefMouseEvent event;
    event.x = x;
    event.y = y;
    qDebug() << "press Release" << x << y << "modifiers" << event.modifiers << "buttons" << buttons;
    m_key_flags &= ~CefEventFactory::mouseEventFlags(buttons);
    event.modifiers = CefEventFactory::modifiersFromQtKeyBoardModifiers(modifiers) | CefEventFactory::mouseEventFlags(buttons);
    m_browser->GetHost().get()->SendMouseClickEvent(event, getButton(buttons), true, m_click_count);
  }
}

void IOBuffer::mouseWheel(int x, int y, int buttons, QPoint angle, int modifiers) {
  if (m_browser) {
    CefMouseEvent event;
    event.x = x;
    event.y = y;
    event.modifiers = event.modifiers = CefEventFactory::modifiersFromQtKeyBoardModifiers(modifiers) | CefEventFactory::mouseEventFlags(buttons);;
    //qDebug() << "scroll x" << angle.x() << "y" << angle.y();
    m_browser->GetHost()->SendMouseWheelEvent(event, angle.x(), angle.y());
  }
}

bool IOBuffer::keyPress(QKeyEvent* event) {
  if (!m_browser) {
    return false;
  }
  m_pressed_keys.insert(event->key());
  m_browser->GetHost()->SendKeyEvent(CefEventFactory::createPressEvent(event));
  m_key_flags |= CefEventFactory::keyEventFlags(event);
  if (!event->text().isEmpty()){
    m_browser->GetHost()->SendKeyEvent(CefEventFactory::createCharEvent(event));
  }
  return true;
}

bool IOBuffer::keyRelease(QKeyEvent* event) {
  if (!m_browser) {
    return false;
  }
  if (!m_pressed_keys.contains(event->key())) {
    m_browser->GetHost()->SendKeyEvent(
          CefEventFactory::createPressEvent(event)); //qml sometimes gives only release events
  }
  else {
    m_pressed_keys.remove(event->key());
  }
  m_key_flags &= ~CefEventFactory::keyEventFlags(event);
  //qDebug() << "keyReleaseEvent" << "key:" << event->key() << "modifires" << event->modifiers();
  m_browser->GetHost()->SendKeyEvent(CefEventFactory::createReleaseEvent(event));
  return true;
}


class QOpenQuickEvent : public QObject {
public:
  QKeyEvent event;
};

bool CefItem::sendKeyPress(QObject* qKeyEvent) {
  if (!m_iobuffer) {
    return false;
  }
  QOpenQuickEvent* event2 = static_cast<QOpenQuickEvent*>((void*) qKeyEvent);
  return m_iobuffer->keyPress(&event2->event);
}

bool CefItem::sendKeyRelease(QObject* qKeyEvent) {
  if (!m_iobuffer) {
    return false;
  }
  QOpenQuickEvent* event2 = static_cast<QOpenQuickEvent*>((void*) qKeyEvent);
  return m_iobuffer->keyRelease(&event2->event);
}

CefMouseEvent createMouseEvent(double x, double y) {
  CefMouseEvent event;
  event.x = (int) x;
  event.y = (int) y;
  event.modifiers = 0;
  return event;
}

CefBrowserHost::DragOperationsMask translateAction(Qt::DropAction action) {
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

bool CefItem::dragEnterUrls(double x, double y, QList<QUrl> urls, Qt::DropAction action) {

  CefRefPtr<CefDragData> dragData = CefDragData::Create();
  for (QUrl url: urls) {
    QString surl = url.toString();
    if (surl.startsWith("file:///")) {
      surl = surl.mid(8);
    }
    //qDebug() << "enter" << x << y << surl << action;
    dragData->AddFile(surl.toStdString(), "some data"); //TODO change some data on smth
  }
  m_browser->GetHost()->DragTargetDragEnter(dragData, createMouseEvent(x, y), translateAction(action));
  return true;
}

bool CefItem::dragEnterText(double x, double y, QString text, Qt::DropAction action) {
  //qDebug() << "enter text" << x << y << text;
  CefRefPtr<CefDragData> dragData = CefDragData::Create();
  dragData->SetFragmentText(text.toStdString());
  m_browser->GetHost()->DragTargetDragEnter(dragData, createMouseEvent(x, y), translateAction(action));
  return true;
}

bool CefItem::dragEnterHtml(double x, double y, QString html, Qt::DropAction action) {
  //qDebug() << "enter html" << x << y << html;
  CefRefPtr<CefDragData> dragData = CefDragData::Create();
  dragData->SetFragmentHtml(html.toStdString());
  m_browser->GetHost()->DragTargetDragEnter(dragData, createMouseEvent(x, y), translateAction(action));
  return true;

}

bool CefItem::dragExit() {
  //qDebug() << "exit";
  m_browser->GetHost()->DragTargetDragLeave();
  return true;
}

bool CefItem::dragMove(double x, double y, Qt::DropAction action) {
  //qDebug() << "move" << x << y;
  m_browser->GetHost()->DragTargetDragOver(createMouseEvent(x, y), translateAction(action));
  return true;
}

bool CefItem::dragDrop(double x, double y) {
  //qDebug() << "drop" << x << y;
  m_browser->GetHost()->DragTargetDrop(createMouseEvent(x, y));
  return true;
}

void CefItem::setBrowserFocus(bool focus) {
  if (m_browser) {
    m_browser->GetHost()->SetFocus(focus);
  }
}

void CefItem::finishDrag() {
  m_browser->GetHost()->DragSourceSystemDragEnded();
}

CefBrowserHost::DragOperationsMask toDragOperationsMask(Qt::DropAction dropAction) {
  if (dropAction & Qt::DropAction::CopyAction) {
    return DRAG_OPERATION_COPY;
  }
  if (dropAction & Qt::DropAction::MoveAction) {
    return DRAG_OPERATION_MOVE;
  }
  if (dropAction & Qt::DropAction::LinkAction) {
    return DRAG_OPERATION_LINK;
  }
  return DRAG_OPERATION_NONE;
}

void CefItem::startDrag(QMimeData* mimeData) {
  QDrag* drag = new QDrag(this);
  drag->setMimeData(mimeData);
  emit dragStarted();
  Qt::DropAction dropAction = drag->exec();
  m_browser->GetHost()->DragSourceEndedAt(0, 0, toDragOperationsMask(dropAction));
  m_browser->GetHost()->DragSourceSystemDragEnded();
}

void CefItem::startTextDarg(const QString& text, const QString& html) {
  QMimeData* mimeData = new QMimeData;
  mimeData->setText(text);
  mimeData->setHtml(html);
  startDrag(mimeData);
}

void CefItem::startImageDrag(const QImage& img) {
  QMimeData* mimeData = new QMimeData;
  mimeData->setImageData(img);
  startDrag(mimeData);
}

void CefItem::startUrlsDrag(const QList<QUrl>& urls) {
  QMimeData* mimeData = new QMimeData;
  mimeData->setUrls(urls);
  startDrag(mimeData);
}

void CefItem::download(const QUrl& url) {
  Download* item = new Download(url, QStandardPaths::writableLocation(QStandardPaths::DownloadLocation));
  item->start();
  QQmlEngine::setObjectOwnership(item, QQmlEngine::JavaScriptOwnership);
  emit downloadStarted(item);
}


void CefItem::findText(const QString& text, bool findForward) {
  if (text.length() == 0) {
    m_browser->GetHost()->StopFinding(true);
    return;
  }
  m_current_search_id++;
  m_browser->GetHost()->Find(m_current_search_id, text.toStdString(), findForward, false, false);
}

void CefItem::selectAll() {
  m_browser->GetFocusedFrame()->SelectAll();
}

void CefItem::paste() {
  m_browser->GetFocusedFrame()->Paste();
}

void CefItem::cut() {
  m_browser->GetFocusedFrame()->Cut();
}

void CefItem::undo() {
  m_browser->GetFocusedFrame()->Undo();
}

void CefItem::copy() {
  m_browser->GetFocusedFrame()->Copy();
}

void CefItem::redo() {
  m_browser->GetFocusedFrame()->Redo();
}

void CefItem::reload() {
  m_browser->Reload();
}

void CefItem::loadHtml(const QString& html) {
  if (!html.size()) {
    return;
  }
  m_html = html;
  if (m_running && m_browser) {
    m_browser->GetMainFrame()->LoadString(m_html.toStdString(), "about:blank");
  } else {
    initBrowser(window());
  }
}

void CefItem::saveScreenshot(const QString& fileName, int x, int y, int w, int h) {
  m_renderer->processNextFrame([this, fileName, x, y, w, h](const void* buffer, int width, int heigth) {
    QImage img((uchar*) buffer + (width * y + x) * 4, w, h, width * 4, QImage::Format_RGBA8888);
    QImage res = img.rgbSwapped();
    res.save(fileName);
    emit screenShotSaved();
  });
  update();
}

//ContextMenuModel::ContextMenuModel(CefRefPtr<CefMenuModel> model){
//    for(int i = 0; i < model->GetCount(); i++){
//        m_options.append(QString::fromStdString(model->GetLabelAt(i).ToString()));
//        m_option_ids.insert(i, model->GetCommandIdAt(i));
//    }
//}

//void ContextMenuModel::select(int number){
//    m_callback->Continue(number, false);
//}

//void ContextMenuModel::cancle(){
//    m_callback->Cancel();
//}

void TextCallback::Visit(const CefString& string) {
  if (m_enabled) {
    emit m_owner->textRecieved(QString::fromStdString(string.ToString()));
  }
}

void CefItem::getText() {
  if (m_browser) {
    m_browser->GetMainFrame()->GetText(m_text_callback);
  }
}


void CefItem::clearCookies(const QString& url) {
  m_browser->GetHost()->GetRequestContext()->GetDefaultCookieManager(nullptr)->DeleteCookies(url.toStdString(), "",
                                                                                             nullptr);
}

void CefItem::executeJS(const QString& sctript) {
  if(m_browser){
    CefRefPtr<CefFrame> frame = m_browser->GetMainFrame();
    frame->ExecuteJavaScript(sctript.toStdString(), frame->GetURL(), 0);
  }
}

void CefItem::redirectEnable(bool redirect) {
  m_listener->redirectEnable(redirect);
}

void BrowserListener::redirectEnable(bool redirect) {
  m_redirect_enable = redirect;
}

void BrowserListener::enable() {
  m_enable = true;
}

void BrowserListener::disable() {
  m_enable = false;
}

double CefItem::zoomFactor() const {
  return m_zoom_factor;
}

void CefItem::setZoomFactor(double zoomFactor) {
  m_zoom_factor = zoomFactor;
  if (m_browser) {
    m_browser->GetHost()->SetZoomLevel(zoomFactor);
  }
}

QUrl CefItem::url() const {
  return m_url;
}

//QString parseURL(const QUrl &url){
//    if(url.host.contains("google.")){
//        QUrlQuery qurey(url);
//        QString qureyUrl = qurey.queryItemValue("url");
//        return qureyUrl == "" ? url.toString() : qureyUrl;
//    }
//    if(url.host.contains("yandex.")){
//        QUrlQuery qurey(url);
//        QString qureyUrl = qurey.queryItemValue("url");
//        return qureyUrl == "" ? url.toString() : qureyUrl;
//    }
//}

void CefItem::setUrl(const QUrl& url) {
  //qDebug() << "set url" << url;
  if (m_browser) {
    m_browser->GetMainFrame()->LoadURL(url.toString().toStdString());
  }
  else if (!url.isEmpty()) {
    m_url = url;
    initBrowser(window());
  }
  emit urlChanged(url);
}

bool CefItem::running() const {
  return m_running;
}

void CefItem::setRunning(bool running) {
  if (running && !m_running) {
    m_running = true;
    initBrowser(window());
  }
  else if (!running && m_running) {
    destroyBrowser();
    m_running = false;
  }
  else if (running && m_running && !m_browser) {
    initBrowser(window());
  }
}

bool CefItem::focused() const {
  return m_focused;
}

void CefItem::setFocused(bool focused) {
  if (m_browser)
    m_browser->GetHost()->SetFocus(focused);
  m_focused = focused;
}

bool CefItem::allowLinkTtransitions() {
  return m_listener->m_allow_link_trans;
}

void CefItem::setAllowLinkTtransitions(bool allow) {
  m_listener->m_allow_link_trans = allow;
}

void CefItem::setCookiesEnable(bool cookies) {
  m_cookies_enable = cookies;
}

bool CefItem::cookiesEnable() {
  return m_cookies_enable;
}

//bool CefItem::mute() {
//  return m_mute;
//}

//void CefItem::setMute(bool mute) {
//  m_mute = mute;
////  if(m_browser){
////    CefBrowserHostImpl* impl = static_cast<CefBrowserHostImpl*>(m_browser->get());
////    impl->web_contents()->SetAudioMuted(m_mute);
////  }
//}

}

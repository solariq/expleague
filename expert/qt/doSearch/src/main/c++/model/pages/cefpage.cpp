
#include "cefpage.h"
#include "include/wrapper/cef_helpers.h"
#include <QQuickWindow>
#include <QQmlEngine>
#include "dosearch.h"

namespace expleague {

namespace {

CefString fromUrl(const QUrl& url) {
  QString surl = url.toString();
  if (surl.isEmpty()) {
    return "about:blank";
  }
  if (surl.startsWith("qrc:/") && !surl.startsWith("qrc:///")) {
    return ("qrc:///" + surl.mid(5)).toStdString();
  }
  return surl.toStdString();
}

QString generateUniqueFileName(const QString& path, const QString& name){
  const QStringList files = QDir(path).entryList();
  if(!files.contains(name)){
    return name;
  }
  QFileInfo fileinfo(name);
  QString secondPart = "." + fileinfo.suffix();
  QString firstPart = name.left(name.size() - secondPart.size());
  if(firstPart.endsWith(".tar")){
    firstPart = firstPart.mid(0, firstPart.size() - 4);
    secondPart = ".tar" + secondPart;
  }
  for(int i = 1; ; i++){
    QString ret = QString("%1 (%2)%3").arg(firstPart).arg(i).arg(secondPart);
    if(!files.contains(ret)){
      return ret;
    }
  }
}

}


QOpenGLFramebufferObject* QTPageRenderer::createFramebufferObject(const QSize& size) {
  CefPageRenderer* renderer = m_owner->renderer();
  {
    std::lock_guard<std::mutex> guard(m_owner->m_mutex);
    auto old_buffer = renderer->buffer();
    if(old_buffer.data) delete[] old_buffer.data;
    renderer->setBuffer({size.width(), size.height(), new char[size.width()*size.height()*4], true});
  }

  const CefRefPtr<CefBrowser>& browser = m_owner->browser();
  if (browser.get())
    browser->GetHost()->WasResized();

  QOpenGLFramebufferObject* fbo = new QOpenGLFramebufferObject(size);
  qDebug() << "createFramebufferObject errors" << m_glfunc->glGetError();
  return fbo;
}

QTPageRenderer::QTPageRenderer(CefItem* owner): m_owner(owner) {
  m_glfunc = QOpenGLContext::currentContext()->functions();
  m_owner->renderer()->setRgbswap(true);
}

//QT reder thread
void QTPageRenderer::render() {
//  qDebug() << "render";
  assert(QOpenGLContext::currentContext());

  QOpenGLFramebufferObject* fbo = framebufferObject();
  const int height = fbo->height();
  const int width = fbo->width();

  m_window->resetOpenGLState();

//  const bool texturesEnabled = m_glfunc->glIsEnabled(GL_TEXTURE_2D);
//  if (!texturesEnabled)
//    m_glfunc->glEnable(GL_TEXTURE_2D);

  m_glfunc->glBindTexture(GL_TEXTURE_2D, fbo->texture());
  {

    std::lock_guard<std::mutex> guard(m_owner->m_mutex);
    auto buffer = m_owner->renderer()->buffer();
    if(buffer.data && width == buffer.width && height == buffer.height)
      m_glfunc->glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, buffer.width, buffer.height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer.data);

    auto popup = m_owner->renderer()->popup();
    if(popup.show)
      m_glfunc->glTexSubImage2D(GL_TEXTURE_2D, 0, popup.rect.x, popup.rect.y, popup.rect.width, popup.rect.height, GL_BGRA, GL_UNSIGNED_BYTE, popup.buffer);
  }

//  m_glfunc->glBindTexture(GL_TEXTURE_2D, 0);
//  if (texturesEnabled)
//    m_glfunc->glDisable(GL_TEXTURE_2D);

  qint64 now = QDateTime::currentMSecsSinceEpoch();
  if(now - m_owner->renderer()->lastRenderTime() < 60){
    update();
  }

}

//Qt render thread,  ui (onpaint, CefDoMessageLoopWork, QQuickFramebufferObject) blocked
void QTPageRenderer::synchronize(QQuickFramebufferObject* obj) {
  m_window = obj->window();
  m_window->setClearBeforeRendering(true);
}


QTPageRenderer_GL2_0::QTPageRenderer_GL2_0(CefItem *owner): m_owner(owner){
  m_glfunc = QOpenGLContext::currentContext()->versionFunctions<QOpenGLFunctions_2_0>();
}

void QTPageRenderer_GL2_0::render(){
//  qDebug() << "render";
  assert(QOpenGLContext::currentContext());

  QOpenGLFramebufferObject* fbo = framebufferObject();
  const int height = fbo->height();
  const int width = fbo->width();

  m_glfunc->glBindTexture(GL_TEXTURE_2D, fbo->texture());

  {
    std::lock_guard<std::mutex> guard(m_owner->m_mutex);
    if(m_owner->renderer()->buffer().clean && m_old_buffer){ //draw old buffer until render has new
      m_glfunc->glBindBuffer(GL_PIXEL_UNPACK_BUFFER, m_old_buffer);
      m_glfunc->glPixelStorei(GL_UNPACK_ROW_LENGTH, m_old_width);
      m_glfunc->glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, std::min(m_old_width, width), std::min(m_old_height, height), GL_BGRA, GL_UNSIGNED_BYTE, 0);
      m_glfunc->glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
    }else{
      m_glfunc->glBindBuffer(GL_PIXEL_UNPACK_BUFFER, m_buffer);
      m_glfunc->glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER);
      m_glfunc->glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_BGRA, GL_UNSIGNED_BYTE, 0);
      m_owner->renderer()->setBuffer({width, height, (char*)m_glfunc->glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL_WRITE_ONLY), false});
    }
    m_glfunc->glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
    auto popup = m_owner->renderer()->popup();
    if(popup.show)
      m_glfunc->glTexSubImage2D(GL_TEXTURE_2D, 0, popup.rect.x, popup.rect.y, popup.rect.width, popup.rect.height, GL_BGRA, GL_UNSIGNED_BYTE, popup.buffer);
  }

  m_glfunc->glBindTexture(GL_TEXTURE_2D, 0);
}

QOpenGLFramebufferObject* QTPageRenderer_GL2_0::createFramebufferObject(const QSize& size){
  CefPageRenderer* renderer = m_owner->renderer();
  if (size.height() != renderer->height() || size.width() != renderer->width()) {
    {
      std::lock_guard<std::mutex> guard(m_owner->m_mutex);
      if(m_buffer){
        if(!renderer->buffer().clean){
          if(m_old_buffer)
            m_glfunc->glDeleteBuffers(1, &m_old_buffer);
          m_old_buffer = m_buffer;
          m_old_width = renderer->width();
          m_old_height = renderer->height();
          m_glfunc->glBindBuffer(GL_UNIFORM_BUFFER, m_old_buffer);
          m_glfunc->glUnmapBuffer(GL_UNIFORM_BUFFER);
          m_glfunc->glBindBuffer(GL_UNIFORM_BUFFER, 0);
        }else{
          m_glfunc->glDeleteBuffers(1, &m_buffer);
        }
      }
    }

    m_glfunc->glGenBuffers(1, &m_buffer);
    m_glfunc->glBindBuffer(GL_PIXEL_UNPACK_BUFFER, m_buffer);
    m_glfunc->glBufferData(GL_PIXEL_UNPACK_BUFFER, size.width() * size.height() * 4, 0, GL_STREAM_DRAW);
    {
      std::lock_guard<std::mutex> guard(m_owner->m_mutex);
      renderer->setBuffer({size.width(), size.height(), (char*)m_glfunc->glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL_WRITE_ONLY), true});
    }
    m_glfunc->glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);

    const CefRefPtr<CefBrowser>& browser = m_owner->browser();
    if (browser.get())
      browser->GetHost()->WasResized();
  }
  QOpenGLFramebufferObject* fbo = new QOpenGLFramebufferObject(size);
  return fbo;
}


CefPageRenderer::CefPageRenderer(CefItem* owner): m_owner(owner) {
}

void CefPageRenderer::processNextFrame(std::function<void(const void*, int, int)> f) {
  m_next_frame_func = f;
}

void CefPageRenderer::disable() {
  std::lock_guard<std::mutex> guard(m_owner->m_mutex);
  m_enable = false;
}

void CefPageRenderer::enable() {
  m_enable = true;
}

//ui thread
bool CefPageRenderer::GetViewRect(CefRefPtr<CefBrowser> browser, CefRect& rect) {
  CEF_REQUIRE_UI_THREAD()
      rect = CefRect(0, 0, (int)(m_draw_buffer.width/m_scale_factor), (int)(m_draw_buffer.height/m_scale_factor));
  return true;
}

void copyRect(void* dest, int width, int height, void *buffer, CefRect& rect){ //source matches rect
  if (rect.width < width) {
    for (int i = 0; i < rect.height; i++) {
      const int offset = ((i + rect.y) * width + rect.x) * 4;
      memcpy((char*) dest + offset, (char*) buffer + i * rect.width * 4, (size_t) rect.width * 4);
    }
  }
  else  {
    const int offset = (rect.y * width + rect.x) * 4;
    memcpy((char*) dest + offset, (char*) buffer, (size_t) rect.width * rect.height * 4);
  }
}

void drawToBuffer(void* dest, const std::vector<CefRect>& dirtyRects,
                  const void* buffer, const int width, const int height, void* (*copyFunction)(void*, const void*, std::size_t) = memcpy){
  for (CefRect rect: dirtyRects) {
    if (rect.width < width) {
      for (int i = 0; i < rect.height; i++) {
        const int offset = ((i + rect.y) * width + rect.x) * 4;
        copyFunction((char*) dest + offset, (char*) buffer + offset, (size_t) rect.width * 4);
      }
    }
    else  {
      const int offset = (rect.y * width + rect.x) * 4;
      copyFunction((char*) dest + offset, (char*) buffer + offset, (size_t) rect.width * rect.height * 4);
    }
  }
}

void* copySwapRedAndBlue(void* dest, const void*  source, std::size_t width){
  uint32* d = (uint32*)dest;
  uint32* s = (uint32*)source;
  const uint32* end = s + width/4;
  while(s < end){
    *d = ((*s << 16) & 0xff0000) | ((*s >> 16) & 0xff) | (*s & 0xff00ff00);
    d++;
    s++;
  }
  return dest;
}

//ui thread
void CefPageRenderer::OnPaint(CefRefPtr<CefBrowser> browser, PaintElementType type, const RectList& dirtyRects,
                              const void* buffer, int width, int height) {
  if(!m_enable){
    return;
  }  
//  qDebug() << "on Paint" << "visible" << m_owner->isVisible() << (void*)m_owner;
  if(type == PET_POPUP){
    std::lock_guard<std::mutex> guard(m_owner->m_mutex);
    drawToBuffer(m_popup.buffer, dirtyRects, buffer, width, height, m_swap_RGB ? copySwapRedAndBlue: memcpy);
    m_last_frame_render_time = QDateTime::currentMSecsSinceEpoch();
    return;
  }

  {
    std::lock_guard<std::mutex> guard(m_owner->m_mutex);
    if (!m_draw_buffer.data  || m_draw_buffer.width != width || m_draw_buffer.height != height)
      return;

//      if(m_draw_buffer.clean){
    std::vector<CefRect> rects;
    rects.push_back(CefRect(0, 0, width, height));
    drawToBuffer(m_draw_buffer.data, rects, buffer, width, height,  m_swap_RGB ? copySwapRedAndBlue: memcpy);
    m_draw_buffer.clean = false;
    //  }else{
    //    drawToBuffer(m_draw_buffer.data, dirtyRects, buffer, width, height);
    //  }

  }

  if (m_next_frame_func) {
    m_next_frame_func(buffer, width, height);
    m_next_frame_func = nullptr;
  };
  m_last_frame_render_time = QDateTime::currentMSecsSinceEpoch();
  m_owner->update();
}

void CefPageRenderer::OnPopupShow(CefRefPtr<CefBrowser> browser, bool show) {
  std::lock_guard<std::mutex> guard(m_owner->m_mutex);
  m_popup.show = show;
}

void CefPageRenderer::OnPopupSize(CefRefPtr<CefBrowser> browser, const CefRect& rect) {
  std::lock_guard<std::mutex> guard(m_owner->m_mutex);
  if(!m_popup.buffer || m_popup.rect.width != rect.width || m_popup.rect.height != rect.height){
    if(m_popup.buffer)
      delete m_popup.buffer;
    m_popup.buffer = new char[rect.width * rect.height*4];
  }
  m_popup.rect = rect;
}

bool CefPageRenderer::GetScreenInfo(CefRefPtr<CefBrowser> browser,
                                    CefScreenInfo& screen_info) {
  if(!m_enable)
    return false;
  QQuickWindow* wind = m_owner->window();
  if(!wind){
    qWarning() << "Cef item hasnt window";
    return false;
  }
  m_scale_factor = (float) wind->devicePixelRatio();
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

const CefPageRenderer::Buffer& CefPageRenderer::buffer(){
  return m_draw_buffer;
}

void CefPageRenderer::setBuffer(const Buffer& buffer) {
  m_draw_buffer = buffer;
}

void CefPageRenderer::Buffer::update(int width_, int height_){
  if(width != width_ || height != height_){
    if(data) delete[] data;
    width = width_;
    height = height_;
    data = new char[width*height*4];
    clean = true;
  }
}

CefPageRenderer::Buffer::Buffer(int width_, int height_, char* data_, bool clean_):
  width(width_), height(height_), data(data_), clean(clean_){}


bool CefPageRenderer::StartDragging(CefRefPtr<CefBrowser> browser, CefRefPtr<CefDragData> drag_data,
                                    DragOperationsMask allowed_ops, int x, int y) {
  QMimeData* mimeData = new QMimeData();
  if (drag_data->IsFile()) {
    QString fileDir = QDir::tempPath() + "/" + QString::fromStdString(drag_data->GetFileName().ToString());
    drag_data->GetFileContents(CefStreamWriter::CreateForFile(fileDir.toStdString()));
    QList<QUrl> urls;
    urls.append(QUrl::fromLocalFile(fileDir));
    mimeData->setUrls(urls);
    m_owner->startDrag(mimeData);
    QFile::remove(fileDir);
  }
  else if (drag_data->IsFragment() || drag_data->IsLink()) {
    mimeData->setText(QString::fromStdString(drag_data->GetFragmentText()));
    mimeData->setHtml(QString::fromStdString(drag_data->GetFragmentHtml()));
    m_owner->startDrag(mimeData);
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


bool BrowserListener::OnBeforeBrowse(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame, CefRefPtr<CefRequest> request,
                                     bool is_redirect) {

  //TT_FORM_SUBMIT need for correct work of google search by image
  if (request->GetResourceType() != RT_MAIN_FRAME   || request->GetMethod() == "POST" || request->GetTransitionType() == TT_EXPLICIT) {
    return false;
  }
  if (!m_enable) {
    return true;
  }

  QString url = QString::fromStdString(request->GetURL().ToString());
  QUrl qurl(url, QUrl::TolerantMode);

  qDebug() << "on Before browse" << qurl << "is_redirect" << is_redirect << "transition type" << request->GetTransitionType();

  qint64 now = QDateTime::currentMSecsSinceEpoch();
  if (is_redirect ||  now - m_owner->lastUserActionTime() > 5000) { //redirect
    if (m_redirect_enable) {
      m_owner->m_url = qurl;
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
  QTimer::singleShot(1, [this, qurl](){
    emit m_owner->requestPage(qurl, false);
  });
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

void BrowserListener::OnRenderProcessTerminated(CefRefPtr<CefBrowser> browser,
                                                TerminationStatus status) {
  if(m_enable){
    m_owner->onPageTerminate();
  }
}

void BrowserListener::OnTitleChange(CefRefPtr<CefBrowser> browser, const CefString& title) {
  if (!m_enable) {
    return;
  }
  QString str = QString::fromStdString(title.ToString());
  //qDebug() << "title changed" << str;
  emit m_owner->titleChanged(str);
}

void BrowserListener::OnFaviconURLChange(CefRefPtr<CefBrowser> browser, const std::vector<CefString>& icon_urls) {
  if (!m_enable || icon_urls.empty()) {
    return;
  }
  emit m_owner->iconChanged(QString::fromStdString(icon_urls[0].ToString()));
}

void BrowserListener::OnLoadingStateChange(CefRefPtr<CefBrowser> browser, bool isLoading,
                                           bool canGoBack, bool canGoForward) {
  if (!isLoading && m_enable) {
    emit m_owner->loadEnd();
  }
}

void BrowserListener::OnBeforeDownload(CefRefPtr<CefBrowser> browser, CefRefPtr<CefDownloadItem> download_item,
                                       const CefString& suggested_name, CefRefPtr<CefBeforeDownloadCallback> callback) {
  m_owner->download(QString::fromStdString(download_item->GetURL().ToString()), QString::fromStdString(suggested_name));
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
    m_owner->download(QString::fromStdString(params->GetSourceUrl().ToString()), "");
    return true;
  }
  return true;
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

void BrowserListener::OnFullscreenModeChange(CefRefPtr<CefBrowser> browser, bool fullscreen){
  if(m_enable){
    if(fullscreen)
      m_owner->window()->showFullScreen();
    else
      m_owner->window()->showNormal();
    emit m_owner->fullScreenChanged(fullscreen);
  }
}

CefItem::CefItem(QQuickItem* parent): QQuickFramebufferObject(parent),
  m_listener(new BrowserListener(this)), m_renderer(new CefPageRenderer(this)),
  m_text_callback(new TextCallback(this)) {
  CEF_REQUIRE_UI_THREAD();
  QObject::connect(this, SIGNAL(windowChanged(QQuickWindow * )), this, SLOT(onWindowChanged(QQuickWindow*)));

  setKeepMouseGrab(true);
  setKeepTouchGrab(true);
  m_listener->enable();
}

void CefItem::onVisibleChanged() {
  if (m_browser) {
    m_browser->GetHost()->WasHidden(!isVisible());
  }
}

void CefItem::onWindowChanged(QQuickWindow *window){
  initBrowser(window);
}

CefItem::~CefItem() {
  removeFromShutDownGC();
  m_listener->disable();
  destroyBrowser();
}

QQuickFramebufferObject::Renderer* CefItem::createRenderer() const {
  if(QOpenGLContext::currentContext()->versionFunctions<QOpenGLFunctions_2_0>())
    return new QTPageRenderer_GL2_0(const_cast<CefItem*>(this));
  else
    return new QTPageRenderer(const_cast<CefItem*>(this));
}


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

  virtual bool OnProcessMessageReceived(CefRefPtr<CefBrowser> browser,
                                        CefProcessId source_process,
                                        CefRefPtr<CefProcessMessage> message) {
    qDebug() << "IPC message recieved" << QString::fromStdString(message->GetName());
    return false;
  }

private:
  CefRefPtr<CefRenderHandler> m_renderer;
  CefRefPtr<BrowserListener> m_listener;
  CefRefPtr<IOBuffer> m_io;
  IMPLEMENT_REFCOUNTING(ACefClient)
};

void CefItem::releaseResources() {
  destroyBrowser();
  QQuickFramebufferObject::releaseResources();
}

void CefItem::shutDown() {
//  qDebug() << "shutDown()" << m_url;
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
    qDebug() << "DestroyBrowser" << m_url << this;
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

  CEF_REQUIRE_UI_THREAD();
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

  qDebug() << "Init Browser " << QString::fromStdString(fromUrl(m_url)) << (void*)this;

  CefRefPtr<ACefClient> acefclient = new ACefClient();
  {
    acefclient->set(m_renderer);
    acefclient->setIO(m_iobuffer);
    acefclient->setBrowserListener(m_listener);
  }
  CefBrowserSettings settings;
  settings.windowless_frame_rate = 60;
  m_browser = CefBrowserHost::CreateBrowserSync(mainWindowInfo, acefclient, fromUrl(m_url), settings,
                                                nullptr);
  m_browser->GetHost()->WasHidden(!isVisible());

  QObject::connect(this, SIGNAL(visibleChanged()), this, SLOT(onVisibleChanged()));

  if (m_url.isEmpty()) {
    m_browser->GetMainFrame()->LoadString(m_html.toStdString(), "about:blank");
  }
  m_iobuffer->setBrowser(m_browser);
  addToShutDownGC();
}

void CefItem::updateLastUserActionTime(){
  m_last_user_action_time = QDateTime::currentMSecsSinceEpoch();
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
  updateLastUserActionTime();
  m_iobuffer->mousePress(x, y, buttons, modifiers);
}

void CefItem::mouseRelease(int x, int y, int buttons, int modifiers) {
  if (!m_iobuffer) {
    return;
  }
  updateLastUserActionTime();
  m_iobuffer->mouseRelease(x, y, buttons, modifiers);
}

void CefItem::mouseWheel(int x, int y, int buttons, QPoint angle, int modifiers) {
  if (!m_iobuffer) {
    return;
  }
  updateLastUserActionTime();
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
    if (time - m_last_click_time < 500) {
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
    //    qDebug() << "press Event" << x << y << "modifiers" << event.modifiers << "buttons" << buttons;
    m_browser->GetHost()->SendMouseClickEvent(event, getButton(buttons), false, m_click_count);
    m_key_flags |= CefEventFactory::mouseEventFlags(buttons);
  }
}

void IOBuffer::mouseRelease(int x, int y, int buttons, int modifiers) {
  if (m_browser) {
    CefMouseEvent event;
    event.x = x;
    event.y = y;
    //    qDebug() << "press Release" << x << y << "modifiers" << event.modifiers << "buttons" << buttons;
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
  CefKeyEvent cefEv = CefEventFactory::createPressEvent(event);
//  qDebug() << "keyPressEvent" << "key:" << event->key() << "modifires" << event->modifiers();
  m_browser->GetHost()->SendKeyEvent(cefEv);
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
//    qDebug() << "Released not pressed key" << "key:" << event->key() << "modifires" << event->modifiers();
    m_browser->GetHost()->SendKeyEvent(
          CefEventFactory::createPressEvent(event)); //qml sometimes gives only release events
  }
  else {
    m_pressed_keys.remove(event->key());
  }
  m_key_flags &= ~CefEventFactory::keyEventFlags(event);
//  qDebug() << "keyReleaseEvent" << "key:" << event->key() << "modifires" << event->modifiers();
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
  updateLastUserActionTime();
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
//    qDebug() << "enter" << x << y << surl << action;
    dragData->AddFile(surl.toStdString(), "");
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
  updateLastUserActionTime();
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

void CefItem::startDrag(QMimeData *mimeData){
  QDrag* drag = new QDrag(this);
  drag->setMimeData(mimeData);
  emit dragFromCefStarted();
  Qt::DropAction dropAction = drag->exec();
  m_browser->GetHost()->DragSourceEndedAt(0, 0, toDragOperationsMask(dropAction));
  m_browser->GetHost()->DragSourceSystemDragEnded();
  emit dragFromCefFinished();
}

void CefItem::download(const QUrl& url, const QString& name) {
  qDebug() << "download url" << url;
  QString path = QStandardPaths::writableLocation(QStandardPaths::DownloadLocation);
  QString fileName = generateUniqueFileName(path, name == "" ? url.fileName(): name);
  Download* item = new Download(url, path, fileName);
  emit downloadStarted(item);
}

void CefItem::onPageTerminate(){
  qDebug() << "Page" << m_url << "is dead";
  destroyBrowser();
  auto wind = window();
  if(wind)
    initBrowser(wind);
}

qint64 CefItem::lastUserActionTime(){
  return m_last_user_action_time;
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

void CefItem::setUrl(const QUrl& url) {
  if(m_url == url){
    return;
  }
  m_url = url;
//  if (m_browser) {
//    qDebug() << "set url" << url.toString();
//    CefRefPtr<CefBrowser> browser = m_browser;
//    browser->GetMainFrame()->LoadURL(url.toString().toStdString());
//  }
//  else if (!url.isEmpty()) {
//    qDebug() << "init with url" << m_url;
//    initBrowser(window());
//  } //doesnt work if other url loading now
  destroyBrowser();
  initBrowser(window());
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


}

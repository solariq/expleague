#ifndef CEFPAGE_H
#define CEFPAGE_H


#include <QtCore>
#include "../page.h"
#include "../../cef.h"
#include "../../cef/cefeventfactory.h"

#include <QQuickFramebufferObject>
#include <QTimer>
#include <mutex>
#include <QtQuick>

#include "../downloads.h"


namespace expleague {


class CefItem;

class CefPageRenderer;

class BrowserListener;

class TextCallback;

class CefItem;

class QTPageRenderer : public QQuickFramebufferObject::Renderer {
public:
  explicit QTPageRenderer(CefItem* owner);
  ~QTPageRenderer();
  //Qt methods
  virtual void render();

  virtual QOpenGLFramebufferObject* createFramebufferObject(const QSize& size);

  virtual void synchronize(QQuickFramebufferObject* obj);
private:
  CefItem* const m_owner;
  GLuint m_buffer = 0;
  friend class CefPageRenderer;
  QQuickWindow* m_window = nullptr;
};

class CefPageRenderer : public CefRenderHandler {

  struct Popup{
    bool show = false;
    CefRect rect;
    void* buffer = nullptr;
  };

public:
  void disable(); //stop render on QT level
  void enable();

  int width() const { return m_width; }
  int height() const { return m_height; }

  Popup popup(){ return m_popup; }

  void processNextFrame(std::function<void(const void* buffer, int w, int h)>);

  virtual bool GetViewRect(CefRefPtr<CefBrowser> browser, CefRect& rect) OVERRIDE;

  virtual void OnPaint(CefRefPtr<CefBrowser> browser, PaintElementType type,
                       const RectList& dirtyRects, const void* buffer,
                       int width, int height) OVERRIDE;

  virtual bool GetScreenInfo(CefRefPtr<CefBrowser> browser, CefScreenInfo& screen_info) OVERRIDE;


  virtual void OnPopupShow(CefRefPtr<CefBrowser> browser,
                           bool show) OVERRIDE;

  virtual void OnPopupSize(CefRefPtr<CefBrowser> browser,
                           const CefRect& rect) OVERRIDE;


#ifdef Q_OS_WIN
  virtual void OnCursorChange(CefRefPtr<CefBrowser> browser, HCURSOR cursor, CursorType type, const CefCursorInfo &custom_cursor_info) OVERRIDE;
#elif defined(Q_OS_MAC)
  virtual void OnCursorChange(CefRefPtr<CefBrowser> browser, CefCursorHandle cursor, CursorType type, const CefCursorInfo& custom_cursor_info) OVERRIDE;
#endif

  virtual bool GetScreenPoint(CefRefPtr<CefBrowser> browser, int viewX, int viewY, int& screenX, int& screenY) OVERRIDE;
  virtual bool StartDragging(CefRefPtr<CefBrowser> browser, CefRefPtr<CefDragData> drag_data, DragOperationsMask allowed_ops, int x, int y) OVERRIDE;

  const void* buffer();
  void setBuffer(void* pVoid, int width, int height);

IMPLEMENT_REFCOUNTING(CefPageRenderer)
public:
  explicit CefPageRenderer(CefItem* owner);

private:
  bool m_enable = false;
  int m_height, m_width;
  CefItem* const m_owner;
  std::function<void(const void* buffer, int w, int h)> m_next_frame_func;
  bool m_clean = true;
  void* m_gpu_buffer = nullptr;
  float m_scale_factor = 1.f;
  Popup m_popup;
};


class IOBuffer : public CefKeyboardHandler {
public:
  void setBrowser(CefRefPtr<CefBrowser> browser);

  void mouseMove(int x, int y, int buttons, int modifiers);

  void mousePress(int x, int y, int buttons, int modifiers);

  void mouseRelease(int x, int y, int buttons, int modifiers);

  void mouseWheel(int x, int y, int buttons, QPoint angle, int modifiers);

  bool keyPress(QKeyEvent* event);

  bool keyRelease(QKeyEvent* event);

IMPLEMENT_REFCOUNTING(IOBuffer)
private:
  CefRefPtr<CefBrowser> m_browser;
  uint32 m_key_flags = EVENTFLAG_NONE;
  int m_last_click_time;
  int m_click_count;
  QSet<int> m_pressed_keys;
};

class CefItem : public QQuickFramebufferObject, ShutDownGCItem {
Q_OBJECT

  Q_PROPERTY(QUrl url READ url WRITE setUrl NOTIFY urlChanged)//url of webpage
  Q_PROPERTY(double zoomFactor READ zoomFactor WRITE setZoomFactor)
  Q_PROPERTY(bool running READ running WRITE setRunning)
  Q_PROPERTY(bool focused READ focused WRITE setFocused)
  Q_PROPERTY(bool allowLinkTtransitions READ allowLinkTtransitions WRITE setAllowLinkTtransitions)
  Q_PROPERTY(bool cookiesEnable READ cookiesEnable WRITE setCookiesEnable)

public:
  explicit CefItem(QQuickItem* parent = 0);

  virtual ~CefItem();

  virtual Renderer* createRenderer() const;

  virtual void releaseResources();

  virtual void shutDown();

  void onBrowserDestroyed();

  Q_INVOKABLE void redirectEnable(bool redirect);

  Q_INVOKABLE bool sendKeyPress(QObject* qKeyEvent);

  Q_INVOKABLE bool sendKeyRelease(QObject* qKeyEvent);

  Q_INVOKABLE void setBrowserFocus(bool focus);

  Q_INVOKABLE void mouseMove(int x, int y, int buttons, int modifiers);

  Q_INVOKABLE void mousePress(int x, int y, int buttons, int modifiers);

  Q_INVOKABLE void mouseRelease(int x, int y, int buttons, int modifiers);

  Q_INVOKABLE void mouseWheel(int x, int y, int buttons, QPoint angle, int modifiers);

  Q_INVOKABLE bool dragEnterUrls(double x, double y, QList<QUrl> urls, Qt::DropAction action);

  Q_INVOKABLE bool dragEnterText(double x, double y, QString text, Qt::DropAction action);

  Q_INVOKABLE bool dragEnterHtml(double x, double y, QString html, Qt::DropAction action);

  Q_INVOKABLE bool dragExit();

  Q_INVOKABLE bool dragMove(double x, double y, Qt::DropAction action);

  Q_INVOKABLE bool dragDrop(double x, double y);

  Q_INVOKABLE void finishDrag();

  Q_INVOKABLE void findText(const QString& s, bool findForward);

  Q_INVOKABLE void selectAll();

  Q_INVOKABLE void paste();

  Q_INVOKABLE void copy();

  Q_INVOKABLE void cut();

  Q_INVOKABLE void undo();

  Q_INVOKABLE void redo();

  Q_INVOKABLE void reload();

  Q_INVOKABLE void loadHtml(const QString& html);

  Q_INVOKABLE void saveScreenshot(const QString& fileName, int x, int y, int w, int h);

  Q_INVOKABLE void getText();

  Q_INVOKABLE void clearCookies(const QString& domain);

  Q_INVOKABLE void executeJS(const QString& sctript);

  void startDrag(QMimeData* data);

  void download(const QUrl& url);

  void onPageTerminate();


  qint64 lastUserActionTime();

  virtual QQuickItem* asItem() {
    return this;
  }

  CefPageRenderer* renderer() const{
    return m_renderer.get();
  }

  CefRefPtr<CefBrowser> browser() const { return m_browser; }

private:
  CefRefPtr<CefBrowser> m_browser = nullptr;
  CefRefPtr<IOBuffer> m_iobuffer = nullptr;

  const CefRefPtr<CefPageRenderer> m_renderer;
  const CefRefPtr<BrowserListener> m_listener;
  const CefRefPtr<TextCallback> m_text_callback;

  int m_current_search_id = 0;

private:
  QUrl m_url;
  QString m_html = "";
  double m_zoom_factor = 1;
  bool m_running = true;
  bool m_cookies_enable = true;
  bool m_mute;
  std::mutex m_mutex;
  qint64 m_last_user_action_time = 0;
  //property methods

public:
  QUrl url() const;
  void setUrl(const QUrl& url);

  bool running() const;
  void setRunning(bool running);

  bool focused() const;
  void setFocused(bool focused);

  double zoomFactor() const;
  void setZoomFactor(double zoomFactor);

  bool allowLinkTtransitions();
  void setAllowLinkTtransitions(bool);

  bool cookiesEnable();
  void setCookiesEnable(bool cookies);

signals:

  void urlChanged(const QUrl& url);

  void requestPage(const QUrl& url, bool newTab);

  void redirect(const QUrl& url);

  void titleChanged(const QString& title);

  void iconChanged(const QString& icon);

  void dragFromCefStarted();

  void dragFromCefFinished();

  void cursorChanged(Qt::CursorShape cursorShape);

  void screenShotSaved();

  void loadEnd();

  void textRecieved(const QString& text);

  void downloadStarted(Download* item);

  void savedToStorage(const QString& text);

  void fullScreenChanged(bool fullScreen);

private slots:
  void initBrowser(QQuickWindow* window);
  void updateVisible();

private:
  void destroyBrowser();
  void updateLastUserActionTime();

  friend class BrowserListener;
  friend class CefPageRenderer;
  friend class QTPageRenderer;
  bool m_focused = false;
  void updateJS();
};

class TextCallback : public CefStringVisitor {
public:
  virtual void Visit(const CefString& string) OVERRIDE;

  void enable() { m_enabled = true; }
  void disable() { m_enabled = false; }

public:
  explicit TextCallback(CefItem* owner): m_owner(owner) {}

private:
  bool m_enabled = false;
  CefItem* const m_owner;
IMPLEMENT_REFCOUNTING(TextCallback)
};


class BrowserListener : public CefRequestHandler,
                        public CefLifeSpanHandler,
                        public CefDisplayHandler,
                        public CefKeyboardHandler,
                        public CefDragHandler,
                        public CefLoadHandler,
                        public CefDownloadHandler,
                        public CefContextMenuHandler {
public:
  BrowserListener(CefItem* owner) : m_owner(owner) {}

  virtual bool OnBeforeBrowse(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame,
                              CefRefPtr<CefRequest> request, bool is_redirect) OVERRIDE;

  virtual bool OnOpenURLFromTab(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame,
                                const CefString& target_url,
                                CefRequestHandler::WindowOpenDisposition target_disposition,
                                bool user_gesture) OVERRIDE;

  virtual bool OnBeforePopup(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame,
                             const CefString& target_url, const CefString& target_frame_name,
                             CefLifeSpanHandler::WindowOpenDisposition target_disposition, bool user_gesture,
                             const CefPopupFeatures& popupFeatures, CefWindowInfo& windowInfo,
                             CefRefPtr<CefClient>& client, CefBrowserSettings& settings,
                             bool* no_javascript_access) OVERRIDE;

  virtual void OnRenderProcessTerminated(CefRefPtr<CefBrowser> browser,
                                         TerminationStatus status);

  virtual void OnTitleChange(CefRefPtr<CefBrowser> browser, const CefString& title) OVERRIDE;

  virtual void OnFaviconURLChange(CefRefPtr<CefBrowser> browser, const std::vector<CefString>& icon_urls) OVERRIDE;

  virtual void OnLoadingStateChange(CefRefPtr<CefBrowser> browser, bool isLoading,
                                    bool canGoBack, bool canGoForward) OVERRIDE;

  virtual void OnBeforeDownload(CefRefPtr<CefBrowser> browser, CefRefPtr<CefDownloadItem> download_item,
                                const CefString& suggested_name,
                                CefRefPtr<CefBeforeDownloadCallback> callback) OVERRIDE;

  virtual void OnBeforeContextMenu(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame,
                                   CefRefPtr<CefContextMenuParams> params, CefRefPtr<CefMenuModel> model) OVERRIDE;

  virtual bool OnContextMenuCommand(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame,
                                    CefRefPtr<CefContextMenuParams> params, int command_id,
                                    EventFlags event_flags) OVERRIDE;

  virtual bool RunContextMenu(CefRefPtr<CefBrowser> browser,
                              CefRefPtr<CefFrame> frame,
                              CefRefPtr<CefContextMenuParams> params,
                              CefRefPtr<CefMenuModel> model,
                              CefRefPtr<CefRunContextMenuCallback> callback) OVERRIDE {
    return false;
  }

  virtual void OnBeforeClose(CefRefPtr<CefBrowser> browser) OVERRIDE;


  virtual void OnLoadStart(CefRefPtr<CefBrowser> browser,
                           CefRefPtr<CefFrame> frame,
                           TransitionType transition_type) OVERRIDE;

  virtual void OnAddressChange(CefRefPtr<CefBrowser> browser,
                               CefRefPtr<CefFrame> frame,
                               const CefString& url);

  virtual void OnFullscreenModeChange(CefRefPtr<CefBrowser> browser,
                                      bool fullscreen);

  virtual ReturnValue OnBeforeResourceLoad(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame,
                                           CefRefPtr<CefRequest> request, CefRefPtr<CefRequestCallback> callback)
  {
    QString str(QString::fromStdString(request->GetURL()));
//    qDebug() << "OnBeforeResourceLoad" << str;
    return RV_CONTINUE;
  }

  virtual void OnResourceLoadComplete(CefRefPtr<CefBrowser> browser,
                                      CefRefPtr<CefFrame> frame,
                                      CefRefPtr<CefRequest> request,
                                      CefRefPtr<CefResponse> response,
                                      URLRequestStatus status,
                                      int64 received_content_length)
  {
    QString str(QString::fromStdString(request->GetURL()));
    QString str2(QString::fromStdString(response->GetStatusText()));
//    qDebug() << "OnResourceLoadComplete" << str << "error" << response->GetError() << str2 << response->GetStatus();
  }

  void redirectEnable(bool);

  void enable();
  void disable();

IMPLEMENT_REFCOUNTING(CefRequestHandler)
private:
  CefItem* const m_owner;
  bool m_redirect_enable = true;
  bool m_enable;
  bool m_allow_link_trans = false;

  friend class CefItem;
};
}
#endif // CEFPAGE_H

#ifndef CEFPAGE_H
#define CEFPAGE_H

#include "../page.h"
#include "../../cef.h"
#include "../../cef/cefeventfactory.h"


#include <QQuickFramebufferObject>
#include <QTimer>
#include <QtOpenGL>
#include <mutex>

#if _MSC_VER && !__INTEL_COMPILER
#pragma warning (push, 0)
#else
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wall"
#endif

#include "include/cef_app.h"
#include "include/cef_client.h"
#include "include/cef_render_process_handler.h"
#include "include/cef_browser.h"

#if _MSC_VER && !__INTEL_COMPILER
#pragma warning (pop)
#else
#pragma GCC diagnostic pop
#endif

#include "../downloads.h"


namespace expleague {


class CefItem;

class CefPageRenderer;

class BrowserListener;

class TextCallback;

struct image_buffer {
  int width;
  int height;
  const void *data;
};


class QTPageRenderer : public QQuickFramebufferObject::Renderer {
public:
  QTPageRenderer(CefRefPtr<CefPageRenderer> renderer);

  //Qt methods
  virtual void render();

  virtual QOpenGLFramebufferObject *createFramebufferObject(const QSize &size);

  virtual void synchronize(QQuickFramebufferObject *obj);

  //Cef methods
  void clearBuffer();

  virtual ~QTPageRenderer();

private:
  CefRefPtr<CefPageRenderer> m_renderer;
  QQuickWindow *m_window;
};

class CefPageRenderer : public CefRenderHandler {
public:
  CefPageRenderer(CefItem *owner);

  void setSize(int height, int width);

  void clearBuffer();

  void bind();

  void pause(); //stop render draw white square
  void resume();

  void stop(); //stop render on QT level
  void start();

  void synchronize(QQuickFramebufferObject *obj);

  void processNextFrame(std::function<void(const void *buffer, int w, int h)>);

  virtual bool GetViewRect(CefRefPtr<CefBrowser> browser, CefRect &rect) OVERRIDE;

  virtual void OnPaint(CefRefPtr<CefBrowser> browser, PaintElementType type,
                       const RectList &dirtyRects, const void *buffer,
                       int width, int height) OVERRIDE;

  #ifdef Q_OS_WIN
  virtual void OnCursorChange(CefRefPtr<CefBrowser> browser, HCURSOR cursor, CursorType type, const CefCursorInfo &custom_cursor_info) OVERRIDE;
  #elif defined(Q_OS_MAC)
  virtual void OnCursorChange(CefRefPtr<CefBrowser> browser, CefCursorHandle cursor, CursorType type, const CefCursorInfo &custom_cursor_info)  OVERRIDE;
  #endif

  virtual bool GetScreenPoint(CefRefPtr<CefBrowser> browser, int viewX, int viewY, int &screenX, int &screenY)  OVERRIDE;

  virtual bool
  StartDragging(CefRefPtr<CefBrowser> browser, CefRefPtr<CefDragData> drag_data, DragOperationsMask allowed_ops, int x, int y) OVERRIDE;

IMPLEMENT_REFCOUNTING(CefPageRenderer)
private:
  image_buffer m_buffer;
  bool m_enable = true;
  bool m_pause = false;
  int m_x;
  int m_y;
  int m_new_height;
  int m_new_width;
  int m_texture_height = 0;
  int m_texture_width = 0;
  GLuint m_screen_tex = 0;
  CefItem *m_owner;
  std::mutex m_mutex;
  std::vector<CefRect> m_dirty_rects;
  std::function<void(const void *buffer, int w, int h)> m_next_frame_func;
};

class IOBuffer {
public:
  void setBrowser(CefRefPtr<CefBrowser> browser);

  void mouseMove(int x, int y, int buttons);

  void mousePress(int x, int y, int buttons);

  void mouseRelease(int x, int y, int buttons);

  void mouseWheel(int x, int y, int buttons, QPoint angle);

  bool keyPress(QKeyEvent* event);

  bool keyRelease(QKeyEvent* event);

//  virtual bool OnPreKeyEvent(CefRefPtr<CefBrowser> browser,  const CefKeyEvent& event,
//                             CefEventHandle os_event, bool* is_keyboard_shortcut);

//  virtual bool OnKeyEvent(CefRefPtr<CefBrowser> browser, const CefKeyEvent& event,
//                          CefEventHandle os_event);
private:
  CefRefPtr<CefBrowser> m_browser;
  uint32 m_key_flags = EVENTFLAG_NONE;
  int m_last_click_time;
  int m_click_count;
};


class CefItem : public QQuickFramebufferObject, Browser {
Q_OBJECT
  Q_PROPERTY(QUrl url
                     READ
                     url
                     WRITE
                     setUrl
                     NOTIFY
                     urlChanged)
  Q_PROPERTY(double zoomFactor
                     READ
                     zoomFactor
                     WRITE
                     setZoomFactor)
  Q_PROPERTY(bool running
                     READ
                     running
                     WRITE
                     setRunning)
  Q_PROPERTY(bool allowLinkTtransitions
                     READ
                     allowLinkTtransitions
                     WRITE
                     setAllowLinkTtransitions)
  Q_PROPERTY(bool cookiesEnable
                     READ
                     cookiesEnable
                     WRITE
                     setCookiesEnable)
public:
  CefItem(QQuickItem *parent = 0);

  ~CefItem();

  virtual Renderer *createRenderer() const;

  virtual void releaseResources();

  virtual void shutDown();

  void onBrowserDestroyed();

  Q_INVOKABLE void redirectEnable(bool redirect);

  Q_INVOKABLE bool
  sendKeyPress(QObject* qKeyEvent);

  Q_INVOKABLE bool
  sendKeyRelease(QObject* qKeyEvent);

  Q_INVOKABLE void setBrowserFocus(bool focus);

  Q_INVOKABLE void mouseMove(int x, int y, int buttons);

  Q_INVOKABLE void mousePress(int x, int y, int buttons);

  Q_INVOKABLE void mouseRelease(int x, int y, int buttons);

  Q_INVOKABLE void mouseWheel(int x, int y, int buttons, QPoint angle);

  Q_INVOKABLE bool dragEnterUrls(double x, double y, QList<QUrl> urls, Qt::DropAction action);

  Q_INVOKABLE bool dragEnterText(double x, double y, QString text, Qt::DropAction action);

  Q_INVOKABLE bool dragEnterHtml(double x, double y, QString html, Qt::DropAction action);

  Q_INVOKABLE bool dragExit();

  Q_INVOKABLE bool dragMove(double x, double y, Qt::DropAction action);

  Q_INVOKABLE bool dragDrop(double x, double y);

  Q_INVOKABLE void finishDrag();

  Q_INVOKABLE void findText(const QString &s, bool findForward);

  Q_INVOKABLE void selectAll();

  Q_INVOKABLE void paste();

  Q_INVOKABLE void copy();

  Q_INVOKABLE void cut();

  Q_INVOKABLE void undo();

  Q_INVOKABLE void redo();

  Q_INVOKABLE void reload();

  Q_INVOKABLE void loadHtml(const QString &html);

  Q_INVOKABLE void saveScreenshot(const QString &fileName, int x, int y, int w, int h);

  Q_INVOKABLE void getText();

  Q_INVOKABLE void clearCookies(const QString &domain);

  void startTextDarg(const QString &text, const QString &html);

  void startImageDrag(const QImage &img);

  void startUrlsDrag(const QList<QUrl> &urls);

  void startDrag(QMimeData *data);

  void download(const QUrl &url);

private:
  CefRefPtr<CefPageRenderer> m_renderer;
  CefRefPtr<BrowserListener> m_listener;
  CefRefPtr<CefBrowser> m_browser = nullptr;
  CefRefPtr<TextCallback> m_text_callback;
  IOBuffer m_iobuffer;
  QTimer *m_timer;

  int m_current_search_id = 0;
  QString last_find_request = "";

private:
    QUrl m_url;
    QString m_html = "";
    double m_zoom_factor = 1;
    bool m_fullscreen;
    bool m_running = true;
    bool m_cookies_enable = true;
    //property methods
public:
  QUrl url() const;

  void setUrl(const QUrl &url);

  bool running();

  void setRunning(bool running);

  double zoomFactor();

  void setZoomFactor(double zoomFactor);

  bool allowLinkTtransitions();

  void setAllowLinkTtransitions(bool);

  bool cookiesEnable();

  void setCookiesEnable(bool cookies);

  QSize pageSize();

signals:

  void urlChanged(const QUrl &url);

  void requestPage(const QUrl &url, bool newTab);

  void redirect(const QUrl &url);

  void titleChanged(const QString &title);

  void iconChanged(const QString &icon);

  void dragStarted();

  void cursorChanged(Qt::CursorShape cursorShape);

  void screenShotSaved();

  void loadEnd();

  void textRecieved(const QString &text);

  void downloadStarted(Download *item);

  void savedToStorage(const QString &text);

private slots:

  void resize();

  void initBrowser(QQuickWindow *window);

  void doCefWork();

  void updateVisible();

private:
  void destroyBrowser();

  friend class BrowserListener;
};

class TextCallback : public CefStringVisitor {
public:
  void setOwner(CefItem *item);

  virtual void Visit(const CefString &string) OVERRIDE;

private:
  CefItem *m_owner;
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
  BrowserListener(CefItem *owner) : m_owner(owner) {}

  virtual bool OnBeforeBrowse(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame,
                              CefRefPtr<CefRequest> request, bool is_redirect) OVERRIDE;

  virtual bool OnOpenURLFromTab(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame,
                                const CefString &target_url,
                                CefRequestHandler::WindowOpenDisposition target_disposition,
                                bool user_gesture) OVERRIDE;

  virtual bool OnBeforePopup(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame,
                             const CefString &target_url, const CefString &target_frame_name,
                             CefLifeSpanHandler::WindowOpenDisposition target_disposition, bool user_gesture,
                             const CefPopupFeatures &popupFeatures, CefWindowInfo &windowInfo,
                             CefRefPtr<CefClient> &client, CefBrowserSettings &settings,
                             bool *no_javascript_access) OVERRIDE;

  virtual void OnTitleChange(CefRefPtr<CefBrowser> browser, const CefString &title) OVERRIDE;

  virtual void OnFaviconURLChange(CefRefPtr<CefBrowser> browser, const std::vector<CefString> &icon_urls) OVERRIDE;

  virtual void OnLoadingStateChange(CefRefPtr<CefBrowser> browser, bool isLoading,
                                    bool canGoBack, bool canGoForward) OVERRIDE;

  virtual void OnBeforeDownload(CefRefPtr<CefBrowser> browser, CefRefPtr<CefDownloadItem> download_item,
                                const CefString &suggested_name,
                                CefRefPtr<CefBeforeDownloadCallback> callback) OVERRIDE;

  virtual void OnBeforeContextMenu(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame,
                                   CefRefPtr<CefContextMenuParams> params, CefRefPtr<CefMenuModel> model) OVERRIDE;

  virtual bool OnContextMenuCommand(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame,
                                    CefRefPtr<CefContextMenuParams> params, int command_id,
                                    EventFlags event_flags) OVERRIDE;

  virtual void OnBeforeClose(CefRefPtr<CefBrowser> browser) OVERRIDE;


  virtual void OnLoadStart(CefRefPtr<CefBrowser> browser,
                           CefRefPtr<CefFrame> frame,
                           TransitionType transition_type) OVERRIDE;
//    virtual bool OnDragEnter(CefRefPtr<CefBrowser> browser,
//                             CefRefPtr<CefDragData> dragData,
//                             DragOperationsMask mask)
//    {
//        qDebug() << "drag in cef" << QString::fromStdString(dragData->GetFileName().ToString())
//                 << QString::fromStdString(dragData->GetLinkURL().ToString()) <<
//                    dragData->IsFile();
//        return false;
//    }



  void userEventOccured(); //click or smth
  void redirectEnable(bool);

  void setEnable(bool);

IMPLEMENT_REFCOUNTING(CefRequestHandler)
private:
  bool m_first = true;
  qint64 m_last_event_time = 0;
  CefItem *m_owner;
  bool m_redirect_enable = true;
  bool m_enable;
  bool m_allow_link_trans = false;

  friend class CefItem;
};
}
#endif // CEFPAGE_H

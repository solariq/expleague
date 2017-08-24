
//#include "webscreenshotviewer.h"
//#include <QSGSimpleTextureNode>
//#include "cef.h"
//#include <QImage>

//namespace {
//class Browser;

//class Client: CefClient{
//public:
//  Client(CefRefPtr<Browser> browser): m_browser(browser){
//  }

//  CefRefPtr<CefRenderHandler> GetRenderHandler() override{
//    return m_browser;
//  }

//  CefRefPtr<CefLoadHandler> GetLoadHandler() override{
//    return m_browser;
//  }
//private:
//  CefRefPtr<Browser> m_browser;
//};

//class Browser: CefRenderHandler, CefLoadHandler{
//public:

//  bool GetViewRect(CefRefPtr<CefBrowser> browser, CefRect& rect) override{
//    rect =  CefRect(0, 0, m_width, m_height);
//  }

//  void OnPaint(CefRefPtr<CefBrowser> browser, PaintElementType type,
//                       const RectList& dirtyRects, const void* buffer, int width, int height) override{
//    if(m_loaded && m_screenshot_callback){
//      m_screenshot_callback(buffer, width, height);
//      browser->GetHost()->CloseBrowser(true);
//    }
//  }

//  void OnLoadingStateChange(CefRefPtr<CefBrowser> browser, bool isLoading, bool, bool) override {
//    if (!isLoading) {
//      if(m_url == "about:blank" && m_html != ""){
//        browser->GetMainFrame()->LoadString(m_html);
//      }else{
//        m_loaded = true;
//      }
//    }
//  }

//  void setUrl(const QString& url){
//    m_url = url == "" ? "about:blank" : url;
//  }

//  void setHtml(const QString &html){
//    m_html = html;
//  }

//  void setCallBack(std::function<void(const void* buffer, int width, int height)> callback){
//    m_screenshot_callback = callback;
//  }

//private:
//  int m_height;
//  int m_width;
//  QString m_url;
//  QString m_html;
//  bool m_loaded = false;
//  std::function<void(const void* buffer, int width, int height)> m_screenshot_callback;
//};

//}

//struct WebScreenshotViewerPrivate{
//  CefRefPtr<Browser> browser;
//  QSGTexture texture;

//  void makeScreenshot(int height, int width, std::function<void(const void* buffer, int width, int height)> screenshotCallback){
//    CefWindowInfo mainWindowInfo;
//#ifdef Q_OS_WIN
//    mainWindowInfo.SetAsWindowless(0, false);
//#elif defined(Q_OS_MAC)
//    mainWindowInfo.SetAsWindowless(0);
//#endif
//    CefBrowserSettings settings;
//    browser->setCallBack(screenshotCallback);
//    CefBrowserHost::CreateBrowser(mainWindowInfo, new Client(browser), m_url, settings, nullptr);
//  }
//};

//WebScreenshotViewer::WebScreenshotViewer(QQuickItem *parent): QQuickItem(parent){
//  d_ptr = new WebScreenshotViewerPrivate;
//}

//QSGNode *WebScreenshotViewer::updatePaintNode(QSGNode *oldNode, QQuickItem::UpdatePaintNodeData *){
//    QSGSimpleTextureNode *node = static_cast<QSGSimpleTextureNode *>(oldNode);
//    if (!node) {
//        node = new QSGSimpleTextureNode();
//    }
//    if(d_ptr->texture){
//      node->setTexture(d_ptr->texture);
//    }
//    node->setRect(boundingRect());
//    return node;
//}


//WebScreenshotViewer::~WebScreenshotViewer(){
//  d_ptr->browser->setCallBack(0);
//  if(d_ptr->texture) d_ptr->texture->deleteLater();
//  delete dptr;
//}


//QString WebScreenshotViewer::html(){
//  return m_html;
//}

//void WebScreenshotViewer::setHtml(const QString& html){
//  if(m_html != html){
//    m_html = html;
//    d_ptr->browser->setHtml(html);
//    d_ptr->makeScreenshot([this](){
//    }, width(), height());
//  }
//}

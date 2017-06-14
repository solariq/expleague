#ifndef COOKIEMANAGER_H
#define COOKIEMANAGER_H


#if _MSC_VER && !__INTEL_COMPILER
#pragma warning (push, 0)
#endif

#include "include/cef_app.h"
#include "include/cef_client.h"
#include "include/cef_render_process_handler.h"
#include "include/cef_browser.h"

#if _MSC_VER && !__INTEL_COMPILER
#pragma warning (pop)
#endif

class CookieManager: CefCookieManager
{
public:
    ///
    // Set the schemes supported by this manager. The default schemes ("http",
    // "https", "ws" and "wss") will always be supported. If |callback| is non-
    // NULL it will be executed asnychronously on the IO thread after the change
    // has been applied. Must be called before any cookies are accessed.
    ///
    /*--cef(optional_param=callback)--*/
    virtual void SetSupportedSchemes(
            const std::vector<CefString>& schemes,
            CefRefPtr<CefCompletionCallback> callback);

    ///
    // Visit all cookies on the IO thread. The returned cookies are ordered by
    // longest path, then by earliest creation date. Returns false if cookies
    // cannot be accessed.
    ///
    /*--cef()--*/
    virtual bool VisitAllCookies(CefRefPtr<CefCookieVisitor> visitor) =0;

    ///
    // Visit a subset of cookies on the IO thread. The results are filtered by the
    // given url scheme, host, domain and path. If |includeHttpOnly| is true
    // HTTP-only cookies will also be included in the results. The returned
    // cookies are ordered by longest path, then by earliest creation date.
    // Returns false if cookies cannot be accessed.
    ///
    /*--cef()--*/
    virtual bool VisitUrlCookies(const CefString& url,
                                 bool includeHttpOnly,
                                 CefRefPtr<CefCookieVisitor> visitor) =0;

    ///
    // Sets a cookie given a valid URL and explicit user-provided cookie
    // attributes. This function expects each attribute to be well-formed. It will
    // check for disallowed characters (e.g. the ';' character is disallowed
    // within the cookie value attribute) and fail without setting the cookie if
    // such characters are found. If |callback| is non-NULL it will be executed
    // asnychronously on the IO thread after the cookie has been set. Returns
    // false if an invalid URL is specified or if cookies cannot be accessed.
    ///
    /*--cef(optional_param=callback)--*/
    virtual bool SetCookie(const CefString& url,
                           const CefCookie& cookie,
                           CefRefPtr<CefSetCookieCallback> callback) =0;

    ///
    // Delete all cookies that match the specified parameters. If both |url| and
    // |cookie_name| values are specified all host and domain cookies matching
    // both will be deleted. If only |url| is specified all host cookies (but not
    // domain cookies) irrespective of path will be deleted. If |url| is empty all
    // cookies for all hosts and domains will be deleted. If |callback| is
    // non-NULL it will be executed asnychronously on the IO thread after the
    // cookies have been deleted. Returns false if a non-empty invalid URL is
    // specified or if cookies cannot be accessed. Cookies can alternately be
    // deleted using the Visit*Cookies() methods.
    ///
    /*--cef(optional_param=url,optional_param=cookie_name,
         optional_param=callback)--*/
    virtual bool DeleteCookies(const CefString& url,
                               const CefString& cookie_name,
                               CefRefPtr<CefDeleteCookiesCallback> callback) =0;

    ///
    // Sets the directory path that will be used for storing cookie data. If
    // |path| is empty data will be stored in memory only. Otherwise, data will be
    // stored at the specified |path|. To persist session cookies (cookies without
    // an expiry date or validity interval) set |persist_session_cookies| to true.
    // Session cookies are generally intended to be transient and most Web
    // browsers do not persist them. If |callback| is non-NULL it will be executed
    // asnychronously on the IO thread after the manager's storage has been
    // initialized. Returns false if cookies cannot be accessed.
    ///
    /*--cef(optional_param=path,optional_param=callback)--*/
    virtual bool SetStoragePath(const CefString& path,
                                bool persist_session_cookies,
                                CefRefPtr<CefCompletionCallback> callback) =0;

    ///
    // Flush the backing store (if any) to disk. If |callback| is non-NULL it will
    // be executed asnychronously on the IO thread after the flush is complete.
    // Returns false if cookies cannot be accessed.
    ///
    /*--cef(optional_param=callback)--*/
    virtual bool FlushStore(CefRefPtr<CefCompletionCallback> callback) =0;
};

#endif // COOKIEMANAGER_H

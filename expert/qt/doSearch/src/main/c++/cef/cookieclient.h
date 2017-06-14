#ifndef COOKIECLIENT_H
#define COOKIECLIENT_H

#include "include/cef_app.h"
#include "include/cef_client.h"
#include "include/cef_render_process_handler.h"
#include "include/cef_browser.h"

class CookieClient: CefCookieManager
{
public:
    CookieClient();
};

#endif // COOKIECLIENT_H

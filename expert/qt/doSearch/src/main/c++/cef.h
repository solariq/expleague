#ifndef CEF_H
#define CEF_H

#include <functional>

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

class QQuickItem;
namespace expleague {


class ShutDownGCItem {
public:
  virtual void shutDown() = 0;
protected:
  void addToShutDownGC();
  void removeFromShutDownGC();
};

void initCef(int i, char *pString[]);

void shutDownCef(std::function<void ()> callback);

}

#endif // CEF_H

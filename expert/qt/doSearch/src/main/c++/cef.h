#ifndef CEF_H
#define CEF_H

#include <functional>

namespace expleague {

class Browser{
public:
    virtual void shutDown() = 0;
protected:
    void addCefBrowserToGC();
    void removeCefBrowserFromGC();
};

void initCef();

void shutDownCef();

}

#endif // CEF_H

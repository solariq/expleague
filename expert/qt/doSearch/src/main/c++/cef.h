#ifndef CEF_H
#define CEF_H

namespace expleague {

class Browser{
public:
    virtual void shutDown() = 0;
protected:
    void addCefBrowserToGC();
    void removeCefBrowserFromGC();
    void shutDownCallBack(); //browser should call this after shutDown() call
};

void initCef();

void shutDownCef();


}

#endif // CEF_H

#include <iostream>
#if !defined (__unix__) && !(defined (__APPLE__) && defined (__MACH__))
#include <windows.h>
#endif
#include <fstream>
#include "include/cef_app.h"

void log(const std::string& mes){
    std::ofstream out;
    out.open ("Cef_exc.log", std::ofstream::app);
    out << mes << std::endl;
    out.close();
}


class MyApp: public CefApp {
  virtual void OnBeforeCommandLineProcessing(const CefString& process_type, CefRefPtr<CefCommandLine> command_line) OVERRIDE {
    command_line->AppendSwitch("--off-screen-rendering-enabled");
    command_line->AppendSwitch("--multi-threaded-message-loop");
    command_line->AppendSwitch("--enable-system-flash");
//    command_line->AppendSwitch("--enable-npapi");
    std::vector<CefString> argv;
    command_line.get()->GetArgv(argv);
  }

  virtual void OnRegisterCustomSchemes(CefRawPtr<CefSchemeRegistrar> registrar) OVERRIDE { //TODO try is_local true and is_cors_enabled fasle
#if defined(__unix__) || (defined (__APPLE__) && defined (__MACH__))
    registrar->AddCustomScheme("qrc", false, false, false, false, true, true);
#else
    registrar->AddCustomScheme("qrc", false, false, false, false, true);
#endif
  }

  virtual CefRefPtr<CefBrowserProcessHandler> GetBrowserProcessHandler() {
    return NULL;
  }
IMPLEMENT_REFCOUNTING(MyApp)
};


int main(int argc, char *argv[]) {
#if defined (__unix__) || (defined (__APPLE__) && defined (__MACH__))
  CefMainArgs main_args(argc, argv);
#else
  CefMainArgs main_args(GetModuleHandle(NULL));
#endif
  CefRefPtr<MyApp> app(new MyApp);
  return CefExecuteProcess(main_args, app, NULL);
}

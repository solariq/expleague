#include <iostream>
#if !defined (__unix__) && !(defined (__APPLE__) && defined (__MACH__))
#include <windows.h>
#endif
#include <fstream>
#include "include/cef_app.h"

void log(const std::string& mes){
    std::ofstream out;
    out.open ("log.log", std::ofstream::app);
    out << mes << std::endl;
    out.close();
}

class MyApp: public CefApp {
    virtual void OnBeforeCommandLineProcessing(const CefString& process_type, CefRefPtr<CefCommandLine> command_line) OVERRIDE {
        command_line.get()->AppendSwitch("--off-screen-rendering-enabled");
        command_line.get()->AppendSwitch("--multi-threaded-message-loop");
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

IMPLEMENT_REFCOUNTING(MyApp)
};

int main(int argc, char *argv[]) {
  #ifdef Q_OS_WINDOWS
  CefMainArgs main_args(GetModuleHandle(NULL));
  #else
  CefMainArgs main_args(argc, argv);
  #endif
  // Optional implementation of the CefApp interface.
  CefRefPtr<MyApp> app(new MyApp);
  // Execute the sub-process logic. This will block until the sub-process should exit.
  return CefExecuteProcess(main_args, app, NULL);
}

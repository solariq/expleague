#include <iostream>
#include <windows.h>
#include <iostream>
#include <fstream>
#include "include/cef_app.h"
#include "include/cef_render_process_handler.h"


class myApp: public CefApp{
    virtual void OnBeforeCommandLineProcessing(const CefString& process_type, CefRefPtr<CefCommandLine> command_line) {
        command_line.get()->AppendSwitch("--disable-gpu");
        std::vector<CefString> argv;
        command_line.get()->GetArgv(argv);
        std::ofstream log;
        log.open ("log.log");
        log << "Executing new process" << std::endl;;
        for(int i = 0; i < argv.size(); i++){
            log << argv[i].ToString() << std::endl;
        }
        log << "handle: " << GetModuleHandle(NULL);
        log.close();
    }
    IMPLEMENT_REFCOUNTING(myApp)
};

int main(int argc, char *argv[]) {
  CefMainArgs main_args(GetModuleHandle(NULL));
  // Optional implementation of the CefApp interface.
  CefRefPtr<myApp> app(new myApp);

  // Execute the sub-process logic. This will block until the sub-process should exit.
  return CefExecuteProcess(main_args, app.get(), NULL);
}

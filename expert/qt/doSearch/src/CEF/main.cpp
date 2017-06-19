#include <iostream>
#include <windows.h>
#include <iostream>
#include <fstream>
#include "include/cef_app.h"
#include "include/wrapper/cef_stream_resource_handler.h"
#include "include/cef_render_process_handler.h"
#include <QFile>
#include <QUrl>
#include <QFileInfo>
#include <QMimeDatabase>
#include <QDebug>
#include <QCoreApplication>

void log(const std::string& mes){
    std::ofstream out;
    out.open ("Cef_exc.log", std::ofstream::app);
    out << mes << std::endl;
    out.close();
}

class MyApp: public CefApp{
    virtual void OnBeforeCommandLineProcessing(const CefString& process_type, CefRefPtr<CefCommandLine> command_line) OVERRIDE{
        command_line.get()->AppendSwitch("--off-screen-rendering-enabled");
        command_line.get()->AppendSwitch("--multi-threaded-message-loop");
        //command_line.get()->AppendSwitch("--show-fps-counter");
        std::vector<CefString> argv;
        command_line.get()->GetArgv(argv);
        for(CefString str: argv){
            log(str.ToString());
        }
    }

    virtual void OnRegisterCustomSchemes(CefRawPtr<CefSchemeRegistrar> registrar) OVERRIDE { //TODO try is_local true and is_cors_enabled fasle

        registrar->AddCustomScheme("qrc", false /*is_standard*/, false /*is_local*/,  false /*is_display_isolated*/,
                                   false /*is_secure*/, true /*is_cors_enabled*/);
    }

    IMPLEMENT_REFCOUNTING(MyApp)
};

int main(int argc, char *argv[]) {
    log("execute cef proccess");
    CefMainArgs main_args(GetModuleHandle(NULL));
    // Optional implementation of the CefApp interface.
    CefRefPtr<MyApp> app(new MyApp);
    // Execute the sub-process logic. This will block until the sub-process should exit.
    return CefExecuteProcess(main_args, app, NULL);
}

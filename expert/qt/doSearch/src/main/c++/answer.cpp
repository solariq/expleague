#include "model/editor.h"

extern "C" {
#include "markdown.h"
}

namespace expleague {
QString MarkdownEditorScreen::html() {
    QByteArray utf8 = m_text.toUtf8();
    Document* mdDoc = mkd_string(utf8.constData(), utf8.length(), 0);
    mkd_compile(mdDoc, 0);
    char* result;
    int length = mkd_document(mdDoc, &result);
    QString html = QString::fromUtf8(result, length);
    mkd_cleanup(mdDoc);
    return html;
}

}

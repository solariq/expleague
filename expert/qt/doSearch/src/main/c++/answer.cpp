#include <QApplication>
#include <QClipboard>
#include <QMimeData>
#include <QFile>
#include <QTextStream>
#include <QTextDocument>
#include <QImageReader>

#include <QThread>
#include <QTimer>

#include "model/folder.h"
#include "model/editor.h"
#include "task.h"

#include "markdownhighlighter.h"
#include "styleparser.h"
#include "protocol.h"
#include "dosearch.h"

extern "C" {
#include "markdown.h"
}

namespace expleague {

QString buildHtmlByMD(const QString& text) {
    const QRegExp CUT_RE = QRegExp("\\+\\[([^\\]]+)\\]([^-]*(?:-[^\\[][^-]*)*)-\\[\\1\\]");
    QString html;
    int prevPos = 0;
    int nextPos;
    int index = 0;
    while ((nextPos = CUT_RE.indexIn(text, prevPos)) != -1) {
//        qDebug() << "Match: " << nextPos << " from " << prevPos << " to " << nextPos + CUT_RE.matchedLength() << " length " << CUT_RE.matchedLength();
        index++;
        QString id = "cut-" + QString::number(index);
        QString id_1 = "cuta-" + QString::number(index);
        html += text.mid(prevPos, nextPos - prevPos);
//        qDebug() << "Intermezzo: " << m_text.mid(prevPos, nextPos);
//        qDebug() << "Fragment:" << m_text.mid(nextPos, nextPos - prevPos);

        html += "<a class=\"cut_open\" id=\"" + id_1 + "\" href=\"javascript:showHide('" + id + "','" + id_1 + "')\">" + CUT_RE.cap(1) + "</a>" +
                "<div class=\"cut_open\" id=\"" + id +"\">" + CUT_RE.cap(2) +
                "\n<a class=\"hide\" href=\"#" + id_1 + "\" onclick=\"showHide('" + id + "','" + id_1 + "')\">" + QObject::tr("скрыть") + "</a></div>";
        prevPos = nextPos + CUT_RE.matchedLength();
    }
    html += text.mid(prevPos);
    QByteArray utf8 = html.toUtf8();
    Document* mdDoc = mkd_string(utf8.constData(), utf8.length(), 0);
    mkd_compile(mdDoc, 0);
    char* result;
    int length = mkd_document(mdDoc, &result);
    QString resultHtml = QString("<!DOCTYPE html>\n")
            + "<html>\n"
            + "<head>\n"
            + "<script src=\"qrc:/md-scripts.js\"></script>\n"
            + "<link rel=\"stylesheet\" href=\"qrc:/markdownpad-github.css\">\n"
            + "</head>\n"
            + "<body>\n"
            + QString::fromUtf8(result, length)
            + "</body>\n</html>\n";

    mkd_cleanup(mdDoc);
    return resultHtml;
}

MarkdownEditorScreen::MarkdownEditorScreen(QObject* parent, bool editable): Screen(QUrl("qrc:/EditorView.qml"), parent) {
    m_editor = itemById<QQuickItem>("editor");
    m_document = m_editor->property("textDocument").value<QQuickTextDocument*>();
    League* league = doSearch::instance()->league();
    m_author = league->findMember(league->id());
    m_highlighter = new MarkdownHighlighter(m_document->textDocument(), &m_spellchecker);
    m_highlighter->setParent(this);
    QObject::connect(m_document->textDocument(), SIGNAL(contentsChanged()), this, SLOT(contentChanged()));

    QFile f(":/themes/solarized-light+.txt");
    if (!f.open(QIODevice::ReadOnly | QIODevice::Text)) {
        return;
    }

    QTextStream ts(&f);
    QString input = ts.readAll();

    // parse the stylesheet
    PegMarkdownHighlight::StyleParser parser(input);
    QVector<PegMarkdownHighlight::HighlightingStyle> styles = parser.highlightingStyles(m_document->textDocument()->defaultFont());
    m_highlighter->setStyles(styles);
    QObject::connect(m_author, SIGNAL(nameChanged(QString)), this, SLOT(authorChanged()));
    QObject::connect(m_author, SIGNAL(avatarChanged(QUrl)), this, SLOT(authorChanged()));
    setupOwner();
    if (editable) {
        QThread* thread = new QThread(this);
        QTimer* timer = new QTimer();
        timer->moveToThread(thread);
        timer->setInterval(1000);
        connect(timer, &QTimer::timeout, [this](){
            if (this->m_html.isEmpty()) {
                m_html = buildHtmlByMD(this->m_text);
                this->htmlChanged(m_html);
            }
        });
        connect(thread, SIGNAL(started()), timer, SLOT(start()));
        connect(thread, &QObject::destroyed, timer, &QTimer::deleteLater);
        thread->start();
        m_html_thread = thread;
    }
}

MarkdownEditorScreen::MarkdownEditorScreen(ReceivedAnswer* answer, QObject* parent): MarkdownEditorScreen(parent, false) {
    QObject::disconnect(m_author, SIGNAL(nameChanged(QString)), this, SLOT(authorChanged()));
    QObject::disconnect(m_author, SIGNAL(avatarChanged(QUrl)), this, SLOT(authorChanged()));
    m_text = answer->text();
    m_html = buildHtmlByMD(m_text);
    m_author = answer->author();
    m_editor->setProperty("enabled", false);
    m_document->textDocument()->setPlainText(m_text);
    QObject::connect(answer->author(), SIGNAL(nameChanged(QString)), this, SLOT(authorChanged()));
    QObject::connect(answer->author(), SIGNAL(avatarChanged(QUrl)), this, SLOT(authorChanged()));
    QObject::connect(answer, SIGNAL(requestFocus()), SLOT(acquireFocus()));
}

QString MarkdownEditorScreen::name() const {
    return tr("Ответ ") + m_author->name();
}

QUrl MarkdownEditorScreen::icon() const {
    return m_author->avatar();
}

void MarkdownEditorScreen::setText(const QString& text) {
    m_text = text;
    m_html = "";
    textChanged(text);
}

QString MarkdownEditorScreen::html() {
    if (m_html.isEmpty() && !m_text.isEmpty()) {
        m_html = buildHtmlByMD(m_text);
    }
    return m_html;
}

void MarkdownEditorScreen::acquireFocus() {
    setActive(true);
    qobject_cast<Folder*>(parent())->setActive(true);
    m_editor->forceActiveFocus();
}

QStringList MarkdownEditorScreen::codeClipboard() {
    const QClipboard* clipboard = QApplication::clipboard();
    QStringList result;
    const QMimeData* mime = clipboard->mimeData();
    foreach (const QUrl& url, mime->urls()) {
        if (url.scheme() == "file") {
            QImage img(url.toLocalFile());
            if (!img.isNull()) {
                QUrl url = League::instance()->uploadImage(img);
                result += QString("!["+ (mime->hasText() ? mime->text() : "") + "](" + url.toString() + ")\n");
            }
        }
        else if (url.scheme() == "http" || url.scheme() == "https" || url.scheme() == "ftp") {
            if (QImageReader::imageFormat(url.fileName()).isEmpty())
                result += QString("["+ (mime->hasText() ? mime->text() : "") + "](" + url.toString() + ")\n");
            else
                result += QString("!["+ (mime->hasText() ? mime->text() : "") + "](" + url.toString() + ")\n");
        }
        else {
            result += QString("["+ (mime->hasText() ? mime->text() : "") + "](" + url.toString() + ")\n");
        }
    }
    if (result.empty()) {
        if (mime->hasImage()) {
            QUrl url = League::instance()->uploadImage(qvariant_cast<QImage>(mime->imageData()));
            result += QString("!["+ (mime->hasText() ? mime->text() : "") + "](" + url.toString() + ")\n");
        }
        else if (mime->hasText()) {
            result += mime->text();
        }
    }
    return result;
}
}

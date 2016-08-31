#include "editor.h"
#include "../../task.h"
#include "../../protocol.h"
#include "../../dosearch.h"

#include "markdownhighlighter.h"
#include "styleparser.h"
#include "spellchecker.h"

#include <QApplication>

#include <QQuickItem>
#include <QQuickTextDocument>

#include <QClipboard>
#include <QMimeData>
#include <QFile>
#include <QTextStream>
#include <QTextDocument>
#include <QImageReader>

#include <QThread>
#include <QTimer>

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

struct MarkdownEditorPagePrivate {
    QQuickItem* editor;
    QQuickTextDocument* document;
    std::unique_ptr<MarkdownHighlighter> highlighter;
    QThread* html_thread;

    virtual ~MarkdownEditorPagePrivate() {
        if (html_thread) {
            html_thread->exit();
            html_thread->deleteLater();
        }
    }
};

void MarkdownEditorPage::initUI(QQuickItem* result) const {
    d_ptr.reset(new MarkdownEditorPagePrivate());
    connect(result, SIGNAL(destroyed(QObject*)), this, SLOT(onUiDestryed(QObject*)));
    d_ptr->editor = result->property("editor").value<QQuickItem*>();
    d_ptr->document = d_ptr->editor->property("textDocument").value<QQuickTextDocument*>();
    d_ptr->highlighter.reset(new MarkdownHighlighter(d_ptr->document->textDocument(), const_cast<hunspell::SpellChecker*>(m_spellchecker)));
    d_ptr->highlighter->setParent(const_cast<MarkdownEditorPage*>(this));
    QObject::connect(d_ptr->document->textDocument(), SIGNAL(contentsChanged()), this, SLOT(contentChanged()));

    QFile f(":/themes/solarized-light+.txt");
    if (!f.open(QIODevice::ReadOnly | QIODevice::Text))
        return;

    QTextStream ts(&f);
    QString input = ts.readAll();

    // parse the stylesheet
    PegMarkdownHighlight::StyleParser parser(input);
    QVector<PegMarkdownHighlight::HighlightingStyle> styles = parser.highlightingStyles(d_ptr->document->textDocument()->defaultFont());
    d_ptr->highlighter->setStyles(styles);

    if (m_editable) {
        QThread* thread = new QThread(0);
        QTimer* timer = new QTimer();
        timer->moveToThread(thread);
        timer->setInterval(1000);
        connect(timer, &QTimer::timeout, [this](){
            if (m_html.isEmpty()) {
                m_html = buildHtmlByMD(this->m_text);
                htmlChanged(m_html);
            }
        });
        connect(thread, SIGNAL(started()), timer, SLOT(start()));
        connect(thread, &QObject::destroyed, timer, &QTimer::deleteLater);
        thread->start();
        d_ptr->html_thread = thread;
    }
    else {
        d_ptr->html_thread = 0;
        d_ptr->editor->setProperty("readOnly", true);
    }
    d_ptr->document->textDocument()->setPlainText(m_text);
}

void MarkdownEditorPage::resetText(const QString& text) {
    MarkdownEditorPagePrivate* privatePart = d_ptr.get();
    if (privatePart)
        privatePart->document->textDocument()->setPlainText(text);
}

void MarkdownEditorPage::contentChanged() {
    MarkdownEditorPagePrivate* privatePart = d_ptr.get();
    if (privatePart && privatePart->document) {
        QString text = privatePart->document->textDocument()->toPlainText();
        setText(text);
    }
}

MarkdownEditorPage::MarkdownEditorPage(const QString& id, Member* author, const QString& title, doSearch* parent): Page(id, "qrc:/EditorView.qml", parent), m_author(author) {
    d_ptr.reset(0);
    m_text = value("document.text").toString();
    store("document.author", author ? author->id() : QVariant());
    store("document.title", title);
    save();
    League* league = doSearch::instance()->league();
    m_editable = !author || author->id() == league->id();
    if (m_author) {
        QObject::connect(m_author, SIGNAL(nameChanged(QString)), this, SLOT(authorChanged()));
        QObject::connect(m_author, SIGNAL(avatarChanged(QUrl)), this, SLOT(authorChanged()));
    }
}

MarkdownEditorPage::MarkdownEditorPage(const QString& id, doSearch* parent): Page(id, "qrc:/EditorView.qml", parent) {
    m_author = parent->league()->findMember(value("document.author").toString());
    m_text = value("document.text").toString();
    m_html = buildHtmlByMD(m_text);
    League* league = doSearch::instance()->league();
    m_editable = !m_author || m_author->id() == league->id();
    if (m_author) {
        QObject::connect(m_author, SIGNAL(nameChanged(QString)), this, SLOT(authorChanged()));
        QObject::connect(m_author, SIGNAL(avatarChanged(QUrl)), this, SLOT(authorChanged()));
    }
}

MarkdownEditorPage::~MarkdownEditorPage() {}

QString MarkdownEditorPage::title() const {
    return value("document.title").toString();
}

QString MarkdownEditorPage::icon() const {
    return m_author ? m_author->avatar().toString() : "qrc:/avatar.png";
}

void MarkdownEditorPage::setText(const QString& text) {
    m_text = text;
    m_html = "";
    store("document.text", m_text);
    save();
    textChanged(text);
}

QString MarkdownEditorPage::html() {
    if (m_html.isEmpty() && !m_text.isEmpty()) {
        m_html = buildHtmlByMD(m_text);
    }
    return m_html;
}

void MarkdownEditorPage::acquireFocus() {
    parent()->navigation()->open(this);
}

void MarkdownEditorPage::onUiDestryed(QObject*) {
    d_ptr.reset(0);
}

QStringList MarkdownEditorPage::codeClipboard() {
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

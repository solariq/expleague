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

void addSchemesToMarkDownUrls(QString &text){
  QString result;
  const QRegExp EXP(R"(\[([^\]]+)\]\(([^\/)]*\.[^)]*)\))");
  int prevPos = 0;
  int nextPos = 0;
  while((nextPos = EXP.indexIn(text, prevPos)) != -1){
    qDebug() << "modify url" << EXP.cap(2);;
    result += text.mid(prevPos, nextPos - prevPos) + "[" + EXP.cap(1) + "]" + "(http://" + EXP.cap(2) + ")";
    prevPos = nextPos + EXP.matchedLength();
  }
  if(prevPos != 0){
    text = result + text.mid(prevPos);
  }
}

QString buildHtmlByMD(QString text) {
  addSchemesToMarkDownUrls(text);
  const QRegExp CUT_RE("\\+\\[([^\\]]+)\\]([^-]*(?:-[^\\[][^-]*)*)-\\[\\1\\]");
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
  addSchemesToMarkDownUrls(html);
  QByteArray utf8 = html.toUtf8();
  Document* mdDoc = mkd_string(utf8.constData(), utf8.length(), 0);
  mkd_compile(mdDoc, 0);
  char* result;
  int length = mkd_document(mdDoc, &result);
  QString resultHtml = QString::fromUtf8(result, length);
  mkd_cleanup(mdDoc);
  return resultHtml;
}

struct MarkdownEditorPagePrivate {
    QQuickItem* ui;
    QQuickItem* editor;
    QQuickTextDocument* document;
    QThread* html_thread;

    virtual ~MarkdownEditorPagePrivate() {
        if (html_thread) {
            html_thread->exit();
            html_thread->deleteLater();
        }
    }
};

void doSearch::setupHighlighter(QQuickTextDocument* document) const {
    static QVector<PegMarkdownHighlight::HighlightingStyle> styles;
    if (styles.empty()) {
        QFile f(":/themes/solarized-light+.txt");
        if (f.open(QIODevice::ReadOnly | QIODevice::Text)) {
            QTextStream ts(&f);
            QString input = ts.readAll();
            PegMarkdownHighlight::StyleParser parser(input);
            styles = parser.highlightingStyles(document->textDocument()->defaultFont());
        }
    }
    static hunspell::SpellChecker spellchecker;
    MarkdownHighlighter* highlighter = new MarkdownHighlighter(document->textDocument(), &spellchecker);
    highlighter->setStyles(styles);
}

void MarkdownEditorPage::initUI(QQuickItem* result) {
    d_ptr.reset(new MarkdownEditorPagePrivate());
    connect(result, SIGNAL(destroyed(QObject*)), this, SLOT(onUiDestryed(QObject*)));
    d_ptr->ui = result;
    d_ptr->editor = result->property("editor").value<QQuickItem*>();
    d_ptr->document = d_ptr->editor->property("textDocument").value<QQuickTextDocument*>();
    QObject::connect(d_ptr->document->textDocument(), SIGNAL(contentsChanged()), this, SLOT(contentChanged()));
    doSearch::instance()->setupHighlighter(d_ptr->document);

    if (m_editable) {
        QThread* thread = new QThread(parent());
        QTimer* timer = new QTimer();
        timer->moveToThread(thread);
        timer->setInterval(1000);
        connect(timer, &QTimer::timeout, [this](){
            if (m_html.isNull()) {
                m_html = buildHtmlByMD(this->m_text);
                emit htmlChanged(m_html);
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
        m_text = text;
        m_html = QString();
        ContentPage::setTextContent(text);
    }
}

void MarkdownEditorPage::onAuthorChanged() {
    auto prev = m_author;
    m_author = parent()->league()->findMember(m_author_id);
    if (m_author != prev && m_author) {
        QObject::connect(m_author, SIGNAL(nameChanged(QString)), this, SLOT(onAuthorChanged()));
        QObject::connect(m_author, SIGNAL(avatarChanged(QUrl)), this, SLOT(onAuthorChanged()));
        QObject::connect(m_author, SIGNAL(destroyed(QObject*)), this, SLOT(onAuthorChanged()));
    }

    emit Page::titleChanged(title());
    emit Page::iconChanged(icon());
}

MarkdownEditorPage::MarkdownEditorPage(const QString& id, Member* author, const QString& title, bool editable, doSearch* parent): ContentPage(id, "qrc:/EditorView.qml", parent), m_author(author), /*m_owner(context), */m_editable(editable) {
    d_ptr.reset(0);
    m_text = ContentPage::textContent();
    m_author_id = author ? author->id() : "";
    store("document.author", author ? author->id() : QVariant());
    store("document.title", title);
    store("document.editable", editable);
    m_editable = editable;
    save();
    if (m_author) {
        QObject::connect(m_author, SIGNAL(nameChanged(QString)), this, SLOT(onAuthorChanged()));
        QObject::connect(m_author, SIGNAL(avatarChanged(QUrl)), this, SLOT(onAuthorChanged()));
    }
}

MarkdownEditorPage::MarkdownEditorPage(const QString& id, doSearch* parent): ContentPage(id, "qrc:/EditorView.qml", parent) {
    QVariant author = value("document.author");
    m_author_id = author.toString();
    m_text = ContentPage::textContent();
    m_html = buildHtmlByMD(m_text);
    QVariant editableVar = value("document.editable");
    m_editable = editableVar.isNull() ? true : editableVar.toBool();
    if (!m_author_id.isEmpty()) {
        QObject::connect(parent->league(), SIGNAL(connectionChanged()), this, SLOT(onAuthorChanged()));
        onAuthorChanged();
    }
}

void MarkdownEditorPage::interconnect() {
    ContentPage::interconnect();
    QVariant ownerVar = value("document.owner");
}

MarkdownEditorPage::~MarkdownEditorPage() {}

QString MarkdownEditorPage::title() const {
    return value("document.title").toString();
}

QString MarkdownEditorPage::icon() const {
    return m_author ? m_author->avatar().toString() : "qrc:/avatar.png";
}

void MarkdownEditorPage::setTextContent(const QString& text) {
    if (m_text == text)
        return;
    m_text = text;
    m_html = QString();
    if (!!d_ptr)
        d_ptr->document->textDocument()->setPlainText(m_text);
    ContentPage::setTextContent(text);
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
    if (!!d_ptr && sender() == d_ptr->ui)
        d_ptr.reset(0);
}

void MarkdownEditorPage::setEditable(bool editable) {
    store("document.editable", editable);
    m_editable = editable;
    if (d_ptr && !editable)
        d_ptr->editor->setProperty("readOnly", true);
    save();
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

#ifndef EDITOR_H
#define EDITOR_H

#include <QFile>
#include <QTextStream>
#include <QTextDocument>
#include <QQuickTextDocument>

#include "spellchecker.h"
using hunspell::SpellChecker;

#include "markdownhighlighter.h"
#include "styleparser.h"

#include "screen.h"

namespace expleague {
class MarkdownEditorScreen: public Screen {
    Q_OBJECT

    Q_PROPERTY(QString text READ text WRITE setText NOTIFY textChanged)
    Q_PROPERTY(QString html READ html NOTIFY htmlChanged)

public:
    QString name() const {
        return tr("Ответ");
    }

    QUrl icon() const {
        return QUrl("qrc:/md.png");
    }

    QString location() const {
        return "";
    }

    bool handleOmniboxInput(const QString&) {
        return false;
    }

    QString text() {
        return m_text;
    }

    QString html();

public:
    void remove() {}

    void setText(const QString& text) {
        m_text = text;
        textChanged(text);
        htmlChanged(html());
    }

signals:
    void textChanged(const QString&);
    void htmlChanged(const QString&);

private slots:
    void contentChanged() {
        QString text = m_document->textDocument()->toPlainText();
        setText(text);
    }

public:
    MarkdownEditorScreen(QObject* parent = 0): Screen(QUrl("qrc:/EditorView.qml"), parent){
        m_editor = findChild<QQuickItem*>("editor");
        m_document = m_editor->property("textDocument").value<QQuickTextDocument*>();
        m_highlighter = new MarkdownHighlighter(m_document->textDocument(), &m_spellchecker);
        m_highlighter->setParent(this);
        QObject::connect(m_document->textDocument(), SIGNAL(contentsChanged()), this, SLOT(contentChanged()));

        QFile f(":/themes/default.txt");
        if (!f.open(QIODevice::ReadOnly | QIODevice::Text)) {
            return;
        }

        QTextStream ts(&f);
        QString input = ts.readAll();

        // parse the stylesheet
        PegMarkdownHighlight::StyleParser parser(input);
        QVector<PegMarkdownHighlight::HighlightingStyle> styles = parser.highlightingStyles(m_document->textDocument()->defaultFont());
        m_highlighter->setStyles(styles);
    }

private:
    QQuickItem* m_editor;
    QQuickTextDocument* m_document;
    MarkdownHighlighter* m_highlighter;
    QString m_text;
    hunspell::SpellChecker m_spellchecker;
};
}

QML_DECLARE_TYPE(expleague::MarkdownEditorScreen)

#endif // EDITOR_H

#ifndef EDITOR_H
#define EDITOR_H

#include <QQuickTextDocument>

#include "spellchecker.h"
using hunspell::SpellChecker;

#include "screen.h"

class MarkdownHighlighter;
namespace expleague {
class ReceivedAnswer;
class Member;

class MarkdownEditorScreen: public Screen {
    Q_OBJECT

    Q_PROPERTY(QString text READ text WRITE setText NOTIFY textChanged)
    Q_PROPERTY(QString html READ html NOTIFY htmlChanged)

public:
    QString name() const;
    QUrl icon() const;

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

    Q_INVOKABLE QStringList codeClipboard();

public slots:
    void resetText(const QString& text) {
        m_document->textDocument()->setPlainText(text);
    }

signals:
    void textChanged(const QString&);
    void htmlChanged(const QString&);

private slots:
    void contentChanged() {
        QString text = m_document->textDocument()->toPlainText();
        setText(text);
    }

    void authorChanged() {
        emit Screen::nameChanged(name());
        emit Screen::iconChanged(icon());
    }

    void acquireFocus();

public:
    MarkdownEditorScreen(QObject* parent = 0);
    MarkdownEditorScreen(ReceivedAnswer* answer, QObject* parent = 0);

private:
    QQuickItem* m_editor;
    QQuickTextDocument* m_document;
    MarkdownHighlighter* m_highlighter;
    QString m_text;
    Member* m_author;
    hunspell::SpellChecker m_spellchecker;
};
}

QML_DECLARE_TYPE(expleague::MarkdownEditorScreen)

#endif // EDITOR_H

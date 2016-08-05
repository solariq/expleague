#ifndef EDITOR_H
#define EDITOR_H

#include "page.h"
namespace hunspell {
class SpellChecker;
}

class MarkdownHighlighter;
class QThread;
namespace expleague {
class ReceivedAnswer;
class Member;
struct MarkdownEditorPagePrivate;

class MarkdownEditorPage: public Page {
    Q_OBJECT

    Q_PROPERTY(QString text READ text WRITE setText NOTIFY textChanged)
    Q_PROPERTY(QString html READ html NOTIFY htmlChanged)

public:
    QString title() const;
    QString icon() const;

    QString text() const {
        return m_text;
    }

    QString html();

public:
    void setText(const QString& text);
    void setEditable(bool editable);
    Q_INVOKABLE QStringList codeClipboard();

public slots:
    void resetText(const QString& text);

    void onUiDestryed(QObject*);

signals:
    void textChanged(const QString&);
    void htmlChanged(const QString&) const;

private slots:
    void contentChanged();

    void authorChanged() {
        emit Page::titleChanged(title());
        emit Page::iconChanged(icon());
    }

    void acquireFocus();

public:
    MarkdownEditorPage(const QString& id, Member* author, const QString& title, doSearch* parent);
    MarkdownEditorPage(const QString& id = "", doSearch* parent = 0);
    virtual ~MarkdownEditorPage();

protected:
    void initUI(QQuickItem *ui) const;

private:
    QString m_text;
    Member* m_author;
    hunspell::SpellChecker* m_spellchecker;
    bool m_editable;

    mutable QString m_html;
    mutable std::unique_ptr<MarkdownEditorPagePrivate> d_ptr;
};
}

#endif // EDITOR_H

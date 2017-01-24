#ifndef EDITOR_H
#define EDITOR_H

#include "../page.h"

#include <memory>

namespace hunspell {
class SpellChecker;
}

class MarkdownHighlighter;
class QThread;
namespace expleague {
class ReceivedAnswer;
class Member;
class Context;
struct MarkdownEditorPagePrivate;

class MarkdownEditorPage: public ContentPage {
    Q_OBJECT

    Q_PROPERTY(QString html READ html NOTIFY htmlChanged)

public:
    QString title() const;
    QString icon() const;

    QString textContent() const { return m_text; }

    QString html();

public:
    void setTextContent(const QString& text);
    Q_INVOKABLE QStringList codeClipboard();

public slots:
    void resetText(const QString& text);

    void onUiDestryed(QObject*);

signals:
    void htmlChanged(const QString&) const;

private slots:
    void contentChanged();

    void authorChanged() {
        emit Page::titleChanged(title());
        emit Page::iconChanged(icon());
    }

    void acquireFocus();

public:
    explicit MarkdownEditorPage(const QString& id, Member* author, const QString& title, bool editable, doSearch* parent);
    explicit MarkdownEditorPage(const QString& id = "", doSearch* parent = 0);
    virtual ~MarkdownEditorPage();

protected:
    void initUI(QQuickItem *ui) const;
    void interconnect();

private:
    QString m_text;
    Member* m_author = 0;
//    Context* m_owner = 0;
    hunspell::SpellChecker* m_spellchecker = 0;
    bool m_editable;

    mutable QString m_html;
    mutable std::unique_ptr<MarkdownEditorPagePrivate> d_ptr;
};
}

#endif // EDITOR_H

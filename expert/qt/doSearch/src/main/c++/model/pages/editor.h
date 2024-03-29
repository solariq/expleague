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
    Q_PROPERTY(QString text READ textContent NOTIFY htmlChanged)

public:
    QString title() const override;
    QString icon() const override;

    QString textContent() const override { return m_text; }

    QString html();

public:
    void setTextContent(const QString& text) override;
    void setEditable(bool editable);
    Q_INVOKABLE QStringList codeClipboard();

public slots:
    void resetText(const QString& text);

    void onUiDestryed(QObject*);

signals:
    void htmlChanged(const QString&) const;

private slots:
    void contentChanged();

    void onAuthorChanged();

    void acquireFocus();

public:
    explicit MarkdownEditorPage(const QString& id, Member* author, const QString& title, bool editable, doSearch* parent);
    explicit MarkdownEditorPage(const QString& id = "", doSearch* parent = 0);
    virtual ~MarkdownEditorPage();

protected:
    void initUI(QQuickItem *ui) override;
    void interconnect() override;

private:
    QString m_text;
    QString m_author_id;
    Member* m_author = 0;
//    Context* m_owner = 0;
    bool m_editable;

    mutable QString m_html;
    mutable std::unique_ptr<MarkdownEditorPagePrivate> d_ptr;
};
}

#endif // EDITOR_H

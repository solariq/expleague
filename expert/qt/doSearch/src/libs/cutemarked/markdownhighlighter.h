/*
 * Copyright 2013 Christian Loose <christian.loose@hamburg.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
#ifndef MARKDOWNHIGHLIGHTER_H
#define MARKDOWNHIGHLIGHTER_H

#include <QSyntaxHighlighter>

extern "C" {
#include "pmh_definitions.h"
}
#include "definitions.h"

namespace hunspell {
class SpellChecker;
}

class MarkdownHighlighter : public QSyntaxHighlighter
{
    Q_OBJECT

public:
    MarkdownHighlighter(QTextDocument *document, hunspell::SpellChecker *spellChecker);
    
    void setStyles(const QVector<PegMarkdownHighlight::HighlightingStyle> &styles);
    void setSpellingCheckEnabled(bool enabled);
    void setYamlHeaderSupportEnabled(bool enabled);

protected:
    void highlightBlock(const QString &textBlock) Q_DECL_OVERRIDE;

private:
    void checkSpelling(const QString &textBlock);

    QVector<PegMarkdownHighlight::HighlightingStyle> highlightingStyles;
    QTextCharFormat spellFormat;
    hunspell::SpellChecker *spellChecker;
    bool spellingCheckEnabled;
    bool yamlHeaderSupportEnabled;
};

#endif // MARKDOWNHIGHLIGHTER_H

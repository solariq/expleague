/*
 * Copyright 2013 Christian Loose <christian.loose@hamburg.de>
 * Copyright 2015 Aetf <7437103@gmail.com>
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
#include "markdownhighlighter.h"

#include <QDebug>
#include <QFile>
#include <QTextDocument>
#include <QTextLayout>

#include "pmh_parser.h"

#include "definitions.h"
using PegMarkdownHighlight::HighlightingStyle;

#include "spellchecker.h"
using hunspell::SpellChecker;

#include <QDebug>

MarkdownHighlighter::MarkdownHighlighter(QTextDocument *document, hunspell::SpellChecker *spellChecker) :
    QSyntaxHighlighter(document),
    spellingCheckEnabled(true),
    yamlHeaderSupportEnabled(false)
{
    this->spellChecker = spellChecker;

    // QTextCharFormat::SpellCheckUnderline has issues with Qt 5.
    spellFormat.setUnderlineStyle(QTextCharFormat::SingleUnderline);
    spellFormat.setUnderlineColor(QColor(Qt::red));
}

void MarkdownHighlighter::setStyles(const QVector<PegMarkdownHighlight::HighlightingStyle> &styles)
{
    highlightingStyles = styles;
}

void MarkdownHighlighter::setSpellingCheckEnabled(bool enabled)
{
    spellingCheckEnabled = enabled;
}

void MarkdownHighlighter::setYamlHeaderSupportEnabled(bool enabled)
{
    yamlHeaderSupportEnabled = enabled;
}

void MarkdownHighlighter::highlightBlock(const QString& textBlock)
{
    // cut YAML headers
    pmh_element **elements;
    pmh_markdown_to_elements(textBlock.toUtf8().data(), pmh_EXT_NONE, &elements);

    for (int i = 0; i < highlightingStyles.size(); i++) {
        HighlightingStyle style = highlightingStyles.at(i);
        pmh_element *elem_cursor = elements[style.type];
        while (elem_cursor != NULL) {
            unsigned long pos = elem_cursor->pos;
            unsigned long end = elem_cursor->end;

            QTextCharFormat format = style.format;
            if (/*_makeLinksClickable
                &&*/ (elem_cursor->type == pmh_LINK
                    || elem_cursor->type == pmh_AUTO_LINK_URL
                    || elem_cursor->type == pmh_AUTO_LINK_EMAIL
                    || elem_cursor->type == pmh_REFERENCE)
                && elem_cursor->address != NULL)
            {
                QString address(elem_cursor->address);
                if (elem_cursor->type == pmh_AUTO_LINK_EMAIL && !address.startsWith("mailto:"))
                    address = "mailto:" + address;
                format.setAnchor(true);
                format.setAnchorHref(address);
                format.setToolTip(address);
            }
            setFormat(pos, end - pos, format);
            elem_cursor = elem_cursor->next;
        }
    }

    pmh_free_elements(elements);
    if (spellingCheckEnabled) {
        checkSpelling(textBlock);
    }
}

void MarkdownHighlighter::checkSpelling(const QString &textBlock) {
    QStringList wordList = textBlock.split(QRegExp("\\W+"), QString::SkipEmptyParts);
    int index = 0;
    foreach (QString word, wordList) {
        index = textBlock.indexOf(word, index);

        if (word == "TODO") {
            QTextCharFormat format = this->format(index);
            format.setForeground(QBrush(QColor(0xCC, 0xCC, 0)));
            format.setFontWeight(600);
            setFormat(index, word.length(), format);
        }
        else if (!spellChecker->isCorrect(word)) {
            QTextCharFormat format = this->format(index);
            format.setUnderlineStyle(QTextCharFormat::SingleUnderline);
            format.setForeground(QBrush(QColor(0xFF, 0xA0, 0x7A)));
            setFormat(index, word.length(), format);
        }

        index += word.length();
    }
}

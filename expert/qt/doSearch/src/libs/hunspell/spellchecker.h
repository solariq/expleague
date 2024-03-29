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
#ifndef HUNSPELL_SPELLCHECKER_H
#define HUNSPELL_SPELLCHECKER_H

#include <QtCore/qmap.h>
#include <QtCore/qstring.h>

#include <QList>

class Hunspell;
class QTextCodec;
class QFile;

namespace hunspell {

class SpellChecker
{
public:
    SpellChecker();
    ~SpellChecker();

    bool isCorrect(const QString &word);
    QStringList suggestions(const QString &word);
    void addToUserWordlist(const QString &word);

    void loadDictionary(const QString &dictFilePath);
    void loadUserWordlist(const QString &userWordlistPath);

private:
    Hunspell *hunspellChecker;
    QString userWordlist;
    QTextCodec *textCodec;
    QList<QFile*> tempFiles;
};

} // namespace Hunspell

#endif // HUNSPELL_SPELLCHECKER_H

#include "dictionary.h"

#include <memory>
#include <limits>
#include <QRegularExpression>
#include <QDir>

#include <leveldb/write_batch.h>
#include <leveldb/comparator.h>

#include "bow.h"

namespace expleague {

QVector<int> parse(const QString& plainText, CollectionDictionary* dict) {
    QList<int> result;
    bool emptyDocument = true;
    foreach(QString paragraph, plainText.split(QRegularExpression("\\n\\s*\\n+"))) {
        static QRegularExpression invalidBrackets("[\\[\\]\\{\\}\\|\\^\u00A0]");
        paragraph.replace(invalidBrackets, " ");
        static QRegularExpression normalizeSpaces("\\s+");
        paragraph.replace(normalizeSpaces, " ");
        static QRegularExpression sentenceRE("[\\.!?\\*](?:(?:\\s+\\p{Lu})|$)");
        int sentenceOffset = 0;

        QRegularExpressionMatch sentenceMatch;
        bool emptyParagraph = true;
        do {
            sentenceMatch = sentenceRE.match(paragraph, sentenceOffset);
            QString sentence = sentenceMatch.hasMatch() ? paragraph.mid(sentenceOffset, sentenceMatch.capturedStart()) : paragraph.mid(sentenceOffset);
            // TODO group numbers: 600 000 -> 600000
            static QRegularExpression spaces1("[\"'<>/\\\\]");
            sentence.replace(spaces1, " ");
            static QRegularExpression groupingBrackets("(?:\\s+\\()|(?:\\)\\s+)");
            sentence.replace(groupingBrackets, " "); // grouping bracket
            sentence.replace(normalizeSpaces, " ");
            bool emptySentence = true;
            static QRegularExpression wordsRE("[\\s:;]|(?: -+ )|(?:, )");
            foreach(QString word, sentence.split(wordsRE)) {
                static QRegularExpression remove1("[^\\p{L}-&\\d\\+\\(\\)@\\.]");
                word.replace(remove1, "");
                static QRegularExpression remove2("(?:[^\\p{L}\\d]$)|(?:^[^\\p{L}\\d])");
                word.replace(remove2, "");
                word = word.toLower();
                if (word.length() < 2 || word.length() > 20)
                    continue;
                int index = dict->id(word);
                if (index == CollectionDictionary::UnknownWord)
                    index = dict->append(word);
                else if (dict->word(index) != word)
                    qDebug() << "Something has gone wrong with the dictionary: looking for " << word << " but found: " << dict->word(index);
                result.append(index);
                emptySentence = false;
            }
            if (!emptySentence) {
                result.append(CollectionDictionary::SentenceBreak);
                emptyParagraph = false;
            }

            sentenceOffset = sentenceMatch.capturedStart() + 1;
        }
        while(sentenceMatch.hasMatch());
        if (!emptyParagraph) {
            result.append(CollectionDictionary::ParagraphBreak);
            emptyDocument = false;
        }
    }
    if (!emptyDocument)
        result.append(CollectionDictionary::DocumentBreak);
    return result.toVector();
}

QString Word::toString() const {
    return text + " " + QString::number(freq) + " " + QString::number(dfreq);
}

Word Word::fromString(const QString& serialized) {
    QStringList parts = serialized.split(" ");
    return Word(parts[0], parts[1].toInt(), parts[2].toInt());
}

const Word CollectionDictionary::EMPTY_WORD = Word("", 0, 0);

int CollectionDictionary::append(const QString& word) {
    m_lock.lockForWrite();
    QString lemma = m_lemmer(word);
    int lemmaId = this->id(lemma);
    if (lemmaId == SpecialWords::UnknownWord) {
        append(lemma, m_lemma_offset.size() << 8, 0);
    }
    int id;
    if (word != lemma)
        id = append(word, lemmaId, countForms(lemmaId));
    else
        id = lemmaId;
    m_lock.unlock();
    return id;
}

int CollectionDictionary::append(const QString& text, int lemmaId, int formIndex) {
    Word word(text);
    int id = lemmaId + formIndex;
    if (formIndex) {
        const int offset = this->offset(id);
        m_words.insert(m_words.begin() + offset, word);
        for (int i = (lemmaId >> 8) + 1; i < m_lemma_offset.size(); i++) {
            m_lemma_offset[i]++;
        }
    }
    else {
        m_lemma_offset.append(m_words.size());
        m_words.append(word);
    }

    leveldb::WriteBatch batch;
    batch.Put(leveldb::Slice(reinterpret_cast<char*>(&id), sizeof(id)), word.toString().toStdString());
    m_file->Write(leveldb::WriteOptions(), &batch);
    m_index[text] = id;
    return id;
}

int CollectionDictionary::id(const QString& word) const {
    auto index = m_index.find(word);
    if (index != m_index.end())
        return index.value();

    else if (word == "[sentences]")
        return SpecialWords::SentenceBreak;
    else if (word == "[paragraphs]")
        return SpecialWords::ParagraphBreak;
    else if (word == "[documents]")
        return SpecialWords::DocumentBreak;
    else
        return SpecialWords::UnknownWord;
}

int CollectionDictionary::countForms(int id) const {
    int lemmaIndex = id >> 8;
    if (lemmaIndex < m_lemma_offset.size() - 1)
        return m_lemma_offset[lemmaIndex + 1] - m_lemma_offset[lemmaIndex];
    else
        return m_words.size() - m_lemma_offset[lemmaIndex];
}

Word CollectionDictionary::updateWord(int id, float freq, int dfreq) {
    Word result = EMPTY_WORD;
    if (id >= 0) {
        const int index = offset(id);
        if (index >= 0 && index < m_words.size()) {
            Word& word = m_words[index];
            word.dfreq += dfreq;
            if (word.freq + freq < 0)
                qWarning() << "Negative frequency found!";
            word.freq += freq;
            m_total_power += freq;
            result = word;
        }
        else
            qWarning() << "Invalid word id: " << id << " no such word in dictionary";
    }
    else switch (id) {
    case SpecialWords::SentenceBreak:
        result.freq = (m_sentences_count += freq);
        break;
    case SpecialWords::ParagraphBreak:
        result.freq = (m_paragraphs_count += freq);
        break;
    case SpecialWords::DocumentBreak:
        result.freq = (m_documents_count += freq);
        break;
    default:
        break;
    }
    return result;
}

void CollectionDictionary::registerProfile(const BoW& profile) {
    m_lock.lockForWrite();
    leveldb::WriteBatch batch;
    profile.visit([this, &batch](int id, float freq, double) {
        Word result = updateWord(id, freq, 1);
        batch.Put(leveldb::Slice(reinterpret_cast<const char*>(&id), sizeof(id)), result.toString().toStdString());
    });
    m_lock.unlock();
    m_file->Write(leveldb::WriteOptions(), &batch);
}

void CollectionDictionary::updateProfile(const BoW& oldOne, const BoW& newOne) {
    m_lock.lockForWrite();
    leveldb::WriteBatch batch;
    int oldIndex = 0;
    int newIndex = 0;
    QVector<int> oindices = oldOne.m_indices;
    QVector<int> nindices = newOne.m_indices;
    while (oldIndex < oindices.size() || newIndex < nindices.size()) {
        Word update;
        int id;
        int oldId = oindices.size() > oldIndex ? oindices.at(oldIndex) : std::numeric_limits<int>::max();
        int newId = nindices.size() > newIndex ? nindices.at(newIndex) : std::numeric_limits<int>::max();
        if (oldId == newId) {
            id = oldId;
            update = updateWord(id, newOne.m_freqs[newIndex++] - oldOne.m_freqs[oldIndex++], 0);
        }
        else if (oldId > newId) {
            id = newId;
            update = updateWord(id, newOne.m_freqs[newIndex++], 1);
        }
        else {
            id = oldId;
            update = updateWord(id, -oldOne.m_freqs[oldIndex++], -1);
        }
        assert(update.freq >= 0);
        assert(update.dfreq >= 0);
        batch.Put(leveldb::Slice(reinterpret_cast<const char*>(&id), sizeof(id)), update.toString().toStdString());
    }
    m_lock.unlock();
    m_file->Write(leveldb::WriteOptions(), &batch);
}

using namespace leveldb;

class CollectionDictionaryComparator: public leveldb::Comparator {
public:
    int Compare(const Slice& a, const Slice& b) const {
        int idA = *reinterpret_cast<const int*>(a.data());
        int idB = *reinterpret_cast<const int*>(b.data());
        return idA - idB;
    }

    const char* Name() const {
        return "dictionary-comparator-v.1";
    }

    void FindShortestSeparator(std::string* /*start*/, const Slice& /*limit*/) const {
    }
    void FindShortSuccessor(std::string* /*key*/) const {
    }
};

CollectionDictionary::CollectionDictionary(const QString& file, std::function<QString (const QString& word)> lemmer, QObject* parent): QObject(parent), m_lemmer(lemmer) {
    leveldb::Options options;
    options.create_if_missing = true;
    options.comparator = new CollectionDictionaryComparator();
    QDir dbDir(file);
    if (!dbDir.exists()) {
        dbDir.cdUp();
        dbDir.mkpath(file.section('/', -1));
    }
    leveldb::Status status = leveldb::DB::Open(options, file.toStdString(), &m_file);
    assert(status.ok());
    std::unique_ptr<leveldb::Iterator> iter(m_file->NewIterator(leveldb::ReadOptions()));
    QList<Word> words;
    iter->SeekToFirst();
    while (iter->Valid()) {
        leveldb::Slice key = iter->key();
        leveldb::Slice value = iter->value();
        int id = *reinterpret_cast<const int*>(key.data());
        QString serialized = QString::fromUtf8(value.data(), (int)value.size());
        Word word = Word::fromString(serialized);
        if (id >= 0) {
            if ((id & 0xFF) == 0) // lemma
                m_lemma_offset.append(words.size());
            words.append(word);
            m_index[word.text] = id;
            m_total_power += word.freq;
            if (offset(id) != words.size() - 1)
                qDebug() << "Dictionary seems to be corrupted: id: " << id << " offset: " << offset(id) << " current offset: " << words.size() <<"!";
        }
        else switch (id) {
        case SpecialWords::SentenceBreak:
            m_sentences_count = word.freq;
            break;
        case SpecialWords::ParagraphBreak:
            m_paragraphs_count = word.freq;
            break;
        case SpecialWords::DocumentBreak:
            m_documents_count = word.freq;
            break;
        case SpecialWords::UnknownWord:
            break;
        }
        iter->Next();
    }
    m_words = words.toVector();
}
}

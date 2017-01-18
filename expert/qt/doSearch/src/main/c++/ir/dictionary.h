#include <cmath>
#include <functional>

#include <QObject>

#include <QString>
#include <QVector>
#include <QHash>
#include <QMap>
#include <QReadWriteLock>
#include <QFile>

#include <QDebug>

#include <leveldb/db.h>

namespace expleague {

class CollectionDictionary;

QVector<int> parse(const QString& plainText, CollectionDictionary* terms);

struct Word {
    QString text;
    int dfreq = 0;
    int freq = 0;

    QString toString() const;
    static Word fromString(const QString& serialized);
    explicit Word(const QString& word = ""): text(word) {}
    explicit Word(const QString& word, int totalFreq, int documentFreq): text(word), dfreq(documentFreq), freq(totalFreq) {}
};

class BoW;

class CollectionDictionary: public QObject {
    Q_OBJECT

public:
    static const Word EMPTY_WORD;

    enum SpecialWords: int {
        UnknownWord = -1,
        SentenceBreak = -2,
        ParagraphBreak = -3,
        DocumentBreak = -4
    };

public:
    int documentsCount() const { return m_documents_count; }
    int paragraphsCount() const { return m_paragraphs_count; }
    int sentencesCount() const { return m_sentences_count; }

    int power() const { return m_total_power; }

    int append(const QString& word);
    int id(const QString& word) const;

    void registerProfile(const BoW& profile);
    void updateProfile(const BoW& oldOne, const BoW& newOne);

    QString word(int id) const { return processWord<QString>(id, [](const Word& w){ return w.text; }); }
    double idf(int id) const { return processWord<double>(id, [](const Word& w){ return log(2.)/log(2. + (double)w.dfreq); }); }

public:
    explicit CollectionDictionary(const QString& file, std::function<QString (const QString& word)> lemmer, QObject* parent = 0);
    virtual ~CollectionDictionary() { delete m_file; }

private:
    template<typename T>
    T processWord(int id, std::function<T (const Word&)> proc) const;

    int append(const QString &word, int lemmaIndex, int formIndex);
    int countForms(int id) const;
    Word updateWord(int id, float freq, int dfreq);

    inline int offset(int id) const { return m_lemma_offset[id >> 8] + (id & 0xFF); }

private:
    int m_documents_count = 0;
    int m_paragraphs_count = 0;
    int m_sentences_count = 0;

    int m_total_power = 0;

    QMap<QString, int> m_index;

    QVector<Word> m_words;
    std::function<QString (const QString& word)> m_lemmer;

    QVector<int> m_lemma_offset;
    mutable QReadWriteLock m_lock;

    leveldb::DB* m_file = 0;
};

template<typename T>
T CollectionDictionary::processWord(int id, std::function<T (const Word&)> proc) const {
    Word w = EMPTY_WORD;
    if (id >= 0) {
        m_lock.lockForRead();
        const int index = offset(id);
        if (index < 0 || index > m_words.size())
            qWarning() << "Invalid word id: " << id << " no such word in dictionary";
        else
            w = m_words.at(index);
        m_lock.unlock();
    }
    else switch(id) {
    case SpecialWords::SentenceBreak:
        w.text = "[sentences]";
        w.freq = m_sentences_count;
        break;
    case SpecialWords::ParagraphBreak:
        w.text = "[paragraphs]";
        w.freq = m_paragraphs_count;
        break;
    case SpecialWords::DocumentBreak:
        w.text = "[documents]";
        w.freq = m_documents_count;
        break;
    default:
        w.text = "[unknown]";
        w.freq = 0;
        break;
    }

    T result = proc(w);
    return result;
}
}

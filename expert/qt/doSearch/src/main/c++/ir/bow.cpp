#include "bow.h"

#include <algorithm>
#include <math.h>

#include <qalgorithms.h>

#include "dictionary.h"

namespace expleague {

int BoW::indexOf(int id) const {
    QVector<int>::const_iterator current;
    if (m_indices.size() > 32)
        current = std::lower_bound(m_indices.begin(), m_indices.end(), id);
    else
        current = std::find(m_indices.begin(), m_indices.end(), id);
    while (current != m_indices.end() && *current <= id) {
        if (*current == id)
            return current - m_indices.begin();
        current++;
    }
    return -1;
}

void BoW::visit(std::function<void (int, float, double)> visitor) const {
    for (int i = 0; i < m_indices.size(); i++) {
        visitor(m_indices[i], m_freqs[i], m_tf[i]);
    }
}

QString BoW::toString() const {
    QString result;
    for (int i = 0; i < m_indices.size(); i++) {
        result += m_terms->word(m_indices.at(i));
        result += "\t";
        result += QString::number(m_freqs.at(i));
        result += "\n";
    }
    return result;
}

BoW BoW::fromString(const QString& serialized, CollectionDictionary* terms) {
    QVector<std::pair<int, float>> pairs;
    foreach(QString line, serialized.split('\n')) {
        if (line.isEmpty())
            continue;
        QStringList parts = line.split('\t');
        int id = terms->id(parts[0]);
        if (id == CollectionDictionary::UnknownWord && parts[0] != "[unknown]") {
            if (parts[0].isEmpty())
                continue;
            qDebug() << "Invalid profile entry: " << line;
            id = terms->append(parts[0]);
        }
        if (parts.size() > 1)
            pairs.append(std::pair<int, float>(id, parts[1].toFloat()));
        else
            qDebug() << "Profile corrupted: " << line;
    }
    std::sort(pairs.begin(), pairs.end(), [](const std::pair<int, float>& left, const std::pair<int, float>& right){
       return left.first < right.first;
    });
    QVector<int> indices(pairs.size());
    QVector<float> freq(pairs.size());
    QString result;
    for (int i = 0; i < pairs.size(); i++) {
        indices[i] = pairs[i].first;
        freq[i] = pairs[i].second;
    }
    return BoW(indices, freq, terms);
}

BoW BoW::fromPlainText(const QString& text, CollectionDictionary* terms) {
    QVector<int> parsed = parse(text, terms);
    QMap<int, int> profile;
    for (int i = 0; i < parsed.size(); i++) {
        profile[parsed[i]]++;
    }
    auto iter = profile.begin();
    QVector<int> indices(profile.size());
    QVector<float> freqs(profile.size());
    int index = 0;
    while (iter != profile.end()) {
        indices[index] = iter.key();
        freqs[index] = iter.value();
        iter++;
        index++;
    }
    return BoW(indices, freqs, terms);
}

BoW BoW::binarize() const {
    QVector<float> freqs(size());
    for (int i = 0; i < size(); i++) {
        freqs[i] = 1;
    }
    return BoW(m_indices, freqs, m_terms);
}

BoW::BoW(QVector<int> indices, QVector<float> freqs, CollectionDictionary* terms): m_terms(terms), m_indices(indices), m_freqs(freqs), m_tf(freqs.size()) {
    float length = 0;
    for (int i = 0; i < freqs.size(); i++) {
        length += freqs[i];
    }

    m_length = length;

    double module = 0;
    for (int i = 0; i < freqs.size(); i++) {
        m_tf[i] = tf(freqs[i], length, (1.0 + terms->power())/ (1.0 + terms->documentsCount())) * terms->idf(indices[i]);
        module += m_tf[i] * m_tf[i];
    }
    m_module = sqrt(module);
}

double operator *(const BoW& left, const BoW& right) {
    if (left.module() == 0.0 || right.module() == 0.0)
        return 0.0;
    int leftIndex = 0;
    int rightIndex = 0;
    double result = 0;
    QVector<int> lindices = left.m_indices;
    QVector<int> rindices = right.m_indices;
    while (leftIndex < lindices.size() && rightIndex < rindices.size()) {
        if (lindices.at(leftIndex) == rindices.at(rightIndex)) {
            if (lindices.at(leftIndex) >= 0)
                result += left.m_tf.at(leftIndex) * right.m_tf.at(rightIndex);
            rightIndex++;
            leftIndex++;
        }
        else if (lindices.at(leftIndex) > rindices.at(rightIndex))
            rightIndex++;
        else
            leftIndex++;
    }
    return result;
}

BoW operator +(const BoW& left, const BoW& right) {
    if (left.length() == 0.0)
        return right;
    if (right.length() == 0.0)
        return left;
    int leftIndex = 0;
    int rightIndex = 0;
    QVector<int> resultIndices;
    QVector<float> resultFreqs;
    QVector<int> lindices = left.m_indices;
    QVector<int> rindices = right.m_indices;
    while (leftIndex < lindices.size() || rightIndex < rindices.size()) {
        int leftId = lindices.size() > leftIndex ? lindices.at(leftIndex) : std::numeric_limits<int>::max();
        int rightId = rindices.size() > rightIndex ? rindices.at(rightIndex) : std::numeric_limits<int>::max();

        if (leftId == rightId) {
            resultIndices.append(leftId);
            resultFreqs.append(left.m_freqs.at(leftIndex) + right.m_freqs.at(rightIndex));
            rightIndex++;
            leftIndex++;
        }
        else if (leftId > rightId) {
            resultIndices.append(rightId);
            resultFreqs.append(right.m_freqs.at(rightIndex));
            rightIndex++;
        }
        else {
            resultIndices.append(leftId);
            resultFreqs.append(left.m_freqs.at(leftIndex));
            leftIndex++;
        }
    }
    resultIndices.squeeze();
    resultFreqs.squeeze();
    return BoW(resultIndices, resultFreqs, right.m_terms ? right.m_terms : left.m_terms);
}

BoW updateSumComponent(const BoW& sum, const BoW& left, const BoW& right) {
    if (left.length() == 0.0)
        return sum + right;
    int leftIndex = 0;
    int rightIndex = 0;
    int sumIndex = 0;
    QVector<int> resultIndices;
    QVector<float> resultFreqs;

    QVector<int> sindices = sum.m_indices;
    QVector<int> lindices = left.m_indices;
    QVector<int> rindices = right.m_indices;

    while (leftIndex < lindices.size() || rightIndex < rindices.size() || sumIndex < sindices.size()) {
        int leftId = lindices.size() > leftIndex ? lindices.at(leftIndex) : std::numeric_limits<int>::max();
        int rightId = rindices.size() > rightIndex ? rindices.at(rightIndex) : std::numeric_limits<int>::max();
        int sumId = sindices.size() > sumIndex ? sindices.at(sumIndex) : std::numeric_limits<int>::max();

        int minId = std::min(leftId, std::min(rightId, sumId));
        float resultFreq = 0;
        if (leftId == minId)
            resultFreq -= left.m_freqs.at(leftIndex++);
        if (rightId == minId)
            resultFreq += right.m_freqs.at(rightIndex++);
        if (sumId == minId)
            resultFreq += sum.m_freqs.at(sumIndex++);

        if (resultFreq > 1e-10) {
            resultIndices.append(minId);
            resultFreqs.append(resultFreq);
        }
    }
    resultIndices.squeeze();
    resultFreqs.squeeze();
    return BoW(resultIndices, resultFreqs, right.m_terms ? right.m_terms : (left.m_terms ? left.m_terms : sum.m_terms));
}

double cos(const BoW& left, const BoW& right) {
    if (left.module() == 0.0 || right.module() == 0.0)
        return 0.0;
    return left * right / left.module() / right.module();
}

}

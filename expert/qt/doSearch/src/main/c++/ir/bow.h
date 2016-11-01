#ifndef BOWPROFILE_H
#define BOWPROFILE_H

#include <functional>

#include <QString>
#include <QVector>

namespace expleague {

class CollectionDictionary;

const double K = 2.0;
const double B = 0.75;

inline double tf(float freq, int dlength, double avgDocLength) {
    return freq * (K + 1.0)/(freq + K * (1.0 - B + B * dlength / avgDocLength));
}

class BoW {
public:
    void visit(std::function<void (int id, float freq, double tf)> visitor) const;

    double operator[] (int id) const {int idx = indexOf(id); return idx < 0 ? 0.0 : m_tf.at(idx); }
    double module() const { return m_module; }

    float freq(int id) const { int idx = indexOf(id); return idx < 0 ? 0.0f : m_freqs.at(idx); }
    float length() const { return m_length; }

    int indexOf(int id) const;
    int idAt(int index) const { return m_indices.at(index); }
    float freqAt(int index) const { return m_freqs.at(index); }
    double tfAt(int index) const { return m_tf.at(index); }
    int size() const { return m_indices.size(); }

    CollectionDictionary* terms() const { return m_terms; }

    QString toString() const;

    BoW binarize() const;

    static BoW fromString(const QString& serialized, CollectionDictionary* terms);
    static BoW fromPlainText(const QString& text, CollectionDictionary* terms);

    friend double operator *(const BoW& left, const BoW& right);
    friend double cos(const BoW& left, const BoW& right);

    friend BoW operator +(const BoW& left, const BoW& right);
    friend BoW updateSumComponent(const BoW& sum, const BoW& oldComponent, const BoW& newComponent);

    friend class CollectionDictionary;

public:
    BoW(): m_terms(0), m_length(0), m_module(0) {}
    BoW(QVector<int> indices, QVector<float> freqs, CollectionDictionary* terms);

private:
    CollectionDictionary* m_terms;

    QVector<int> m_indices;

    QVector<float> m_freqs;
    float m_length;

    QVector<double> m_tf;
    double m_module;
};
}

#endif // BOWPROFILE_H

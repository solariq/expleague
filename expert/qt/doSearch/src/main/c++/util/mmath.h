#ifndef MATH_H
#define MATH_H

#include <vector>
#include <algorithm>

#include <QString>
#include <QCryptographicHash>

inline QString randString(int length) {
    QString result;
    for (int i = 0; i < length; i++) {
        result += 'a' + ((double)rand()) / RAND_MAX * ('z' - 'a');
    }
    return result;
}

inline QString md5(const QString& str) {
    QCryptographicHash md5(QCryptographicHash::Md5);
    md5.addData(str.toUtf8());
    return QString::fromLatin1(md5.result().toBase64().constData());
}

template <typename T>
typename T::size_type levenshtein_distance(const T& src, const T& dst) {
  const typename T::size_type m = src.size();
  const typename T::size_type n = dst.size();
  if (m == 0) {
    return n;
  }
  if (n == 0) {
    return m;
  }

  std::vector< std::vector<typename T::size_type> > matrix(m + 1);

  for (typename T::size_type i = 0; i <= m; ++i) {
    matrix[i].resize(n + 1);
    matrix[i][0] = i;
  }
  for (typename T::size_type i = 0; i <= n; ++i) {
    matrix[0][i] = i;
  }

  typename T::size_type above_cell, left_cell, diagonal_cell, cost;

  for (typename T::size_type i = 1; i <= m; ++i) {
    for(typename T::size_type j = 1; j <= n; ++j) {
      cost = src[i - 1] == dst[j - 1] ? 0 : 1;
      above_cell = matrix[i - 1][j];
      left_cell = matrix[i][j - 1];
      diagonal_cell = matrix[i - 1][j - 1];
      matrix[i][j] = std::min(std::min(above_cell + 1, left_cell + 1), diagonal_cell + cost);
    }
  }

  return matrix[m][n];
}

double bisection(double left, double right, std::function<double (double)> func);
double optimalExpansionDP(double statPower, int classes);
double erlang(int k, double lambda, double x);

#endif // MATH_H

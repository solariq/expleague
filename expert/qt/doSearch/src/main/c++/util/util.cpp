#include "mmath.h"

#include <math.h>
#include <QFile>
#include <QFileInfo>
#include <QDir>
#include <QDebug>


const double EPSILON = 1e-6;

double bisection(double left, double right, std::function<double(double)> func) {
  const double fLeft = func(left);
  if (fabs(fLeft) < EPSILON)
    return left;
  const double fRight = func(right);
  if (fabs(fRight) < EPSILON)
    return right;

  if (fLeft * fRight > 0) {
    qWarning() << "Function values for left and right parameters should lay on different sides of 0";
    return nan("");
  }

  const double middle = (left + right) / 2.;
  const double fMiddle = func(middle);
  if (fLeft * fMiddle > 0)
    return bisection(middle, right, func);
  else
    return bisection(left, middle, func);
}

double optimalExpansionDP(double statPower, int classes) {
  if (statPower <= classes)
    return std::numeric_limits<double>::infinity();
  return bisection(0, (classes + statPower) * 10, [statPower, classes](double x) {
    return x == 0.0 ? -classes : x * log(1 + statPower / x) - classes;
  });
}

double erlang(int k, double lambda, double x) {
  const double nom = exp(log(lambda) * k + log(x) * (k - 1) - lambda * x - lgamma(k));
  return nom;
}

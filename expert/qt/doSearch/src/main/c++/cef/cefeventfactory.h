#ifndef CEFEVENTFACTORY_H
#define CEFEVENTFACTORY_H

#include <QMouseEvent>
#include "../cef .h"


class CefEventFactory{
public:
    static CefKeyEvent createPressEvent(QKeyEvent *ev);
    static CefKeyEvent createReleaseEvent(QKeyEvent *ev);
    static CefKeyEvent createCharEvent(QKeyEvent *ev);
    static CefMouseEvent createMouseEvent(double x, double y);
    static uint32 keyEventFlags(QKeyEvent *ev);
    static uint32 mouseEventFlags(int mouseButtons);
};

#endif // CEFEVENTFACTORY_H

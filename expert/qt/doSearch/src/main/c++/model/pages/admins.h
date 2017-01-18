#ifndef ADMINS_H
#define ADMINS_H

#include "../context.h"

namespace expleague {
class GlobalChat;

class AdminContext: public Context {
    Q_OBJECT

public:
    static QString ID;

public:
    explicit AdminContext(doSearch* parent);

    QString title() const;
    QString icon() const;

protected:
    void interconnect();

private:
    GlobalChat* m_chat = 0;
};
}

#endif

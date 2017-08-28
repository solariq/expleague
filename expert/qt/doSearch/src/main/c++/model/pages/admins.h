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

    QString title() const override;
    QString icon() const override;

protected:
    void interconnect() override;

private:
    GlobalChat* m_chat = 0;
};
}

#endif

#include "admins.h"

#include "globalchat.h"
#include "../group.h"
#include "../../dosearch.h"

namespace expleague {

QString AdminContext::ID = "league/admins-context";

QString AdminContext::title() const {
    return tr("Админский контекст");
}

QString AdminContext::icon() const {
    return "qrc:/icons/owl.png";
}

AdminContext::AdminContext(doSearch* parent):
    Context(AdminContext::ID, parent)
{
}

void AdminContext::interconnect() {
    Context::interconnect();
    PagesGroup* rootGroup = associated(this);

    m_chat = static_cast<GlobalChat*>(parent()->page(GlobalChat::ID));
    rootGroup->insert(m_chat, 0);
    rootGroup->selectPage(m_chat);
}

}

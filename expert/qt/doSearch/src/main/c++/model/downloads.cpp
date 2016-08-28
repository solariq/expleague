#include "downloads.h"

#include "../dosearch.h"

namespace expleague {
DownloadsPage::DownloadsPage(Context* context, doSearch* parent): Page(context->id() + "/downloads", "qrc:/DownloadsPage.qml", "", parent) {
}

DownloadsPage::DownloadsPage(const QString& id, doSearch* parent): Page(id, "qrc:/DownloadsPage.qml", "", parent) {
}
}

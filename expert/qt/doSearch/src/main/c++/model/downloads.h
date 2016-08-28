#ifndef DOWNLOADS_H
#define DOWNLOADS_H

#include "page.h"

namespace expleague {
class doSearch;
class Context;

class DownloadsPage: public Page {
    Q_OBJECT

public:
    DownloadsPage(Context* context, doSearch* parent);
    DownloadsPage(const QString& id = "download", doSearch* parent = 0);
};
}
#endif // DOWNLOADS_H

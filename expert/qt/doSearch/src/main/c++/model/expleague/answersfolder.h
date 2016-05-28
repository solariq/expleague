#ifndef ANSWERSFOLDER_H
#define ANSWERSFOLDER_H

#include <QQmlListProperty>

#include "../folder.h"
#include "../editor.h"

namespace expleague {
class Task;
class ReceivedAnswer;

class AnswersFolder: public Folder {
    Q_OBJECT

public:
    virtual QUrl icon() const {
        return QUrl("qrc:/md.png");
    }

    QString caption() const {
        return tr("Результаты");
    }

    bool handleOmniboxInput(const QString &, bool) {
        return false;
    }

private slots:
    void answerReceived(ReceivedAnswer* answer);

public:
    AnswersFolder(Task* task = 0, QObject* parent = 0);
};
}

#endif // ELFOLDER_H

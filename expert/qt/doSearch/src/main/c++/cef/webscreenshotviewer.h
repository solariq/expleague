#ifndef SCREENSHOTVIEWER_H
#define SCREENSHOTVIEWER_H

#include <QtCore>
#include <QQuickItem>

struct WebScreenshotViewerPrivate;

class WebScreenshotViewer: QQuickItem{
  Q_PROPERTY(QString html READ html WRITE setHtml)
public:
    WebScreenshotViewer(QQuickItem *parent = nullptr);
    QSGNode* updatePaintNode(QSGNode *oldNode, QQuickItem::UpdatePaintNodeData *) override;

public:
    QString html();
    void setHtml(const QString& html);
private:
    ~WebScreenshotViewer();
    WebScreenshotViewerPrivate* d_ptr;
    QString m_html;
};

#endif

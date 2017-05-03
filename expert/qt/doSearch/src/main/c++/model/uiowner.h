#ifndef UIOWNER_H
#define UIOWNER_H

#include <QQuickItem>

class UIOwner: public QObject{
    Q_OBJECT
    Q_PROPERTY(QQuickItem* ui READ ui NOTIFY uiChanged)
public:
    UIOwner(const QString& uiQml, QObject* parent);
    QQuickItem* ui(bool cache = true);
    QList<QQuickItem*> children();
    QQuickItem* parent();

    void clear();
    bool compareUI(QQuickItem* item) { return m_ui == item; }
signals:
    void uiChanged();
public slots:
    void updateConnections();
protected:
    virtual void initUI(QQuickItem*){}
    virtual bool transferUI(UIOwner* other);
    void setContext(QQmlContext* context);
private:
    QList<QQuickItem*> m_children;
    QQuickItem* m_parent = 0;
    QQuickItem* m_ui = 0;
    QString m_ui_url;
    QQmlContext* m_context = 0;
    QString m_property_filter;
};

#endif // UIOWNER_H

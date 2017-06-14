#ifndef UIOWNER_H
#define UIOWNER_H

#include <QQuickItem>

class UIOwner: public QObject{
    Q_OBJECT
    Q_PROPERTY(QQuickItem* ui READ ui NOTIFY uiChanged)
    Q_PROPERTY(QQuickItem* uiNoCache READ uiNoCache CONSTANT)
public:
    UIOwner(const QString& uiQml, QObject* parent);
    QQuickItem* ui(bool cache = true);
    QQuickItem* uiNoCache();
    Q_INVOKABLE QList<UIOwner*> children();
    Q_INVOKABLE UIOwner* parent();

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
    QList<UIOwner*> m_children;
    UIOwner* m_parent = 0;
    QQuickItem* m_ui = 0;
    QString m_ui_url;
    QQmlContext* m_context = 0;
    QString m_property_filter;
};

#endif // UIOWNER_H

#include "uiowner.h"
#include "../dosearch.h"

#include <QQmlContext>
#include <QQmlApplicationEngine>

QHash<QUrl, QQmlComponent*> componentsCache;

void collectChildren(QList<QQuickItem*>& children, QQuickItem* item);
QQuickItem* findParent(QQuickItem* item);

UIOwner::UIOwner(const QString& uiQml, QObject* parent):
    m_ui_url(uiQml), QObject(parent){
    //QObject::connect(this, SIGNAL(uiChanged()), this, SLOT(updateConnections()));
}

void UIOwner::clear(){
    m_ui = nullptr;
    for(auto child: m_children){
        if(child){
            child->property("owner").value<UIOwner*>()->clear();
        }
    }
    emit uiChanged();
}

QQuickItem* UIOwner::ui(bool cache){
    if (cache && m_ui)
        return m_ui;

    if (!m_context) {
        m_context = new QQmlContext(rootEngine, (QObject*)this);
        m_context->setContextProperty("owner", this);
    }

    QQmlComponent* component = componentsCache[m_ui_url];
    if (!component) {
        component = new QQmlComponent(rootEngine, QUrl(m_ui_url));
        if (component->isError()) {
            qWarning() << "Error on component load. Context: " << rootEngine->rootContext() << ". doSearch: " << rootEngine->rootContext()->contextProperty("dosearch");
            foreach(QQmlError error, component->errors()) {
                qWarning() << error;
            }
            exit(-1);
        }
        componentsCache[m_ui_url] = component;
    }
    QQuickItem* result = (QQuickItem*)component->create(m_context);
    qDebug() << "root context" << rootEngine->rootContext();
    qDebug() << "context before" << m_context;
    qDebug() << "context after" << rootEngine->contextForObject(result);
    if(result->childItems().size() > 0){
        qDebug() << "childrencontext" << rootEngine->contextForObject(result->childItems().at(0));
    }
    if (cache) {
        m_ui = result;
        updateConnections();
        //    m_ui->setParent(const_cast<Page*>(this));
        connect(m_ui, &QQuickItem::destroyed, [this](){
            m_ui = 0;
            emit uiChanged();
        });
        initUI(result);
        emit uiChanged();
    }

    return result;
}

bool UIOwner::transferUI(UIOwner* other){
    if (!m_ui || !m_context || other->m_ui) // have no ui or other have alreagy got one
        return false;
    QObject::disconnect(m_ui, 0, this, 0);
    QQuickItem* scopeItem = m_ui->parentItem();
    while (scopeItem && !scopeItem->isFocusScope() && scopeItem->parentItem())
        scopeItem = scopeItem->parentItem();

    if (!scopeItem) // the ui item is broken (no scope)
        return false;
    other->m_context = m_context;
    other->m_ui = m_ui;
    m_ui->setParentItem(0);
    //    m_ui->setParent(other);
    m_context->setContextProperty("owner", other);
    connect(m_ui, &QQuickItem::destroyed, [other](){
        other->m_ui = 0;
        emit other->uiChanged();
    });
    m_ui = 0;
    m_context = 0;
    emit other->uiChanged();
    emit uiChanged();
    return true;
}


QList<QQuickItem*> UIOwner::children(){
    return m_children;
}

QQuickItem* UIOwner::parent(){
    return m_parent;
}

void UIOwner::updateConnections(){
    m_children.clear();
    if(m_ui != nullptr){
        qDebug() << "children" << m_ui->childItems();
        for(auto child: m_ui->childItems()){
            collectChildren(m_children, child);
        }
        m_parent = findParent(m_ui->parentItem());
    }
}

void collectChildren(QList<QQuickItem*>& children, QQuickItem* item){
    if(!item->property("owner").isNull()){
        children.append(item);
        return;
    }
    for(auto child: item->childItems()){
        collectChildren(children, child);
    }
}

QQuickItem* findParent(QQuickItem* item){
    if(!item){
        return nullptr;
    }

    if(!item->property("owner").isNull()){
        return item;
    }
    QQuickItem* parent = item->parentItem();
    if(parent){
        return findParent(parent);
    }
    return nullptr;
}

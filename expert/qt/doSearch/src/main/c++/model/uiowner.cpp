#include "uiowner.h"
#include "../dosearch.h"

#include <QQmlContext>
#include <QQmlApplicationEngine>

QHash<QUrl, QQmlComponent*> componentsCache;

void collectChildren(QQuickItem* item, UIOwner* root, QList<UIOwner*>& children);

UIOwner::UIOwner(const QString& uiQml, QObject* parent):
    QObject(parent), m_ui_url(uiQml)
{
    //QObject::connect(this, SIGNAL(uiChanged()), this, SLOT(updateConnections()));
}

void UIOwner::clear() {
  updateConnections();
  if(m_ui){
    m_ui->deleteLater();
    m_ui = nullptr;
    for(auto child: m_children){
        child->clear();
    }
  }
}

QQuickItem* UIOwner::ui(bool cache) {
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
    //qDebug() << "ceating ui with owner" << QQmlEngine::contextForObject(result)->contextProperty("owner");
    if (cache) {
        m_ui = result;
        updateConnections();
        //    m_ui->setParent(const_cast<Page*>(this));
        connect(m_ui, &QQuickItem::destroyed, [this](){
            emit uiChanged();
        });
        initUI(result);
        emit uiChanged();
    }

    return result;
}

QQuickItem* UIOwner::uiNoCache(){
    return ui(false);
}

bool UIOwner::transferUI(UIOwner* other) {
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
    connect(m_ui, &QQuickItem::destroyed, [other]() {
        other->m_ui = 0;
        emit other->uiChanged();
    });
    m_ui = 0;
    m_context = 0;
    emit other->uiChanged();
    emit uiChanged();
    return true;
}


QList<UIOwner*> UIOwner::children() {
    return m_children;
}

UIOwner* UIOwner::parent() {
    return m_parent;
}


void UIOwner::updateConnections() {
    m_children.clear();
    if(m_ui ){
        collectChildren(m_ui, this, m_children);
    }
}

void collectChildren(QQuickItem* item, UIOwner* root, QList<UIOwner*>& children){
    for(auto child: item->childItems()) {
        QQmlContext* context = QQmlEngine::contextForObject(child);
        if(!context){
            continue;
        }
        UIOwner* owner = context->contextProperty("owner").value<UIOwner*>();
        if(owner && owner != root){
            children.append(owner);
            continue;
        }
        collectChildren(child, root, children);
    }
}

UIOwner* findParent(QQuickItem* item){
    QQmlContext* context = QQmlEngine::contextForObject(item);
    if(!context){
        return nullptr;
    }
    UIOwner* owner = context->contextProperty("owner").value<UIOwner*>();
    if(owner) {
        return owner;
    }
    if( item->parentItem()){
        return findParent(item->parentItem());
    }
    return nullptr;
}

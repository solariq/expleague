#include "vault.h"
#include "../dosearch.h"

#include <QDebug>

//#include <QQuickDropAreaDrag>

namespace expleague {

Knugget::Knugget(const QString& id, Page* source, Context* owner, const QString& uiQml, doSearch* parent): Page(id, uiQml, parent), m_source(source), m_owner(owner) {
    store("knugget.source", source->id());
    store("knugget.owner", owner->id());
}

Knugget::Knugget(const QString& id, const QString& uiQml, doSearch* parent): Page(id, uiQml, parent) {
}

void Knugget::interconnect() {
    m_source = parent()->page(value("knugget.source").toString());
    m_owner = static_cast<Context*>(parent()->page(value("knugget.owner").toString()));
}

TextKnugget::TextKnugget(const QString &id, const QString &text, Page* source, Context* context, doSearch *parent): Knugget(id, source, context, "qrc:/TextKnuggetView.qml", parent), m_text(text) {
    store("knugget.text", text);
    save();
}

TextKnugget::TextKnugget(const QString &id, doSearch *parent): Knugget(id, "qrc:/TextKnuggetView.qml", parent) {
    m_text = value("knugget.text").toString();
}

QString TextKnugget::md() const {
    WebPage* web = qobject_cast<WebPage*>(source());
    if (web)
        return m_text + "[Источник](" + web->url().toString() + ")";
    else
        return m_text;
}

Vault::Vault(Context* context): QObject(context) {
    context->visitAll("context.vault.item", [this, context](const QVariant& value){
        QVariantHash hash = value.toHash();
        Knugget* knugget = new TextKnugget(hash.value("id").toString(), doSearch::instance());
        knugget->interconnect();
        m_items.append(knugget);
    });
}

bool Vault::drop(const QString& text, const QString& html, const QList<QUrl>& urls, const QString& source) {
    qDebug() << "Drop caught. Text: [" << text << "], html: [" << html << "], urls: " << urls;
    QUrl urlFromText(text, QUrl::StrictMode);
    TextKnugget* knugget = 0;
    if (!text.isEmpty() && !text.startsWith("<img") && !urlFromText.isValid()) {
        knugget = new TextKnugget(parent()->id() + "/knugget/" + QString::number(m_items.size()), text, doSearch::instance()->page(source), parent(), doSearch::instance());
        knugget->interconnect();
        m_items.append(knugget);
        QVariantHash variant;
        variant["id"] = knugget->id();
        variant["type"] = "text";
        parent()->append("context.vault.item", variant);
        parent()->save();
        emit itemsChanged();
    }
    return knugget;
}

void Vault::remove(Page* page) {
    m_items.removeOne(page);
}
}

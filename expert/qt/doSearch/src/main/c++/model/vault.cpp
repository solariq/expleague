#include "vault.h"
#include "../dosearch.h"
#include "../league.h"

#include <QRegularExpression>
#include <QImageReader>
#include <QDebug>
#include <QBuffer>
#include <QClipboard>
#include <QMimeData>

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

void Knugget::open() const {
    doSearch* dosearch = doSearch::instance();
    if (source() != dosearch->empty())
        dosearch->navigation()->open(source());
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
        return m_text + "\n[Источник](" + web->url().toString() + ")";
    else
        return m_text;
}

ImageKnugget::ImageKnugget(const QString &id, const QString &alt, const QUrl& imageUrl, Page* source, Context* context, doSearch *parent):
    Knugget(id, source, context, "qrc:/ImageKnuggetView.qml", parent),
    m_alt(alt),
    m_src(imageUrl)
{
    store("knugget.alt", alt);
    store("knugget.src", imageUrl);
    save();
}

ImageKnugget::ImageKnugget(const QString &id, doSearch *parent):
    Knugget(id, "qrc:/ImageKnuggetView.qml", parent)
{
    m_alt = value("knugget.alt").toString();
    m_src = value("knugget.src").toUrl();
}

QString ImageKnugget::md() const {
    WebPage* web = qobject_cast<WebPage*>(source());
    QString text = "![" + m_alt + "](" + m_src.toString() + ")";
    if (web)
        return text + "\n[Источник](" + web->url().toString() + ")";
    else
        return text;
}

LinkKnugget::LinkKnugget(const QString& id, const QString& text, const QUrl& link, Page* source, Context* context, doSearch* parent):
    Knugget(id, source, context, "qrc:/LinkKnuggetView.qml", parent),
    m_text(text),
    m_link(link)
{
    store("knugget.text", text);
    store("knugget.link", link);
    save();
}

LinkKnugget::LinkKnugget(const QString &id, doSearch *parent):
    Knugget(id, "qrc:/LinkKnuggetView.qml", parent)
{
    m_text = value("knugget.text").toString();
    m_link = value("knugget.link").toUrl();
}

QString LinkKnugget::md() const {
    WebPage* web = qobject_cast<WebPage*>(source());
    QString text = "[" + m_text + "](" + m_link.toString() + ")";
    if (web)
        return text + "\n[Источник](" + web->url().toString() + ")";
    else
        return text;
}

QString LinkKnugget::screenshot() const {
    return hasScreenshot() ? screenshotTarget() : "";
}

QString LinkKnugget::screenshotTarget() const {
    return storage().absoluteFilePath("screenshot.png");
}

bool LinkKnugget::hasScreenshot() const {
    QFile file(screenshotTarget());
    return file.exists();
}

void LinkKnugget::open() const {
    doSearch* dosearch = doSearch::instance();
    dosearch->navigation()->open(dosearch->web(m_link));
}

QString LinkKnugget::title() const {
    if (!m_text.isEmpty())
        return m_text;
    else if (source() != parent()->empty())
        return source()->title();
    else
        return m_link.host();
}

GroupKnugget::GroupKnugget(const QString &id, Context *context, doSearch *parent):
    Knugget(id, parent->empty(), context, "qrc:/GroupKnuggetView.qml", parent),
    m_name(tr("Новая группа")),
    m_parent_group(0)
{
    save();
}

GroupKnugget::GroupKnugget(const QString &id, doSearch *parent):
    Knugget(id, "qrc:/GroupKnuggetView.qml", parent)
{
    QVariant name = value("knugget.name");
    m_name = name.isNull() ? m_name : value("knugget.name").toString();
}

void GroupKnugget::interconnect() {
    Knugget::interconnect();
    visitKeys("knugget.element", [this](const QVariant& value) {
        m_items.append(qobject_cast<Knugget*>(parent()->page(value.toString())));
    });
    QVariant parentGroup = value("knugget.parent");
    m_parent_group = parentGroup.isNull() ? 0 : parent()->page(parentGroup.toString());
}

void GroupKnugget::insert(Knugget* item, int index) {
    if (m_items.contains(item))
        return;
    GroupKnugget* group = qobject_cast<GroupKnugget*>(item);
    if (group)
        group->setParentGroup(this);
    m_items.insert(index >= 0 ? index : m_items.size(), item);
    Page::append("knugget.element", item->id());
    save();
    emit itemsChanged();
}

void GroupKnugget::remove(Knugget* item) {
    m_items.removeOne(item);
    Page::remove("knugget.element");
    for (int i = 0; i < m_items.size(); i++) {
        Page::append("knugget.element", m_items[i]->id());
    }
    save();
    emit itemsChanged();
}

void GroupKnugget::move(int from, int to) {
    m_items.move(from, to);
    Page::remove("knugget.element");
    for (int i = 0; i < m_items.size(); i++) {
        if (m_items[i]) // TODO: remove trash & ugar
            Page::append("knugget.element", m_items[i]->id());
    }
    save();
    emit itemsChanged();
}

void GroupKnugget::setName(const QString& name) {
    m_name = name;
    Page::store("knugget.name", name);
    save();
    emit titleChanged(name);
}

QString GroupKnugget::md() const {
    QString result;
    for (int i = 0; i < m_items.size(); i++) {
        result += m_items[i]->md();
        result += "\n";
    }

    return result;
}

void GroupKnugget::open() const {
    owner()->vault()->setActiveGroup(const_cast<GroupKnugget*>(this));
}

void GroupKnugget::setParentGroup(QObject* parent) {
    GroupKnugget* group = qobject_cast<GroupKnugget*>(parent);
    m_parent_group = group ? (QObject*)group : (QObject*)owner()->vault();
    store("knugget.parent", group ? QVariant(group->id()): QVariant());
    emit parentGroupChanged();
}

Vault::Vault(Context* context): QObject(context) {
    context->visitKeys("context.vault.item", [this, context](const QVariant& value){
        m_items.append(qobject_cast<Knugget*>(context->parent()->page(value.toString())));
    });
}

void Vault::insert(Knugget* item, int position) {
    if (m_items.contains(item))
        return;
    if (position < 0)
        position = m_items.size();
    m_items.insert(position, item);
    parent()->append("context.vault.item", item->id());
    parent()->save();
    emit itemsChanged();
}

QString Vault::generateKnuggetId(const QString& suffix, int index) {
    if (index < 0)
        index = parent()->children("knugget").size();
    return parent()->id() + "/knugget/" + suffix + "-" + QString::number(index);
}

bool Vault::drop(const QString& text, const QString& html, const QList<QUrl>& urls, const QString& source) {
    qDebug() << "Drop caught. Text: [" << text << "], html: [" << html << "], urls: " << urls;
    QUrl urlFromText(text, QUrl::StrictMode);
    Knugget* knugget = 0;
    if (!text.isEmpty() && !text.startsWith("<img") && !urlFromText.isValid() && urlFromText.scheme() != "file") {
        knugget = new TextKnugget(
                    generateKnuggetId("text"),
                    text,
                    doSearch::instance()->page(source),
                    parent(),
                    doSearch::instance()
        );
    }
    else if (html.startsWith("<img")) {
        static QRegularExpression altRE("alt=\"([^\"]+)\"");
        static QRegularExpression srcRE("src=\"([^\"]+)\"");
        QRegularExpressionMatch altMatch = altRE.match(html);
        QRegularExpressionMatch srcMatch = srcRE.match(html);
        if (srcMatch.hasMatch()) {
            QUrl src(srcMatch.captured(1));
            if (src.scheme() == "data") {
                static QRegularExpression dataRE("src=\"(?<mime>[^;]+);base64,(?<data>[^\"]+)\"");
                QRegularExpressionMatch srcMatch = dataRE.match(html);
                if (srcMatch.hasMatch()) {
                    QString base64 = srcMatch.captured("data");
                    QByteArray data = QByteArray::fromBase64(base64.toLatin1());
                    QBuffer buffer(&data);
                    QImageReader reader(&buffer);
                    QString format = srcMatch.captured("mime").mid(strlen("image/"));
                    reader.setFormat(format.toLatin1());
                    QImage image = reader.read();
                    if (!image.isNull())
                        src = League::instance()->uploadImage(image);
                    else  {
                        qDebug() << "Invalid image received: " << base64;
                        return false;
                    }
                }
                else return false;
            }

            knugget = new ImageKnugget(
                        generateKnuggetId("image"),
                        altMatch.captured(1),
                        src,
                        doSearch::instance()->page(source),
                        parent(),
                        doSearch::instance()
            );
        }
    }
    else if (urlFromText.scheme() == "file") {
        QImage img(urlFromText.toLocalFile());
        if (!img.isNull()) {
            knugget = new ImageKnugget(
                                generateKnuggetId("image"),
                                text.section('/', -1).section('.', 0, -2),
                                League::instance()->uploadImage(img),
                                doSearch::instance()->page(source),
                                parent(),
                                doSearch::instance()
            );
        }
    }
    else if (urlFromText.isValid()) {
        knugget = new LinkKnugget(
                    generateKnuggetId("link"),
                    "",
                    urlFromText,
                    doSearch::instance()->page(source),
                    parent(),
                    doSearch::instance()
                    );
    }

    if (!knugget)
        return false;
    knugget->interconnect();
    if (!m_active_group)
        insert(knugget);
    else m_active_group->insert(knugget);

    return true;
}

void Vault::group(Knugget* left, Knugget* right) {
    GroupKnugget* group = qobject_cast<GroupKnugget*>(left);
    if (m_active_group)
        m_active_group->remove(right);
    else
        remove(right);

    if (!group) {
        group = new GroupKnugget(generateKnuggetId("group"), parent(), parent()->parent());
        group->setParentGroup(m_active_group);
        if (m_active_group) {
            m_active_group->insert(group, m_active_group->indexOf(left));
            m_active_group->remove(left);
        }
        else {
            insert(group, m_items.indexOf(left));
            remove(left);
        }
        group->insert(left);
        setActiveGroup(group);
    }

    group->insert(right);
}

void Vault::ungroup(GroupKnugget* group) {
    GroupKnugget* parent = qobject_cast<GroupKnugget*>(group->parentGroup());
    if (parent) {
        int index = parent->indexOf(group);
        parent->remove(group);
        foreach(Knugget* item, group->items()) {
            parent->insert(item, index++);
        }
        if (m_active_group == group)
            setActiveGroup(parent);
    }
    else {
        int index = m_items.indexOf(group);
        remove(group);
        foreach(Knugget* item, group->items()) {
            insert(item, index++);
        }
        if (m_active_group == group)
            setActiveGroup(0);
    }
}

bool Vault::paste(Page* source) {
    if (source == 0)
        source = doSearch::instance()->empty();
    const QClipboard* clipboard = QApplication::clipboard();
    QStringList result;
    QList<Knugget*> knuggets;
    const QMimeData* mime = clipboard->mimeData();
    int index = parent()->children("knugget").size();
    foreach (const QUrl& url, mime->urls()) {
        if (url.scheme() == "file") {
            QImage img(url.toLocalFile());
            if (!img.isNull()) {
                knuggets.append(new ImageKnugget(
                                    generateKnuggetId("image", index++),
                                    mime->hasText() ? mime->text() : "",
                                    League::instance()->uploadImage(img),
                                    source,
                                    parent(),
                                    doSearch::instance()
                ));
            }
        }
        else if (url.scheme() == "http" || url.scheme() == "https" || url.scheme() == "ftp") {
            if (QImageReader::imageFormat(url.fileName()).isEmpty()) {
                knuggets.append(new LinkKnugget(
                                    generateKnuggetId("link", index++),
                                    mime->hasText() ? mime->text() : "",
                                    url,
                                    source,
                                    parent(),
                                    doSearch::instance()
                ));
            }
            else {
                knuggets.append(new ImageKnugget(
                                    generateKnuggetId("image", index++),
                                    mime->hasText() ? mime->text() : "",
                                    url,
                                    source,
                                    parent(),
                                    doSearch::instance()
                ));
            }
        }
        else {
            knuggets.append(new LinkKnugget(
                                generateKnuggetId("link", index++),
                                mime->hasText() ? mime->text() : "",
                                url,
                                source,
                                parent(),
                                doSearch::instance()
            ));
        }
    }
    if (knuggets.empty()) {
        if (mime->hasImage()) {
            QUrl url = League::instance()->uploadImage(qvariant_cast<QImage>(mime->imageData()));
            knuggets.append(new ImageKnugget(
                                generateKnuggetId("image", index++),
                                mime->hasText() ? mime->text() : "",
                                url,
                                source,
                                parent(),
                                doSearch::instance()
            ));
        }
        else if (mime->hasText()) {
            knuggets.append(new TextKnugget(
                        generateKnuggetId("text", index++),
                        mime->text(),
                        source,
                        parent(),
                        doSearch::instance()
            ));

        }
    }

    if (knuggets.empty())
        return false;
    foreach (Knugget* item, knuggets) {
        item->interconnect();
        if (!m_active_group)
            insert(item);
        else
            m_active_group->insert(item);
    }
    emit itemsChanged();
    return true;
}

void Vault::appendLink(const QUrl& url, const QString& text, Page* source) {
    Knugget* knugget = new LinkKnugget(
                generateKnuggetId("link"),
                text,
                url,
                source ? source : doSearch::instance()->empty(),
                parent(),
                doSearch::instance()
    );
    knugget->interconnect();
    if (!m_active_group)
        insert(knugget);
    else
        m_active_group->insert(knugget);
}

void Vault::remove(Knugget* page) {
    if (!m_active_group) {
        m_items.removeOne(page);
        parent()->remove("context.vault.item");
        for (int i = 0; i < m_items.size(); i++) {
            parent()->append("context.vault.item", m_items[i]->id());
        }
        parent()->save();
        emit itemsChanged();
    }
    else m_active_group->remove(page);
}

void Vault::move(int from, int to) {
    if (!m_active_group) {
        m_items.move(from, to);
        parent()->remove("context.vault.item");
        for (int i = 0; i < m_items.size(); i++) {
            parent()->append("context.vault.item", m_items[i]->id());
        }
        parent()->save();
        emit itemsChanged();
    }
    else m_active_group->move(from, to);
}

void Vault::commitVisualModel(QObject* model) {
    qDebug() << model;

    // TODO
}


void Vault::clearClipboard() const {
    QApplication::clipboard()->clear();
}
}
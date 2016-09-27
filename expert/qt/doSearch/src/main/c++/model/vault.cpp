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

Vault::Vault(Context* context): QObject(context) {
    context->visitAll("context.vault.item", [this, context](const QVariant& value){
        m_items.append(qobject_cast<Knugget*>(context->parent()->page(value.toString())));
    });
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
    m_items.append(knugget);
    parent()->append("context.vault.item", knugget->id());
    parent()->save();
    emit itemsChanged();

    return true;
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
        m_items.append(item);
        parent()->append("context.vault.item", item->id());
    }
    parent()->save();
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
    m_items.append(knugget);
    parent()->append("context.vault.item", knugget->id());
    parent()->save();
    emit itemsChanged();
}

void Vault::remove(Knugget* page) {
    m_items.removeOne(page);
    parent()->remove("context.vault.item");
    for (int i = 0; i < m_items.size(); i++) {
        parent()->append("context.vault.item", m_items[i]->id());
    }
    parent()->save();
    emit itemsChanged();
}

void Vault::clearClipboard() const {
    QApplication::clipboard()->clear();
}
}

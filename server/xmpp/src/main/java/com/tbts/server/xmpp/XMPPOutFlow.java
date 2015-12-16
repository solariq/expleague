package com.tbts.server.xmpp;

import akka.stream.Supervision;
import akka.stream.stage.Context;
import akka.stream.stage.PushPullStage;
import akka.stream.stage.SyncDirective;
import akka.stream.stage.TerminationDirective;
import akka.util.ByteString;
import com.tbts.xmpp.Item;
import com.tbts.xmpp.Stream;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBIntrospector;
import javax.xml.namespace.QName;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 09.12.15
 * Time: 15:30
 */
public class XMPPOutFlow extends PushPullStage<Item, ByteString> {
  public static final String XMPP_START = "<stream:stream xmlns:stream=\"http://etherx.jabber.org/streams\" version=\"1.0\" xmlns=\"jabber:client\" xml:lang=\"en\" xmlns:xml=\"http://www.w3.org/XML/1998/namespace\">";
  private static final Logger log = Logger.getLogger(XMPPOutFlow.class.getName());
  private final Queue<ByteString> queue = new ArrayDeque<>();
  private final JAXBIntrospector introspector;

  public XMPPOutFlow() {
    final JAXBContext context = Stream.jaxb();
    introspector = context.createJAXBIntrospector();
    queue.add(ByteString.fromString(XMPP_START));
  }

  @Override
  public TerminationDirective onUpstreamFinish(Context<ByteString> context) {
//    queue.add(ByteString.fromString("</stream:stream>"));
//    queue.add(ByteString.fromString(""));
    return context.absorbTermination();
  }

  @Override
  public SyncDirective onPush(Item item, Context<ByteString> itemContext) {
    queue.add(ByteString.fromString(item.xmlString()));
    return onPull(itemContext);
  }

  private final Map<Class<?>, QName> cache = new HashMap<>();
  @NotNull
  private QName qnameByItem(Item item) {
    final Class<? extends Item> itemClass = item.getClass();
    QName qName = cache.get(itemClass);
    if (qName == null) {
      qName = introspector.getElementName(item);
      cache.put(itemClass, qName);
    }
    return qName;
  }

  @Override
  public SyncDirective onPull(Context<ByteString> ctxt) {
    return queue.isEmpty() ? (ctxt.isFinishing() ? ctxt.finish() : ctxt.pull()) : ctxt.push(queue.poll());
  }

  @Override
  public Supervision.Directive decide(Throwable t) {
    log.log(Level.SEVERE, "JAXB output generation error", t);
    return Supervision.stop();
  }
}

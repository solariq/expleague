package com.tbts.server.xmpp;

import akka.stream.Supervision;
import akka.stream.stage.Context;
import akka.stream.stage.LifecycleContext;
import akka.stream.stage.PushPullStage;
import akka.stream.stage.SyncDirective;
import akka.util.ByteString;
import com.fasterxml.aalto.AsyncByteArrayFeeder;
import com.fasterxml.aalto.AsyncXMLInputFactory;
import com.fasterxml.aalto.AsyncXMLStreamReader;
import com.fasterxml.aalto.stax.InputFactoryImpl;
import com.tbts.util.xml.AsyncJAXBStreamReader;
import com.tbts.xmpp.Item;
import com.tbts.xmpp.Stream;
import org.xml.sax.SAXException;

import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 09.12.15
 * Time: 15:30
 */
public class XMPPInFlow extends PushPullStage<ByteString, Item> {
  private static final Logger log = Logger.getLogger(XMPPInFlow.class.getName());
  private final Queue<Item> queue = new ArrayDeque<>();
  private final String name;
  private AsyncXMLStreamReader<AsyncByteArrayFeeder> asyncXml;
  private AsyncJAXBStreamReader reader;
  // TODO: remove this shit after investigating wrong epilog state in aalto
  private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

  public XMPPInFlow(String name) {
    this.name = name;
  }


  @Override
  public void preStart(LifecycleContext lifecycleContext) throws Exception {
    super.preStart(lifecycleContext);
    final AsyncXMLInputFactory factory = new InputFactoryImpl();
    asyncXml = factory.createAsyncForByteArray();
    reader = new AsyncJAXBStreamReader(asyncXml, Stream.jaxb());
    baos.reset();
  }

  @Override
  public SyncDirective onPush(ByteString data, Context<Item> itemContext) {
    final byte[] copy = new byte[data.length()];
    data.asByteBuffer().get(copy);
    final String s = new String(copy).trim();
    { // debug
      if (!s.isEmpty())
        log.finest(s);
//      else return onPull(itemContext);
    }
    try {
      baos.write(copy, 0, copy.length);
      asyncXml.getInputFeeder().feedInput(copy, 0, copy.length);
      reader.drain(o -> {
        if (o instanceof Item)
          queue.add((Item) o);
      });
    }
    catch (XMLStreamException | SAXException e) {
      throw new RuntimeException("@" + name + " on [" + s + "] message in context [" + new String(baos.toByteArray()) + "]", e);
    }

    return onPull(itemContext);
  }

  @Override
  public SyncDirective onPull(Context<Item> itemContext) {
    if (queue.isEmpty())
      return itemContext.pull();
    else
      return itemContext.push(queue.poll());
  }

  @Override
  public Supervision.Directive decide(Throwable t) {
    log.log(Level.SEVERE, "JAXB input parsing error", t);
    return Supervision.stop();
  }
}

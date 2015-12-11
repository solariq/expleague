package com.tbts.server.xmpp;

import akka.stream.Supervision;
import akka.stream.stage.Context;
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
  private final AsyncXMLStreamReader<AsyncByteArrayFeeder> asyncXml;
  private final AsyncJAXBStreamReader reader;

  public XMPPInFlow() {
    final AsyncXMLInputFactory factory = new InputFactoryImpl();
    asyncXml = factory.createAsyncForByteArray();
    reader = new AsyncJAXBStreamReader(asyncXml, Stream.jaxb());
  }

  @Override
  public SyncDirective onPush(ByteString data, Context<Item> itemContext) {
    final byte[] copy = new byte[data.length()];
    data.asByteBuffer().get(copy);
    try {
      asyncXml.getInputFeeder().feedInput(copy, 0, copy.length);
      reader.drain(o -> {
        if (o instanceof Item)
          queue.add((Item) o);
      });
    }
    catch (XMLStreamException | SAXException e) {
      throw new RuntimeException(e);
    }

    return onPull(itemContext);
  }

  @Override
  public SyncDirective onPull(Context<Item> itemContext) {
    return queue.isEmpty() ? itemContext.pull() : itemContext.push(queue.poll());
  }

  @Override
  public Supervision.Directive decide(Throwable t) {
    log.log(Level.SEVERE, "JAXB input parsing error", t);
    return Supervision.stop();
  }
}

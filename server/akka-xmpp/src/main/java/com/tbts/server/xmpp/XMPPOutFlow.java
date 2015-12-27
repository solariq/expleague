package com.tbts.server.xmpp;

import akka.stream.Supervision;
import akka.stream.stage.Context;
import akka.stream.stage.LifecycleContext;
import akka.stream.stage.PushPullStage;
import akka.stream.stage.SyncDirective;
import akka.util.ByteString;
import com.tbts.xmpp.Item;
import com.tbts.xmpp.control.Close;

import java.util.ArrayDeque;
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

  @Override
  public void preStart(LifecycleContext lifecycleContext) throws Exception {
    super.preStart(lifecycleContext);
    queue.add(ByteString.fromString(XMPP_START));
  }

  @Override
  public SyncDirective onPush(Item item, Context<ByteString> itemContext) {
    if (item instanceof Close)
      return onPull(itemContext);
    final String out = item.xmlString();
    { // debug
      log.finest(out);
    }
    queue.add(ByteString.fromString(out));
    return onPull(itemContext);
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

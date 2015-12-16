package com.tbts.server.xmpp.phase;

import akka.stream.stage.Context;
import akka.stream.stage.PushPullStage;
import akka.stream.stage.SyncDirective;
import com.spbsu.commons.system.RuntimeUtils;
import com.tbts.xmpp.Item;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 08.12.15
 * Time: 17:15
 */
public class XMPPPhase extends PushPullStage<Item, Item> {
  private static final Logger log = Logger.getLogger(XMPPPhase.class.getName());
  private boolean stopped = false;
  private final RuntimeUtils.InvokeDispatcher dispatcher;

  protected XMPPPhase() {
    dispatcher = new RuntimeUtils.InvokeDispatcher(getClass(), this::unhandled);
  }

  protected void unhandled(Object msg) {
    log.log(Level.WARNING, "Unexpected xmpp item: " + msg);
  }
  protected void answer(Item item) {
    out.add(item);
  }
  protected void stop() {
    stopped = true;
  }

  private final Queue<Item> out = new ArrayDeque<>();
  @Override
  public SyncDirective onPush(Item message, Context<Item> itemContext) {
    dispatcher.invoke(this, message);
    return onPull(itemContext);
  }

  @Override
  public SyncDirective onPull(Context<Item> itemContext) {
    return !out.isEmpty() ? itemContext.push(out.poll()) : !stopped ? itemContext.pull() : itemContext.finish();
  }
}

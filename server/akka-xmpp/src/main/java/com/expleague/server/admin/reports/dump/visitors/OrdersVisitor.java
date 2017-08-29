package com.expleague.server.admin.reports.dump.visitors;

import com.expleague.model.Answer;
import com.expleague.model.Offer;
import com.expleague.model.Operations;
import com.expleague.server.admin.reports.dump.DumpVisitor;
import com.expleague.xmpp.stanza.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Artem
 * Date: 22.08.2017
 */
public class OrdersVisitor extends DumpVisitor<List<OrdersVisitor.Order>> {
  private final List<Order> result = new ArrayList<>();
  private Order currentInterval = null;
  private String lastMessageId = null;
  private String owner = null;

  private boolean started = false;
  private boolean done = false;

  @Override
  public List<Order> result() {
    if (currentInterval != null && currentInterval.lastMessageId() == null) {
      currentInterval.lastMessageId(lastMessageId);
      result.add(currentInterval);
    }
    return result;
  }

  @Override
  protected void process(Message message) {
    if (message.has(Offer.class)) {
      { //owner
        if (owner == null) {
          final Offer offer = message.get(Offer.class);
          if (offer.client() == null)
            owner = message.from().local();
          else
            owner = offer.client().local();
        }
      }
      { //processing
        if (done) {
          currentInterval.lastMessageId(lastMessageId);
          result.add(currentInterval);
          currentInterval = null;

          started = false;
          done = false;
        }
        if (currentInterval == null)
          currentInterval = new Order(message.id(), message.ts());
      }
    }
    else if (message.has(Operations.Start.class)) {
      started = true;
    }
    else if ((message.has(Operations.Cancel.class) && (started || message.from().local().equals(owner))) || message.has(Answer.class)) {
      done = true;
    }
    lastMessageId = message.id();
  }

  @Override
  protected boolean check(Message message) {
    return true;
  }

  public static class Order {
    private final long ts;
    private final String firstMessageId;
    private String lastMessageId = null;

    private Order(String firstMessageId, long ts) {
      this.firstMessageId = firstMessageId;
      this.ts = ts;
    }

    public String firstMessageId() {
      return firstMessageId;
    }

    public String lastMessageId() {
      return lastMessageId;
    }

    public long ts() {
      return ts;
    }

    private void lastMessageId(String lastMessageId) {
      this.lastMessageId = lastMessageId;
    }
  }
}

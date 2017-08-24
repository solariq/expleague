package com.expleague.server.admin.reports.dump.visitors;

import com.expleague.model.Answer;
import com.expleague.model.Offer;
import com.expleague.model.Operations;
import com.expleague.model.OrderState;
import com.expleague.server.admin.reports.dump.DumpVisitor;
import com.expleague.xmpp.stanza.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Artem
 * Date: 22.08.2017
 */
public class OrdersVisitor extends DumpVisitor<List<OrdersVisitor.OrderInterval>> {
  private final List<OrderInterval> result = new ArrayList<>();
  private OrderInterval currentInterval = null;
  private String lastMessageId = null;

  private boolean started = false;
  private boolean done = false;

  @Override
  public List<OrderInterval> result() {
    if (currentInterval != null && currentInterval.lastMessageId() == null) {
      currentInterval.lastMessageId(lastMessageId);
      result.add(currentInterval);
    }
    return result;
  }

  @Override
  protected void process(Message message) {
    if (message.has(Offer.class)) {
      tryToReopenInterval(message);
    }
    else if (message.has(Operations.Start.class)) {
      started = true;
    }
    else if (started && message.has(Operations.Cancel.class)) {
      done = true;
    }
    else if (started && message.has(Operations.Progress.class)) {
      final Operations.Progress progress = message.get(Operations.Progress.class);
      if (progress.state() == OrderState.DONE) {
        done = true;
      }
    }
    else if (message.has(Answer.class)) {
      if (!started && fromAdmin(message.from()))
        currentInterval.shortAnswer();
      done = true;
    }
    lastMessageId = message.id();
  }

  private void tryToReopenInterval(Message message) {
    if (done) {
      currentInterval.lastMessageId(lastMessageId);
      result.add(currentInterval);
      currentInterval = null;

      started = false;
      done = false;
    }
    if (currentInterval == null)
      currentInterval = new OrderInterval(message.id());
  }

  @Override
  protected boolean check(Message message) {
    return true;
  }

  public static class OrderInterval {
    private String firstMessageId;
    private String lastMessageId = null;
    private boolean shortAnswer = false;

    private OrderInterval(String firstMessageId) {
      this.firstMessageId = firstMessageId;
    }

    public String firstMessageId() {
      return firstMessageId;
    }

    public String lastMessageId() {
      return lastMessageId;
    }

    public boolean isShortAnswer() {
      return shortAnswer;
    }

    private void lastMessageId(String lastMessageId) {
      this.lastMessageId = lastMessageId;
    }

    private void shortAnswer() {
      this.shortAnswer = true;
    }
  }
}

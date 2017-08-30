package com.expleague.server.admin.reports.dump.quality_monitoring.visitors;

import com.expleague.model.Answer;
import com.expleague.model.Operations;
import com.expleague.server.admin.reports.dump.visitors.SpecifiedOrderVisitor;
import com.expleague.xmpp.stanza.Message;

/**
 * User: Artem
 * Date: 29.08.2017
 */
public class StatusVisitor extends SpecifiedOrderVisitor<StatusVisitor.OrderStatus> {
  private boolean started = false;
  private StatusVisitor.OrderStatus status = OrderStatus.IN_PROGRESS;

  public StatusVisitor(String startMessageId, String stopMessageId) {
    super(startMessageId, stopMessageId);
  }

  @Override
  public StatusVisitor.OrderStatus result() {
    return status;
  }

  @Override
  protected void process(Message message) {
    super.process(message);
    if (message.has(Operations.Start.class)) {
      started = true;
    }
    else if (message.has(Answer.class)) {
      if (!started && fromAdmin(message.from()))
        status = OrderStatus.SHORT_ANSWER;
      else
        status = OrderStatus.DONE;
      done();
    }
    else if (message.has(Operations.Cancel.class) && (started || message.from().local().equals(owner()))) {
      status = OrderStatus.CANCEL;
      done();
    }
  }

  public enum OrderStatus {
    CANCEL(0),
    DONE(1),
    SHORT_ANSWER(2),
    IN_PROGRESS(3),;

    int index;

    OrderStatus(int index) {
      this.index = index;
    }

    public int index() {
      return index;
    }
  }
}

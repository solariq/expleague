package com.expleague.server.admin.reports.dump.quality_monitoring.visitors;

import com.expleague.model.Answer;
import com.expleague.model.Operations;
import com.expleague.server.admin.reports.dump.visitors.RoomVisitor;
import com.expleague.xmpp.stanza.Message;

/**
 * User: Artem
 * Date: 30.08.2017
 */
public class ContinueVisitor extends RoomVisitor<String> {
  private final String firstMessageId;

  private boolean answered = false;
  private String continueText = "";
  private long continueMessageTs = 0;

  public ContinueVisitor(String firstMessageId) {
    this.firstMessageId = firstMessageId;
  }

  @Override
  public String result() {
    return continueText;
  }

  @Override
  protected void process(Message message) {
    super.process(message);
    if (message.id().equals(firstMessageId)) {
      done();
      return;
    }
    if (message.has(Answer.class)) {
      answered = true;
    }
    else if (answered && message.from().local().equals(owner()) && message.has(Message.Body.class)) {
      continueText = message.get(Message.Body.class).value();
      continueMessageTs = message.ts();
      answered = false;
    }
    else if (message.ts() == continueMessageTs && message.has(Operations.Feedback.class)) {
      continueText = "";
      continueMessageTs = 0;
    }
  }

  @Override
  protected boolean check(Message message) {
    return true;
  }
}

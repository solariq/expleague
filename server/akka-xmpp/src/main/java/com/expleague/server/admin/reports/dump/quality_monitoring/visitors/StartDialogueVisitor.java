package com.expleague.server.admin.reports.dump.quality_monitoring.visitors;

import com.expleague.server.admin.reports.dump.visitors.SpecifiedOrderVisitor;
import com.expleague.xmpp.stanza.Message;

/**
 * User: Artem
 * Date: 01.09.2017
 */
public class StartDialogueVisitor extends SpecifiedOrderVisitor<Long> {
  public static final long NO_DIALOGUE = -1;
  private long startDialogueTs = NO_DIALOGUE;

  public StartDialogueVisitor(String startMessageId, String stopMessageId) {
    super(startMessageId, stopMessageId);
  }

  @Override
  protected void process(Message message) {
    super.process(message);
    if (fromAdmin(message.from()) && message.has(Message.Body.class)) {
      startDialogueTs = message.ts();
      done();
    }
  }

  @Override
  public Long result() {
    return startDialogueTs;
  }
}

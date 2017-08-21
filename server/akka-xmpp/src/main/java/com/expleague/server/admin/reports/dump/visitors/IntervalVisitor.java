package com.expleague.server.admin.reports.dump.visitors;

import com.expleague.xmpp.stanza.Message;

/**
 * User: Artem
 * Date: 23.08.2017
 */
public abstract class IntervalVisitor<T> extends EarlyStopVisitor<T> {
  private final String startMessageId;
  private final String stopMessageId;
  private boolean started = false;

  protected IntervalVisitor(String startMessageId, String stopMessageId) {
    this.startMessageId = startMessageId;
    this.stopMessageId = stopMessageId;
  }

  @Override
  protected boolean check(Message message) {
    if (!started && message.id().equals(startMessageId)) {
      started = true;
    }
    else if (started && message.id().equals(stopMessageId)) {
      started = false;
      super.done();
    }
    return started;
  }
}

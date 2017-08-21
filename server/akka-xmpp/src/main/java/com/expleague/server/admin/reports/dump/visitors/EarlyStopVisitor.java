package com.expleague.server.admin.reports.dump.visitors;

import com.expleague.server.admin.reports.dump.DumpVisitor;
import com.expleague.xmpp.stanza.Message;

/**
 * User: Artem
 * Date: 23.08.2017
 */
public abstract class EarlyStopVisitor<T> extends DumpVisitor<T> {
  private boolean done = false;

  @Override
  public void visit(Message message) {
    if (done)
      return;
    super.visit(message);
  }

  protected void done() {
    done = true;
  }
}

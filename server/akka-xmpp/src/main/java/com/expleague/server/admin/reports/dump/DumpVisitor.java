package com.expleague.server.admin.reports.dump;

import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;

/**
 * User: Artem
 * Date: 22.08.2017
 */
public abstract class DumpVisitor<T> {
  public abstract T result();

  public void visit(Message message) {
    if (check(message))
      process(message);
  }

  protected abstract void process(Message message);

  protected abstract boolean check(Message message);

  protected boolean fromAdmin(JID jid) {
    return jid.resource() != null && jid.resource().endsWith("/admin");
  }
}

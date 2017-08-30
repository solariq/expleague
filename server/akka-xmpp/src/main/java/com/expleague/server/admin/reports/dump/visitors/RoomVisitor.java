package com.expleague.server.admin.reports.dump.visitors;

import com.expleague.model.Offer;
import com.expleague.server.admin.reports.dump.DumpVisitor;
import com.expleague.xmpp.stanza.Message;

/**
 * User: Artem
 * Date: 30.08.2017
 */
public abstract class RoomVisitor<T> extends DumpVisitor<T> {
  private String owner = null;

  protected String owner() {
    return owner;
  }

  @Override
  protected void process(Message message) {
    if (message.has(Offer.class)) {
      final Offer offer = message.get(Offer.class);
      if (offer.client() == null)
        owner = message.from().local();
      else
        owner = offer.client().local();
    }
  }
}

package com.expleague.server.admin.reports.dump.quality_monitoring.visitors;

import com.expleague.model.Offer;
import com.expleague.server.admin.reports.dump.visitors.OrdersSearchVisitor;
import com.expleague.xmpp.stanza.Message;

/**
 * User: Artem
 * Date: 22.08.2017
 */
public class ClientOrdersSearchVisitor extends OrdersSearchVisitor {
  private final String client;
  private boolean checked = false;
  private boolean clientMatch = false;

  public ClientOrdersSearchVisitor(String client) {
    this.client = client;
    if (client == null) {
      checked = true;
      clientMatch = true;
    }
  }

  @Override
  protected boolean check(Message message) {
    if (!checked && message.has(Offer.class)) {
      final Offer offer = message.get(Offer.class);
      clientMatch = offer.client().local().equals(client);
      checked = true;
    }
    return clientMatch;
  }
}

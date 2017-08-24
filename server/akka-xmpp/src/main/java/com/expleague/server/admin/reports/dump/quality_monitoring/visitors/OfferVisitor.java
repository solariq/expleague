package com.expleague.server.admin.reports.dump.quality_monitoring.visitors;

import com.expleague.model.Offer;
import com.expleague.server.admin.reports.dump.visitors.IntervalVisitor;
import com.expleague.xmpp.stanza.Message;

/**
 * User: Artem
 * Date: 23.08.2017
 */
public class OfferVisitor extends IntervalVisitor<Offer> {
  private Offer result = null;

  public OfferVisitor(String startMessageId, String stopMessageId) {
    super(startMessageId, stopMessageId);
  }

  @Override
  public Offer result() {
    return result;
  }

  @Override
  protected void process(Message message) {
    if (message.has(Offer.class)) {
      result = message.get(Offer.class);
      super.done();
    }
  }
}

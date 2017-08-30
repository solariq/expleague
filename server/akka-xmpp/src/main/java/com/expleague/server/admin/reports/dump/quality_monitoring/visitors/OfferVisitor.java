package com.expleague.server.admin.reports.dump.quality_monitoring.visitors;

import com.expleague.model.Offer;
import com.expleague.server.admin.reports.dump.visitors.SpecifiedOrderVisitor;
import com.expleague.xmpp.stanza.Message;

/**
 * User: Artem
 * Date: 23.08.2017
 */
public class OfferVisitor extends SpecifiedOrderVisitor<Message> {
  private Message result = null;

  public OfferVisitor(String startMessageId, String stopMessageId) {
    super(startMessageId, stopMessageId);
  }

  @Override
  public Message result() {
    return result;
  }

  @Override
  protected void process(Message message) {
    super.process(message);
    if (message.has(Offer.class)) {
      result = message;
      super.done();
    }
  }
}

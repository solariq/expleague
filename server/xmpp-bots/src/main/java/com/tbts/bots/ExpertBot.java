package com.tbts.bots;

import com.spbsu.commons.util.sync.StateLatch;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;

/**
 * User: solar
 * Date: 11.10.15
 * Time: 21:10
 */
public class ExpertBot extends Bot {
  public ExpertBot(final BareJID jid, final String passwd) throws JaxmppException {
    super(jid, passwd, "expert");
  }


  public static void main(final String[] args) throws JaxmppException {
    final ExpertBot expert = new ExpertBot(BareJID.bareJIDInstance("somename", "localhost"), "poassord");
    final StateLatch latch = new StateLatch();
    expert.start();
    expert.online();
    expert.onClose(latch::advance);
    latch.state(2, 1);
    expert.stop();
  }
}

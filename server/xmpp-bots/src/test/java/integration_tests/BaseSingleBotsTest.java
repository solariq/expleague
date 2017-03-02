package integration_tests;

import com.expleague.bots.AdminBot;
import com.expleague.bots.ClientBot;
import com.expleague.bots.ExpertBot;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;

/**
 * User: Artem
 * Date: 28.02.2017
 * Time: 15:05
 */
public class BaseSingleBotsTest {
  protected ClientBot clientBot;
  protected AdminBot adminBot;
  protected ExpertBot expertBot;

  protected void setUpClient() throws JaxmppException {
    clientBot = new ClientBot(BareJID.bareJIDInstance("client-bot-1", "localhost"), "poassord");
    clientBot.start();
    clientBot.online();
  }

  protected void setUpAdmin() throws JaxmppException {
    adminBot = new AdminBot(BareJID.bareJIDInstance("admin-bot-1", "localhost"), "poassord");
    adminBot.start();
    adminBot.online();
  }

  protected void setUpExpert() throws JaxmppException {
    expertBot = new ExpertBot(BareJID.bareJIDInstance("expert-bot-1", "localhost"), "poassord");
    expertBot.start();
    expertBot.online();
  }

  protected void tearDownClient() throws JaxmppException {
    clientBot.offline();
    clientBot.stop();
  }

  protected void tearDownAdmin() throws JaxmppException {
    adminBot.offline();
    adminBot.stop();
  }

  protected void tearDownExpert() throws JaxmppException {
    expertBot.offline();
    expertBot.stop();
  }
}

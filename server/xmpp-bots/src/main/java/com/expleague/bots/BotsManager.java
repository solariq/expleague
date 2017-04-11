package com.expleague.bots;

import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;

import java.util.ArrayList;
import java.util.List;

import static com.expleague.bots.utils.FunctionalUtils.throwableConsumer;

/**
 * User: Artem
 * Date: 06.04.2017
 * Time: 2:02
 */
public class BotsManager {
  private final List<Bot> bots = new ArrayList<>();
  private int clientNum = 0;
  private int adminNum = 0;
  private int expertNum = 0;

  public ClientBot startNewClient() throws JaxmppException {
    final ClientBot clientBot = new ClientBot(BareJID.bareJIDInstance(String.format("client-bot-%d", ++clientNum), "localhost"), "poassord");
    bots.add(clientBot);
    clientBot.start();
    return clientBot;
  }

  public AdminBot startNewAdmin() throws JaxmppException {
    final AdminBot adminBot = new AdminBot(BareJID.bareJIDInstance(String.format("admin-bot-%d", ++adminNum), "localhost"), "poassord");
    bots.add(adminBot);
    adminBot.start();
    return adminBot;
  }

  public ExpertBot startNewExpert() throws JaxmppException {
    final ExpertBot expertBot = new ExpertBot(BareJID.bareJIDInstance(String.format("expert-bot-%d", ++expertNum), "localhost"), "poassord");
    bots.add(expertBot);
    expertBot.start();
    return expertBot;
  }

  public void stopAll() {
    bots.forEach(throwableConsumer(Bot::stop));
    bots.clear();
  }
}

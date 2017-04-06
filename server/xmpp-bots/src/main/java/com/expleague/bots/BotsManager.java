package com.expleague.bots;

import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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

  public void addBots(int clientCount, int adminCount, int expertCount) throws JaxmppException {
    for (int i = 0; i < clientCount; i++) {
      bots.add(new ClientBot(BareJID.bareJIDInstance(String.format("client-bot-%d", ++clientNum), "localhost"), "poassord"));
    }
    for (int i = 0; i < adminCount; i++) {
      bots.add(new AdminBot(BareJID.bareJIDInstance(String.format("admin-bot-%d", ++adminNum), "localhost"), "poassord"));
    }
    for (int i = 0; i < expertCount; i++) {
      bots.add(new ExpertBot(BareJID.bareJIDInstance(String.format("expert-bot-%d", ++expertNum), "localhost"), "poassord"));
    }
  }

  public ClientBot defaultClientBot() {
    return (ClientBot) findBots(ClientBot.class).findFirst().orElse(null);
  }

  public AdminBot defaultAdminBot() {
    return (AdminBot) findBots(AdminBot.class).findFirst().orElse(null);
  }

  public ExpertBot defaultExpertBot() {
    return (ExpertBot) findBots(ExpertBot.class).findFirst().orElse(null);
  }

  public ClientBot[] clientBots() {
    return findBots(ClientBot.class).toArray(ClientBot[]::new);
  }

  public AdminBot[] adminBots() {
    return findBots(AdminBot.class).toArray(AdminBot[]::new);
  }

  public ExpertBot[] ExpertBots() {
    return findBots(ExpertBot.class).toArray(ExpertBot[]::new);
  }

  public void startAll() {
    bots.forEach(throwableConsumer(Bot::start));
  }

  public void stopAll() {
    bots.forEach(throwableConsumer(Bot::stop));
  }

  private <T extends Bot> Stream<Bot> findBots(Class<T> clazz) {
    return bots.stream().filter(bot -> clazz.equals(bot.getClass()));
  }
}

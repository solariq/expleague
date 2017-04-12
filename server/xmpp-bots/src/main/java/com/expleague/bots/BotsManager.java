package com.expleague.bots;

import akka.actor.ActorSystem;
import com.expleague.server.ExpLeagueServer;
import com.expleague.server.XMPPServer;
import com.expleague.server.agents.LaborExchange;
import com.expleague.server.agents.XMPP;
import com.expleague.server.notifications.NotificationsManager;
import com.expleague.server.services.XMPPServices;
import com.expleague.util.akka.ActorAdapter;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;

import java.util.HashMap;
import java.util.Map;

import static com.expleague.bots.utils.FunctionalUtils.throwableConsumer;

/**
 * User: Artem
 * Date: 06.04.2017
 * Time: 2:02
 */
public class BotsManager {
  private final Map<String, Bot> bots = new HashMap<>();
  private int clientNum = 0;
  private int adminNum = 0;
  private int expertNum = 0;


  public BotsManager() {
    final Config load = ConfigFactory.load();
    try {
      ExpLeagueServer.setConfig(new ExpLeagueServer.ServerCfg(load));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    final ActorSystem system = ActorSystem.create("ExpLeague", load);
    system.actorOf(ActorAdapter.props(LaborExchange.class), "labor-exchange");
    system.actorOf(ActorAdapter.props(XMPPServices.class), "services");
    system.actorOf(ActorAdapter.props(XMPPServer.class), "comm");
    system.actorOf(ActorAdapter.props(NotificationsManager.class, null, null), "notifications");
    system.actorOf(ActorAdapter.props(XMPP.class), "xmpp");
  }

  public ClientBot nextClient() throws JaxmppException {
    final ClientBot clientBot = (ClientBot)bots.compute(String.format("client-bot-%d", ++clientNum), (id, bot) -> bot != null ? bot : new ClientBot(BareJID.bareJIDInstance(id, "localhost"), "poassord"));
    clientBot.online();
    return clientBot;
  }

  public AdminBot nextAdmin() throws JaxmppException {
    final AdminBot adminBot = (AdminBot) bots.compute(String.format("admin-bot-%d", ++adminNum), (id, bot) -> bot != null ? bot : new AdminBot(BareJID.bareJIDInstance(id, "localhost"), "poassord"));
    adminBot.online();
    return adminBot;
  }

  public ExpertBot nextExpert() throws JaxmppException {
    final ExpertBot expertBot = (ExpertBot) bots.compute(String.format("expert-bot-%d", ++expertNum), (id, bot) -> bot != null ? bot : new ExpertBot(BareJID.bareJIDInstance(id, "localhost"), "poassord"));
    expertBot.online();
    return expertBot;
  }

  public void stopAll() {
    bots.values().forEach(throwableConsumer(Bot::offline));
    clientNum = 0;
    expertNum = 0;
    adminNum = 0;
  }
}

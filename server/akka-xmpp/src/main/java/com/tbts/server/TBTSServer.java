package com.tbts.server;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import com.tbts.dao.Archive;
import com.tbts.server.agents.LaborExchange;
import com.tbts.server.agents.XMPP;
import com.tbts.server.services.Services;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.IOException;
import java.util.logging.LogManager;

/**
 * User: solar
 * Date: 24.11.15
 * Time: 17:42
 */
public class TBTSServer {
  private static Cfg config;
  private static Roster users;

  public static void main(String[] args) throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
    final Config load = ConfigFactory.parseResourcesAnySyntax("tbts.conf").withFallback(ConfigFactory.load()).resolve();
    config = new Cfg(load);
    users = config.roster().newInstance();
    Archive.instance = config.archive().newInstance();
    LogManager.getLogManager().readConfiguration(TBTSServer.class.getResourceAsStream("/logging.properties"));

    final ActorSystem system = ActorSystem.create("TBTS", load);


    final ActorRef actorRef = system.actorOf(Props.create(ConnectionManager.class));
    system.actorOf(Props.create(XMPP.class), "xmpp");
    system.actorOf(Props.create(Services.class), "services");
    system.actorOf(Props.create(XMPPServer.class, actorRef), "comm");
    system.actorOf(Props.create(BOSHServer.class), "bosh");
    system.actorOf(Props.create(LaborExchange.class), "labor-exchange");
  }

  public static synchronized Roster roster() {
    return users;
  }

  public static class ConnectionManager extends UntypedActor {
    @Override
    public void onReceive(Object o) throws Exception {
      System.out.println(String.valueOf(o));
    }
  }

  public static Cfg config() {
    return config;
  }

  public static class Cfg {
    private final String db;
    private final String domain;
    private final Class<? extends Archive> archive;
    private final Class<? extends Roster> roster;

    public Cfg(Config load) throws ClassNotFoundException {
      final Config tbts = load.getConfig("tbts");
      db = tbts.getString("db");
      domain = tbts.getString("domain");
      //noinspection unchecked
      archive = (Class<? extends Archive>) Class.forName(tbts.getString("archive"));
      //noinspection unchecked
      roster = (Class<? extends Roster>) Class.forName(tbts.getString("roster"));
    }

    public String domain() {
      return domain;
    }

    public String db() {
      return db;
    }

    public Class<? extends Archive> archive() {
      return archive;
    }

    public Class<? extends Roster> roster() {
      return roster;
    }
  }
}

package com.expleague.server;

import akka.actor.ActorSystem;
import akka.actor.Props;
import com.expleague.server.agents.LaborExchange;
import com.expleague.server.agents.XMPP;
import com.expleague.server.dao.Archive;
import com.expleague.server.services.XMPPServices;
import com.expleague.util.ios.NotificationsManager;
import com.google.common.annotations.VisibleForTesting;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 24.11.15
 * Time: 17:42
 */
public class ExpLeagueServer {
  private static final Logger log = Logger.getLogger(ExpLeagueServer.class.getName());
  private static Cfg config;
  private static Roster users;

  public static void main(String[] args) throws Exception {
    final Config load = ConfigFactory.load();
    setConfig(new Cfg(load));

    final ActorSystem system = ActorSystem.create("ExpLeague", load);

    NotificationsManager.instance();
    // singletons
    system.actorOf(Props.create(XMPP.class), "xmpp");
    system.actorOf(Props.create(LaborExchange.class), "labor-exchange");

    // per node
    system.actorOf(Props.create(XMPPServices.class), "services");
    system.actorOf(Props.create(XMPPServer.class), "comm");
    system.actorOf(Props.create(BOSHServer.class), "bosh");
    system.actorOf(Props.create(ImageStorage.class), "image-storage");
  }

  public static synchronized Roster roster() {
    return users;
  }

  public static Cfg config() {
    return config;
  }

  @VisibleForTesting
  protected static void setConfig(final Cfg cfg) throws Exception {
    config = cfg;
    users = config.roster().newInstance();
    Archive.instance = config.archive().newInstance();
    if (System.getProperty("logger.config") == null)
      LogManager.getLogManager().readConfiguration(ExpLeagueServer.class.getResourceAsStream("/logging.properties"));
    else
      LogManager.getLogManager().readConfiguration(new FileInputStream(System.getProperty("logger.config")));
  }

  public static class Cfg {
    private final String db;
    private final String domain;
    private final Class<? extends Archive> archive;
    private final Class<? extends Roster> roster;
    private final Type type;

    public Cfg(Config load) throws ClassNotFoundException {
      final Config tbts = load.getConfig("tbts");
      db = tbts.getString("db");
      domain = tbts.getString("domain");
      //noinspection unchecked
      archive = (Class<? extends Archive>) Class.forName(tbts.getString("archive"));
      //noinspection unchecked
      roster = (Class<? extends Roster>) Class.forName(tbts.getString("roster"));
      type = Type.valueOf(tbts.getString("type").toUpperCase());
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

    public Type type() {
      return type;
    }

    public enum Type {
      PRODUCTION,
      TEST
    }
  }
}

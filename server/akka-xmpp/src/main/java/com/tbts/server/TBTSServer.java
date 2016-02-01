package com.tbts.server;

import akka.actor.ActorSystem;
import akka.actor.Props;
import com.tbts.server.agents.LaborExchange;
import com.tbts.server.agents.XMPP;
import com.tbts.server.dao.Archive;
import com.tbts.server.services.XMPPServices;
import com.tbts.xmpp.control.sasl.Failure;
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
public class TBTSServer {
  private static final Logger log = Logger.getLogger(TBTSServer.class.getName());
  private static Cfg config;
  private static Roster users;

  public static void main(String[] args) throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
    final Config load = ConfigFactory.load();
    config = new Cfg(load);
    users = config.roster().newInstance();
    Archive.instance = config.archive().newInstance();
    if (System.getProperty("logger.config") == null)
      LogManager.getLogManager().readConfiguration(TBTSServer.class.getResourceAsStream("/logging.properties"));
    else
      LogManager.getLogManager().readConfiguration(new FileInputStream(System.getProperty("logger.config")));

    final ActorSystem system = ActorSystem.create("TBTS", load);

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

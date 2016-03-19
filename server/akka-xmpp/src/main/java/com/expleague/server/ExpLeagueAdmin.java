package com.expleague.server;

import akka.actor.ActorSystem;
import akka.actor.Props;
import com.expleague.server.admin.ExpLeagueAdminService;
import com.expleague.server.agents.LaborExchange;
import com.expleague.server.dao.Archive;
import com.expleague.server.dao.fake.InMemArchive;
import com.expleague.server.dao.fake.InMemRoster;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * User: solar
 * Date: 24.11.15
 * Time: 17:42
 */
@SuppressWarnings("unused")
public class ExpLeagueAdmin {
  public static void main(String[] args) throws Exception {
    final Config config = ConfigFactory.load();
    ExpLeagueServer.setConfig(new AdminCfg(config));

    final ActorSystem system = ActorSystem.create("ExpLeagueAdmin", config);

    system.actorOf(Props.create(ExpLeagueAdminService.class, config.getConfig("tbts.admin.standalone")), "admin-service");
  }

  public static class AdminCfg implements ExpLeagueServer.Cfg {
    private final Config config;

    private final String db;
    private final String domain;
    private final Type type;
    private final Class<? extends LaborExchange.Board> board;

    public AdminCfg(Config load) throws ClassNotFoundException {
      config = load.getConfig("tbts");
      db = config.getString("db");
      domain = config.getString("domain");
      type = Type.valueOf(config.getString("type").toUpperCase());
      board = (Class<? extends LaborExchange.Board>) Class.forName(config.getString("board"));
    }

    public String domain() {
      return domain;
    }

    public String db() {
      return db;
    }

    public ExpLeagueServer.ServerCfg.Type type() {
      return type;
    }

    @Override
    public Class<? extends Archive> archive() {
      return InMemArchive.class;
    }

    @Override
    public Class<? extends Roster> roster() {
      return InMemRoster.class;
    }

    @Override
    public Class<? extends LaborExchange.Board> board() {
      return board;
    }

    @Override
    public Config config() {
      return config;
    }
  }
}

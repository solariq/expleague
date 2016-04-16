package com.expleague.server;

import akka.actor.ActorSystem;
import akka.actor.Props;
import com.expleague.server.admin.ExpLeagueAdminService;
import com.expleague.server.agents.LaborExchange;
import com.expleague.server.dao.Archive;
import com.expleague.server.dao.fake.InMemArchive;
import com.expleague.server.dao.fake.InMemRoster;
import com.expleague.util.akka.ActorContainer;
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
    ExpLeagueServer.setConfig(new ExpLeagueServer.ServerCfg(config));

    final ActorSystem system = ActorSystem.create("ExpLeagueAdmin", config);

    system.actorOf(ActorContainer.props(ExpLeagueAdminService.class, config.getConfig("tbts.admin.standalone")), "admin-service");
  }
}

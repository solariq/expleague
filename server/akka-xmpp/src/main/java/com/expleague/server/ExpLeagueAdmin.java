package com.expleague.server;

import akka.actor.ActorSystem;
import com.expleague.server.admin.ExpLeagueAdminService;
import com.expleague.util.akka.ActorAdapter;
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

    system.actorOf(ActorAdapter.props(ExpLeagueAdminService.class, config.getConfig("tbts.admin.standalone")), "admin-service");
  }
}

package com.expleague.server;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * @author vpdelta
 */
public class ExpLeagueServerTestCase {
  public static Config setUpTestConfig() throws Exception {
    final Config config = ConfigFactory.load("application-test.conf");
    ExpLeagueServer.setConfig(new ExpLeagueServer.Cfg(config));
    return config;
  }
}
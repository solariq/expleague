package integration_tests;

import akka.actor.ActorSystem;
import com.expleague.server.ExpLeagueServer;
import com.expleague.server.XMPPServer;
import com.expleague.server.agents.LaborExchange;
import com.expleague.server.agents.XMPP;
import com.expleague.server.services.XMPPServices;
import com.expleague.util.akka.ActorAdapter;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import integration_tests.tests.ClientAdminTest;
import integration_tests.tests.ClientExpertTest;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * User: Artem
 * Date: 22.02.2017
 * Time: 19:51
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({ClientAdminTest.class, ClientExpertTest.class})
public class IntegrationTestsSuite {

  @BeforeClass
  public static void setUpServer() throws Exception {
    final Config load = ConfigFactory.load();
    ExpLeagueServer.setConfig(new ExpLeagueServer.ServerCfg(load));

    final ActorSystem system = ActorSystem.create("ExpLeague", load);
    system.actorOf(ActorAdapter.props(XMPP.class), "xmpp");
    system.actorOf(ActorAdapter.props(LaborExchange.class), "labor-exchange");
    system.actorOf(ActorAdapter.props(XMPPServices.class), "services");
    system.actorOf(ActorAdapter.props(XMPPServer.class), "comm");
  }
}

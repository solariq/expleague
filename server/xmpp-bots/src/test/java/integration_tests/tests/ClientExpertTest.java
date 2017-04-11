package integration_tests.tests;

import akka.actor.ActorSystem;
import com.expleague.bots.AdminBot;
import com.expleague.bots.ClientBot;
import com.expleague.bots.ExpertBot;
import com.expleague.server.ExpLeagueServer;
import com.expleague.server.XMPPServer;
import com.expleague.server.agents.LaborExchange;
import com.expleague.server.agents.XMPP;
import com.expleague.server.services.XMPPServices;
import com.expleague.util.akka.ActorAdapter;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import integration_tests.BaseRoomTest;
import org.junit.BeforeClass;
import org.junit.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;

/**
 * User: Artem
 * Date: 28.02.2017
 * Time: 14:55
 */
public class ClientExpertTest extends BaseRoomTest {
  @Test
  public void testExpertAnswers() throws JaxmppException {
    //Arrange
    final AdminBot adminBot = botsManager.nextAdmin();
    final ClientBot clientBot = botsManager.nextClient();
    final ExpertBot expertBot = botsManager.nextExpert();

    //Act/Assert
    final BareJID roomJID = obtainRoomFeedbackState(testName(), clientBot, adminBot, expertBot);
    roomCloseStateByClientCancel(roomJID, clientBot, adminBot);
  }

  /*@Test
  public void testExpertCancels() throws JaxmppException {
    //Arrange
    final BareJID roomJID = obtainRoomDeliverState();
    final ExpectedMessage expectedCancel = new ExpectedMessageBuilder().from(botRoomJID(roomJID, expertBot)).has(Operations.Cancel.class).build();

    //Act
    expertBot.sendGroupchat(roomJID, new Operations.Cancel());
    final ExpectedMessage[] notReceivedMessages = clientBot.tryReceiveMessages(new StateLatch(), expectedCancel);
    roomCloseStateByClientCancel(roomJID); //TODO:remove

    //Assert
    assertAllExpectedMessagesAreReceived(notReceivedMessages);
  }*/
}

package integration_tests.tests;

import com.expleague.bots.AdminBot;
import com.expleague.bots.ClientBot;
import com.expleague.bots.ExpertBot;
import integration_tests.BaseRoomTest;
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
    final AdminBot adminBot = botsManager.startNewAdmin();
    final ClientBot clientBot = botsManager.startNewClient();
    final ExpertBot expertBot = botsManager.startNewExpert();

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

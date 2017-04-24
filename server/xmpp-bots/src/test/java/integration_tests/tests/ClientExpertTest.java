package integration_tests.tests;

import com.expleague.bots.AdminBot;
import com.expleague.bots.ClientBot;
import com.expleague.bots.ExpertBot;
import com.expleague.bots.utils.ReceivingMessage;
import com.expleague.bots.utils.ReceivingMessageBuilder;
import com.expleague.model.Operations;
import com.spbsu.commons.util.sync.StateLatch;
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
    final AdminBot adminBot = botsManager.nextAdmin();
    final ClientBot clientBot = botsManager.nextClient();
    final ExpertBot expertBot = botsManager.nextExpert();

    //Act/Assert
    obtainRoomFeedbackState(testName(), clientBot, adminBot, expertBot);
  }

  @Test
  public void testExpertCancels() throws JaxmppException { // expert is able to cancel in progress state only
    //Arrange
    final AdminBot adminBot = botsManager.nextAdmin();
    final ClientBot clientBot = botsManager.nextClient();
    final ExpertBot expertBot = botsManager.nextExpert();

    final BareJID roomJID = obtainRoomProgressState(testName(), clientBot, adminBot, expertBot);
    final ReceivingMessage expectedCancel = new ReceivingMessageBuilder().from(botRoomJID(roomJID, expertBot)).has(Operations.Cancel.class).build();

    //Act
    expertBot.sendGroupchat(roomJID, new Operations.Cancel());

    //Assert
    assertThereAreNoFailedMessages(clientBot.tryReceiveMessages(new StateLatch(), expectedCancel));
  }
}

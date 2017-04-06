package integration_tests.tests;

import integration_tests.BaseSingleBotsTest;
import org.junit.Test;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;

/**
 * User: Artem
 * Date: 28.02.2017
 * Time: 14:55
 */
public class ClientExpertTest extends BaseSingleBotsTest {

  @Test
  public void testExpertAnswers() throws JaxmppException {
    //Arrange
    botsManager.addBots(1, 1, 1);
    botsManager.startAll();

    //Act/Assert
    obtainRoomFeedbackState(botsManager.defaultClientBot(), botsManager.defaultAdminBot(), botsManager.defaultExpertBot());
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

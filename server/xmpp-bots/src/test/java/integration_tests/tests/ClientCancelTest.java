package integration_tests.tests;

import com.expleague.bots.AdminBot;
import com.expleague.bots.ClientBot;
import com.expleague.bots.ExpertBot;
import com.expleague.bots.utils.ExpectedMessage;
import com.expleague.bots.utils.ExpectedMessageBuilder;
import com.expleague.model.Answer;
import com.expleague.model.Offer;
import com.expleague.model.Operations;
import com.expleague.model.RoomState;
import com.expleague.xmpp.stanza.Message;
import com.spbsu.commons.util.sync.StateLatch;
import integration_tests.BaseSingleBotsTest;
import org.junit.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;

/**
 * User: Artem
 * Date: 04.04.2017
 * Time: 16:18
 */
public class ClientCancelTest extends BaseSingleBotsTest {

  @Test
  public void testClientCancelsAfterOrderAdminOff() throws JaxmppException {
    //Arrange
    botsManager.addBots(1, 1, 0);
    final AdminBot adminBot = botsManager.defaultAdminBot();
    final ClientBot clientBot = botsManager.defaultClientBot();
    clientBot.start();

    final BareJID roomJID = obtainRoomOpenState(clientBot);
    clientBot.sendGroupchat(roomJID, new Operations.Cancel());
    final ExpectedMessage roomInfo = new ExpectedMessageBuilder()
        .from(groupChatJID(roomJID))
        .has(Offer.class)
        .has(Operations.RoomStateChanged.class, rsc -> RoomState.CLOSED == rsc.state())
        .build();

    //Act
    adminBot.start();
    final ExpectedMessage[] notReceivedMessages = adminBot.tryReceiveMessages(new StateLatch(), roomInfo);

    //Assert
    assertAllExpectedMessagesAreReceived(notReceivedMessages);
  }

  @Test
  public void testClientCancelsAfterOrderAdminOn() throws JaxmppException {
    //Arrange
    botsManager.addBots(1, 1, 0);
    botsManager.startAll();
    final ClientBot clientBot = botsManager.defaultClientBot();
    final AdminBot adminBot = botsManager.defaultAdminBot();
    final BareJID roomJID = obtainRoomOpenState(clientBot, adminBot);

    //Act/Assert
    checkAdminHandlesCancel(roomJID, clientBot, adminBot);
  }

  @Test
  public void testClientCancelsAfterAdminMessage() throws JaxmppException {
    //Arrange
    botsManager.addBots(1, 1, 0);
    botsManager.startAll();
    final ClientBot clientBot = botsManager.defaultClientBot();
    final AdminBot adminBot = botsManager.defaultAdminBot();

    final BareJID roomJID = obtainRoomOpenState(clientBot, adminBot);
    final Message.Body body = new Message.Body(generateRandomString());
    final ExpectedMessage message = new ExpectedMessageBuilder().from(botRoomJID(roomJID, adminBot)).has(Message.Body.class, b -> body.value().equals(b.value())).build();

    //Act
    adminBot.sendGroupchat(roomJID, body);
    final ExpectedMessage[] notReceivedMessagesByClient = clientBot.tryReceiveMessages(new StateLatch(), message);
    //Assert
    assertAllExpectedMessagesAreReceived(notReceivedMessagesByClient);

    //Act/Assert
    checkAdminHandlesCancel(roomJID, clientBot, adminBot);
  }

  @Test
  public void testClientCancelsAfterShortAnswer() throws JaxmppException {
    //Arrange
    botsManager.addBots(1, 1, 0);
    botsManager.startAll();
    final ClientBot clientBot = botsManager.defaultClientBot();
    final AdminBot adminBot = botsManager.defaultAdminBot();

    final BareJID roomJID = obtainRoomOpenState(clientBot, adminBot);
    final Answer answer = new Answer(generateRandomString());
    final ExpectedMessage expectedAnswer = new ExpectedMessageBuilder().from(botRoomJID(roomJID, adminBot)).has(Answer.class, a -> answer.value().equals(a.value())).build();

    //Act
    adminBot.sendGroupchat(roomJID, answer);
    final ExpectedMessage[] notReceivedMessagesByClient = clientBot.tryReceiveMessages(new StateLatch(), expectedAnswer);
    //Assert
    assertAllExpectedMessagesAreReceived(notReceivedMessagesByClient);

    //Act/Assert
    checkAdminHandlesCancel(roomJID, clientBot, adminBot);
  }

  @Test
  public void testClientCancelsInWorkState() throws JaxmppException {
    //Arrange
    botsManager.addBots(1, 1, 1);
    botsManager.startAll();
    final ClientBot clientBot = botsManager.defaultClientBot();
    final AdminBot adminBot = botsManager.defaultAdminBot();
    final ExpertBot expertBot = botsManager.defaultExpertBot();
    final BareJID roomJID = obtainRoomWorkState(clientBot, adminBot, expertBot);

    //Act/Assert
    checkAdminHandlesCancel(roomJID, clientBot, adminBot);
  }

  @Test
  public void testClientCancelsInDeliverState() throws JaxmppException {
    //Arrange
    botsManager.addBots(1, 1, 1);
    botsManager.startAll();
    final ClientBot clientBot = botsManager.defaultClientBot();
    final AdminBot adminBot = botsManager.defaultAdminBot();
    final ExpertBot expertBot = botsManager.defaultExpertBot();
    final BareJID roomJID = obtainRoomDeliverState(clientBot, adminBot, expertBot);

    //Act/Assert
    checkAdminAndExpertHandleCancel(roomJID, clientBot, adminBot, expertBot);
  }

  @Test
  public void testClientCancelsAfterAnswer() throws JaxmppException {
    //Arrange
    botsManager.addBots(1, 1, 1);
    botsManager.startAll();
    final ClientBot clientBot = botsManager.defaultClientBot();
    final AdminBot adminBot = botsManager.defaultAdminBot();
    final ExpertBot expertBot = botsManager.defaultExpertBot();
    final BareJID roomJID = obtainRoomFeedbackState(clientBot, adminBot, expertBot);

    //Act/Assert
    checkAdminAndExpertHandleCancel(roomJID, clientBot, adminBot, expertBot);
  }

  private void checkAdminAndExpertHandleCancel(BareJID roomJID, ClientBot clientBot, AdminBot adminBot, ExpertBot expertBot) throws JaxmppException {
    //Arrange
    final ExpectedMessage roomStateChanged = new ExpectedMessageBuilder().from(groupChatJID(roomJID)).has(Operations.RoomStateChanged.class, rsc -> RoomState.CLOSED == rsc.state()).build();
    final ExpectedMessage cancel = new ExpectedMessageBuilder().from(botRoomJID(roomJID, clientBot)).has(Operations.Cancel.class).build();

    //Act
    clientBot.sendGroupchat(roomJID, new Operations.Cancel());
    final ExpectedMessage[] notReceivedMessagesByAdmin = adminBot.tryReceiveMessages(new StateLatch(), roomStateChanged);
    final ExpectedMessage[] notReceivedMessagesByExpert = expertBot.tryReceiveMessages(new StateLatch(), cancel);

    //Assert
    assertAllExpectedMessagesAreReceived(notReceivedMessagesByAdmin);
    assertAllExpectedMessagesAreReceived(notReceivedMessagesByExpert);
  }

  private void checkAdminHandlesCancel(BareJID roomJID, ClientBot clientBot, AdminBot adminBot) throws JaxmppException {
    //Arrange
    final ExpectedMessage roomStateChanged = new ExpectedMessageBuilder().from(groupChatJID(roomJID)).has(Operations.RoomStateChanged.class, rsc -> RoomState.CLOSED == rsc.state()).build();

    //Act
    clientBot.sendGroupchat(roomJID, new Operations.Cancel());
    final ExpectedMessage[] notReceivedMessages = adminBot.tryReceiveMessages(new StateLatch(), roomStateChanged);

    //Assert
    assertAllExpectedMessagesAreReceived(notReceivedMessages);
  }
}

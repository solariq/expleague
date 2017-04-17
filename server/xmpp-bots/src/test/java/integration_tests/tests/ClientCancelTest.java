package integration_tests.tests;

import com.expleague.bots.AdminBot;
import com.expleague.bots.ClientBot;
import com.expleague.bots.ExpertBot;
import com.expleague.bots.utils.ReceivingMessage;
import com.expleague.bots.utils.ReceivingMessageBuilder;
import com.expleague.model.Answer;
import com.expleague.model.Offer;
import com.expleague.model.Operations;
import com.expleague.model.RoomState;
import com.expleague.xmpp.stanza.Message;
import com.spbsu.commons.util.sync.StateLatch;
import integration_tests.BaseRoomTest;
import org.junit.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;

/**
 * User: Artem
 * Date: 04.04.2017
 * Time: 16:18
 */
public class ClientCancelTest extends BaseRoomTest {

  @Test
  public void testClientCancelsAfterOrderAdminOff() throws JaxmppException {
    //Arrange
    final ClientBot clientBot = botsManager.nextClient();
    final BareJID roomJID = obtainRoomOpenState(testName(), clientBot);
    clientBot.sendGroupchat(roomJID, new Operations.Cancel());
    final ReceivingMessage roomInfo = new ReceivingMessageBuilder()
        .from(groupChatJID(roomJID))
        .has(Offer.class)
        .has(Operations.RoomStateChanged.class, rsc -> RoomState.CLOSED == rsc.state())
        .build();

    //Act
    final AdminBot adminBot = botsManager.nextAdmin();
    final ReceivingMessage[] notReceivedMessages = adminBot.tryReceiveMessages(new StateLatch(), roomInfo);

    //Assert
    assertThereAreNoFailedMessages(notReceivedMessages);
  }

  @Test
  public void testClientCancelsAfterOrderAdminOn() throws JaxmppException {
    //Arrange
    final AdminBot adminBot = botsManager.nextAdmin();
    final ClientBot clientBot = botsManager.nextClient();
    final BareJID roomJID = obtainRoomOpenState(testName(), clientBot, adminBot);

    //Act/Assert
    checkAdminHandlesCancel(roomJID, clientBot, adminBot);
  }

  @Test
  public void testClientCancelsAfterAdminMessage() throws JaxmppException {
    //Arrange
    final AdminBot adminBot = botsManager.nextAdmin();
    final ClientBot clientBot = botsManager.nextClient();

    final BareJID roomJID = obtainRoomOpenState(testName(), clientBot, adminBot);
    final Message.Body body = new Message.Body(generateRandomString());
    final ReceivingMessage message = new ReceivingMessageBuilder().from(botRoomJID(roomJID, adminBot)).has(Message.Body.class, b -> body.value().equals(b.value())).build();

    //Act
    adminBot.sendGroupchat(roomJID, body);
    final ReceivingMessage[] notReceivedMessagesByClient = clientBot.tryReceiveMessages(new StateLatch(), message);
    //Assert
    assertThereAreNoFailedMessages(notReceivedMessagesByClient);

    //Act/Assert
    checkAdminHandlesCancel(roomJID, clientBot, adminBot);
  }

  @Test
  public void testClientCancelsAfterShortAnswer() throws JaxmppException {
    //Arrange
    final AdminBot adminBot = botsManager.nextAdmin();
    final ClientBot clientBot = botsManager.nextClient();

    final BareJID roomJID = obtainRoomOpenState(testName(), clientBot, adminBot);
    final Answer answer = new Answer(generateRandomString());
    final ReceivingMessage expectedAnswer = new ReceivingMessageBuilder().from(botRoomJID(roomJID, adminBot)).has(Answer.class, a -> answer.value().equals(a.value())).build();

    //Act
    adminBot.sendGroupchat(roomJID, answer);
    final ReceivingMessage[] notReceivedMessagesByClient = clientBot.tryReceiveMessages(new StateLatch(), expectedAnswer);
    //Assert
    assertThereAreNoFailedMessages(notReceivedMessagesByClient);

    //Act/Assert
    checkAdminHandlesCancel(roomJID, clientBot, adminBot);
  }

  @Test
  public void testClientCancelsInWorkState() throws JaxmppException {
    //Arrange
    final AdminBot adminBot = botsManager.nextAdmin();
    final ExpertBot expertBot = botsManager.nextExpert();
    final ClientBot clientBot = botsManager.nextClient();
    final BareJID roomJID = obtainRoomWorkState(testName(), clientBot, adminBot, expertBot);

    //Act/Assert
    checkAdminHandlesCancel(roomJID, clientBot, adminBot);
  }

  @Test
  public void testClientCancelsInDeliverState() throws JaxmppException {
    //Arrange
    final AdminBot adminBot = botsManager.nextAdmin();
    final ExpertBot expertBot = botsManager.nextExpert();
    final ClientBot clientBot = botsManager.nextClient();
    final BareJID roomJID = obtainRoomDeliverState(testName(), clientBot, adminBot, expertBot);

    //Act/Assert
    checkAdminAndExpertHandleCancel(roomJID, clientBot, adminBot, expertBot);
  }

  @Test
  public void testClientCancelsAfterAnswer() throws JaxmppException {
    //Arrange
    final AdminBot adminBot = botsManager.nextAdmin();
    final ExpertBot expertBot = botsManager.nextExpert();
    final ClientBot clientBot = botsManager.nextClient();
    final BareJID roomJID = obtainRoomFeedbackState(testName(), clientBot, adminBot, expertBot);

    //Act/Assert
    checkAdminAndExpertHandleCancel(roomJID, clientBot, adminBot, expertBot);
  }

  private void checkAdminAndExpertHandleCancel(BareJID roomJID, ClientBot clientBot, AdminBot adminBot, ExpertBot expertBot) throws JaxmppException {
    //Arrange
    final ReceivingMessage roomStateChanged = new ReceivingMessageBuilder().from(groupChatJID(roomJID)).has(Operations.RoomStateChanged.class, rsc -> RoomState.CLOSED == rsc.state()).build();
    final ReceivingMessage cancel = new ReceivingMessageBuilder().from(botRoomJID(roomJID, clientBot)).has(Operations.Cancel.class).build();

    //Act
    clientBot.sendGroupchat(roomJID, new Operations.Cancel());
    final ReceivingMessage[] notReceivedMessagesByAdmin = adminBot.tryReceiveMessages(new StateLatch(), roomStateChanged);
    final ReceivingMessage[] notReceivedMessagesByExpert = expertBot.tryReceiveMessages(new StateLatch(), cancel);

    //Assert
    assertThereAreNoFailedMessages(notReceivedMessagesByAdmin);
    assertThereAreNoFailedMessages(notReceivedMessagesByExpert);
  }

  private void checkAdminHandlesCancel(BareJID roomJID, ClientBot clientBot, AdminBot adminBot) throws JaxmppException {
    //Arrange
    final ReceivingMessage roomStateChanged = new ReceivingMessageBuilder().from(groupChatJID(roomJID)).has(Operations.RoomStateChanged.class, rsc -> RoomState.CLOSED == rsc.state()).build();

    //Act
    clientBot.sendGroupchat(roomJID, new Operations.Cancel());
    final ReceivingMessage[] notReceivedMessages = adminBot.tryReceiveMessages(new StateLatch(), roomStateChanged);

    //Assert
    assertThereAreNoFailedMessages(notReceivedMessages);
  }
}

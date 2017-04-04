package integration_tests.tests;

import com.expleague.bots.utils.ExpectedMessage;
import com.expleague.bots.utils.ExpectedMessageBuilder;
import com.expleague.model.Answer;
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
  public void testClientCancelsAfterOrder() throws JaxmppException {
    //Arrange
    final BareJID roomJID = obtainRoomOpenState();

    //Act/Assert
    checkAdminHandlesCancel(roomJID);
  }

  @Test
  public void testClientCancelsAfterAdminMessage() throws JaxmppException {
    //Arrange
    final BareJID roomJID = obtainRoomOpenState();
    final Message.Body body = new Message.Body(generateRandomString());
    final ExpectedMessage message = new ExpectedMessageBuilder().from(botRoomJID(roomJID, adminBot)).has(Message.Body.class, b -> body.value().equals(b.value())).build();

    //Act
    adminBot.sendGroupchat(roomJID, body);
    final ExpectedMessage[] notReceivedMessagesByClient = clientBot.tryReceiveMessages(new StateLatch(), message);
    //Assert
    assertAllExpectedMessagesAreReceived(notReceivedMessagesByClient);

    //Act/Assert
    checkAdminHandlesCancel(roomJID);
  }

  @Test
  public void testClientCancelsAfterShortAnswer() throws JaxmppException {
    //Arrange
    final BareJID roomJID = obtainRoomOpenState();
    final Answer answer = new Answer(generateRandomString());
    final ExpectedMessage expectedAnswer = new ExpectedMessageBuilder().from(botRoomJID(roomJID, adminBot)).has(Answer.class, a -> answer.value().equals(a.value())).build();

    //Act
    adminBot.sendGroupchat(roomJID, answer);
    final ExpectedMessage[] notReceivedMessagesByClient = clientBot.tryReceiveMessages(new StateLatch(), expectedAnswer);
    //Assert
    assertAllExpectedMessagesAreReceived(notReceivedMessagesByClient);

    //Act/Assert
    checkAdminHandlesCancel(roomJID);
  }

  @Test
  public void testClientCancelsInWorkState() throws JaxmppException {
    //Arrange
    final BareJID roomJID = obtainRoomWorkState();

    //Act/Assert
    checkAdminHandlesCancel(roomJID);
  }

  @Test
  public void testClientCancelsInDeliverState() throws JaxmppException {
    //Arrange
    final BareJID roomJID = obtainRoomDeliverState();

    //Act/Assert
    checkAdminAndExpertHandleCancel(roomJID);
  }

  @Test
  public void testClientCancelsAfterAnswer() throws JaxmppException {
    //Arrange
    final BareJID roomJID = obtainRoomFeedbackState();

    //Act/Assert
    checkAdminAndExpertHandleCancel(roomJID);
  }

  private void checkAdminAndExpertHandleCancel(BareJID roomJID) throws JaxmppException {
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

  private void checkAdminHandlesCancel(BareJID roomJID) throws JaxmppException {
    //Arrange
    final ExpectedMessage roomStateChanged = new ExpectedMessageBuilder().from(groupChatJID(roomJID)).has(Operations.RoomStateChanged.class, rsc -> RoomState.CLOSED == rsc.state()).build();

    //Act
    clientBot.sendGroupchat(roomJID, new Operations.Cancel());
    final ExpectedMessage[] notReceivedMessages = adminBot.tryReceiveMessages(new StateLatch(), roomStateChanged);

    //Assert
    assertAllExpectedMessagesAreReceived(notReceivedMessages);
  }
}

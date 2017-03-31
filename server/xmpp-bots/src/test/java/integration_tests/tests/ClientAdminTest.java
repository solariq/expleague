package integration_tests.tests;

import com.expleague.bots.utils.ExpectedMessage;
import com.expleague.bots.utils.ExpectedMessageBuilder;
import com.expleague.model.Answer;
import com.expleague.xmpp.stanza.Message;
import com.spbsu.commons.util.sync.StateLatch;
import integration_tests.BaseSingleBotsTest;
import org.junit.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;

import java.util.function.Supplier;

import static com.expleague.bots.utils.FunctionalUtils.throwableSupplier;

/**
 * User: Artem
 * Date: 15.02.2017
 * Time: 11:51
 */
public class ClientAdminTest extends BaseSingleBotsTest {

  @Test
  public void testAdminClosesRoom() throws JaxmppException {
    //Arrange
    final BareJID roomJID = obtainRoomOpenState();
    final Answer answer = new Answer(generateRandomString());
    final ExpectedMessage expectedAnswer = new ExpectedMessageBuilder()
        .from(botRoomJID(roomJID, adminBot))
        .has(Answer.class, a -> answer.value().equals(a.value())).build();

    //Act
    adminBot.sendGroupchat(roomJID, answer);
    final ExpectedMessage[] notReceivedMessages = clientBot.tryReceiveMessages(new StateLatch(), expectedAnswer);
    roomCloseStateByClientFeedback(roomJID);

    //Assert
    assertAllExpectedMessagesAreReceived(notReceivedMessages);
  }

  @Test
  public void testClientReceivesMessageInOpenRoomState() throws JaxmppException {
    testClientReceivesMessage(throwableSupplier(this::obtainRoomOpenState), true);
  }

  @Test
  public void testClientReceivesMessageInWorkRoomState() throws JaxmppException {
    testClientReceivesMessage(throwableSupplier(this::obtainRoomWorkState), true);
  }

  @Test
  public void testClientReceivesMessageInCloseRoomState() throws JaxmppException {
    testClientReceivesMessage(throwableSupplier(() -> {
      final BareJID roomJID = obtainRoomWorkState();
      roomCloseStateByClientCancel(roomJID);
      return roomJID;
    }), false);
  }

  private void testClientReceivesMessage(Supplier<BareJID> obtainState, boolean closeRoom) throws JaxmppException {
    //Arrange
    final BareJID roomJID = obtainState.get();
    final Message.Body body = new Message.Body(generateRandomString());
    final ExpectedMessage expectedMessageFromAdmin = new ExpectedMessageBuilder()
        .from(botRoomJID(roomJID, adminBot))
        .has(Message.Body.class, b -> body.value().equals(b.value())).build();

    //Act
    adminBot.sendGroupchat(roomJID, body);
    final ExpectedMessage[] notReceivedMessages = clientBot.tryReceiveMessages(new StateLatch(), expectedMessageFromAdmin);
    if (closeRoom) {
      roomCloseStateByClientCancel(roomJID);
    }

    //Assert
    assertAllExpectedMessagesAreReceived(notReceivedMessages);
  }
}
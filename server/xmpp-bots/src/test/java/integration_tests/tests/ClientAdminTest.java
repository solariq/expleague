package integration_tests.tests;

import com.expleague.bots.utils.ExpectedMessage;
import com.spbsu.commons.util.sync.StateLatch;
import integration_tests.BaseSingleBotsTest;
import org.junit.Assert;
import org.junit.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;

import java.util.Collections;
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
    final String answerText = generateRandomString();
    final ExpectedMessage answer = ExpectedMessage.create("answer", answerText, null);
    final BareJID roomJID = obtainRoomOpenState();

    //Act
    clientBot.startReceivingMessages(Collections.singletonList(answer), new StateLatch());
    adminBot.sendAnswer(roomJID, answerText);
    clientBot.waitForMessages();
    roomCloseStateByClientFeedback(roomJID);

    //Assert
    Assert.assertTrue("answer was not received by client", answer.received());
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
    final String messageFromAdminText = generateRandomString();
    final ExpectedMessage messageFromAdmin = ExpectedMessage.create("body", messageFromAdminText, null);
    final BareJID roomJID = obtainState.get();

    //Act
    clientBot.startReceivingMessages(Collections.singletonList(messageFromAdmin), new StateLatch());
    adminBot.sendTextMessageToRoom(messageFromAdminText, roomJID);
    clientBot.waitForMessages();
    if (closeRoom) {
      roomCloseStateByClientCancel(roomJID);
    }

    //Assert
    Assert.assertTrue("message from admin was not received by client", messageFromAdmin.received());
  }
}
package integration_tests.tests;

import com.expleague.bots.utils.ExpectedMessage;
import com.expleague.bots.utils.ExpectedMessageBuilder;
import com.expleague.model.Answer;
import com.expleague.model.Operations;
import com.spbsu.commons.util.sync.StateLatch;
import integration_tests.BaseSingleBotsTest;
import org.junit.Test;
import tigase.jaxmpp.core.client.BareJID;
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
    final Answer answer = new Answer(generateRandomString());
    final BareJID roomJID = obtainRoomDeliverState();
    final ExpectedMessage expectedAnswer = new ExpectedMessageBuilder().from(botRoomJID(roomJID, expertBot)).has(Answer.class, a -> answer.value().equals(a.value())).build();

    //Act
    expertBot.sendToGroupChat(roomJID, answer);
    final ExpectedMessage[] notReceivedMessages = clientBot.tryReceiveMessages(new StateLatch(), expectedAnswer);
    roomCloseStateByClientCancel(roomJID);

    //Assert
    assertAllExpectedMessagesAreReceived(notReceivedMessages);
  }

  /*@Test
  public void testExpertCancels() throws JaxmppException {
    //Arrange
    final BareJID roomJID = obtainRoomDeliverState();
    final ExpectedMessage expectedCancel = new ExpectedMessageBuilder().has(Operations.Cancel.class).build();

    //Act
    expertBot.sendToGroupChat(roomJID, new Operations.Cancel());
    final ExpectedMessage[] notReceivedMessages = clientBot.tryReceiveMessages(new StateLatch(), expectedCancel);
    roomCloseStateByClientCancel(roomJID);

    //Assert
    assertAllExpectedMessagesAreReceived(notReceivedMessages);
  }*/
}

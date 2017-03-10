package integration_tests.tests;

import com.expleague.bots.utils.ExpectedMessage;
import com.spbsu.commons.util.sync.StateLatch;
import integration_tests.BaseSingleBotsTest;
import org.junit.Assert;
import org.junit.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;

import java.util.Collections;

/**
 * User: Artem
 * Date: 28.02.2017
 * Time: 14:55
 */
public class ClientExpertTest extends BaseSingleBotsTest {

  @Test
  public void testExpertAnswers() throws JaxmppException {
    //Arrange
    final String answerText = generateRandomString();
    final ExpectedMessage answer = ExpectedMessage.create("answer", answerText, null);
    final BareJID roomJID = obtainRoomDeliverState();

    //Act
    clientBot.startReceivingMessages(Collections.singletonList(answer), new StateLatch());
    expertBot.sendAnswer(roomJID, answerText);
    clientBot.waitForMessages();
    roomCloseStateByClientCancel(roomJID);

    //Assert
    Assert.assertTrue("answer was not received by client", answer.received());
  }

  @Test
  public void testExpertCancels() throws JaxmppException {
    //Arrange
    final ExpectedMessage cancel = ExpectedMessage.create("cancel", null, null);
    final BareJID roomJID = obtainRoomDeliverState();

    //Act
    clientBot.startReceivingMessages(Collections.singletonList(cancel), new StateLatch());
    expertBot.sendCancel(roomJID);
    clientBot.waitForMessages();
    roomCloseStateByClientCancel(roomJID);

    //Assert
    Assert.assertTrue("cancel was not received by client", cancel.received());
  }
}

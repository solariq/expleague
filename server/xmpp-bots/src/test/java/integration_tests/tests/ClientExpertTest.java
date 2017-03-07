package integration_tests.tests;

import com.spbsu.commons.util.sync.StateLatch;
import integration_tests.BaseSingleBotsTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;

import java.util.Queue;

import static integration_tests.utils.FunctionalUtils.throwablePredicate;

/**
 * User: Artem
 * Date: 28.02.2017
 * Time: 14:55
 */
public class ClientExpertTest extends BaseSingleBotsTest {

  @Before
  public void setUp() throws JaxmppException {
    super.setUpAdmin();
    super.setUpExpert();
    super.setUpClient();
  }

  @After
  public void tearDown() throws JaxmppException {
    super.tearDownAdmin();
    super.tearDownExpert();
    super.tearDownClient();
  }

  @Test
  public void testExpertAnswers() throws JaxmppException {
    //Arrange
    final String topicText = "New topic";
    final String answer = "Answer!";

    //Act
    adminBot.startReceivingMessages(6, new StateLatch());
    final BareJID roomJID = clientBot.startRoom(topicText);
    adminBot.waitAndGetReceivedMessages();

    expertBot.startReceivingMessages(1, new StateLatch());
    adminBot.startWorkState(roomJID);
    final Message offerFromClient = expertBot.waitAndGetReceivedMessage();

    expertBot.startReceivingMessages(1, new StateLatch());
    expertBot.sendOk(offerFromClient);
    final Message invitation = expertBot.waitAndGetReceivedMessage();

    clientBot.startReceivingMessages(4, new StateLatch());
    expertBot.sendStart(roomJID);
    final Queue<Message> clientReceivedMessages = clientBot.waitAndGetReceivedMessages();

    clientBot.startReceivingMessages(1, new StateLatch());
    expertBot.sendAnswer(roomJID, answer);
    final Message answerMessage = clientBot.waitAndGetReceivedMessage();

    //Assert
    Assert.assertNotNull("offer was not received", offerFromClient.getFirstChild("offer"));
    Assert.assertNotNull("invitation offer was not received", invitation.getFirstChild("offer"));
    Assert.assertNotNull("invite was not received", invitation.getFirstChild("invite"));

    Assert.assertEquals("start was not received (2 times)", 2, clientReceivedMessages.stream()
        .filter(throwablePredicate(message -> "start".equals(message.getFirstChild().getName())))
        .count());
    Assert.assertNotNull("expert(tasks=0) was not received", clientReceivedMessages.stream()
        .filter(throwablePredicate(message -> "expert".equals(message.getFirstChild().getName()) && "0".equals(message.getFirstChild().getAttribute("tasks"))))
        .findAny()
        .orElse(null));
    Assert.assertNotNull("expert(tasks=1) was not received", clientReceivedMessages.stream()
        .filter(throwablePredicate(message -> "expert".equals(message.getFirstChild().getName()) && "1".equals(message.getFirstChild().getAttribute("tasks"))))
        .findAny()
        .orElse(null));

    Assert.assertEquals("answer was not received", answer, answerMessage.getFirstChild("answer").getValue());
  }
}

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
 * Date: 15.02.2017
 * Time: 11:51
 */
public class ClientAdminTest extends BaseSingleBotsTest {

  @Before
  public void setUp() throws JaxmppException {
    super.setUpAdmin();
    super.setUpClient();
  }

  @After
  public void tearDown() throws JaxmppException {
    super.tearDownAdmin();
    super.tearDownClient();
  }

  @Test
  public void testClientAdminDialogue() throws JaxmppException {
    //Arrange
    final String topicText = "New topic";
    final String messageFromAdminText = "Message from admin";
    final int receivedByAdminMsgNum = 6;

    //Act
    adminBot.startReceivingMessages(receivedByAdminMsgNum, new StateLatch());
    final BareJID roomJID = clientBot.startRoom(topicText);
    final Queue<Message> initMessages = adminBot.waitAndGetReceivedMessages();

    clientBot.startReceivingMessages(1, new StateLatch());
    adminBot.sendTextMessageToGroupChat(messageFromAdminText, roomJID);
    final Message messageFromAdmin = clientBot.waitAndGetReceivedMessage();

    //Assert
    Assert.assertNotNull("room-role-update(none) was not received", initMessages.stream()
        .filter(throwablePredicate(message -> "room-role-update".equals(message.getFirstChild().getName()) && "none".equals(message.getFirstChild().getAttribute("role"))))
        .findAny()
        .orElse(null));
    Assert.assertNotNull("room-role-update(moderator) was not received", initMessages.stream()
        .filter(throwablePredicate(message -> "room-role-update".equals(message.getFirstChild().getName()) && "moderator".equals(message.getFirstChild().getAttribute("role"))))
        .findAny()
        .orElse(null));
    Assert.assertNotNull("room-state-changed(0) was not received", initMessages.stream()
        .filter(throwablePredicate(message -> "room-state-changed".equals(message.getFirstChild().getName()) && "0".equals(message.getFirstChild().getAttribute("state"))))
        .findAny()
        .orElse(null));
    Assert.assertNotNull("offer was not received", initMessages.stream()
        .filter(throwablePredicate(message -> "offer".equals(message.getFirstChild().getName()) && topicText.equals(message.getFirstChild().getFirstChild("topic").getValue())))
        .findAny()
        .orElse(null));
    Assert.assertNotNull("room-message-received(expert=false) was not received", initMessages.stream()
        .filter(throwablePredicate(message -> "room-message-received".equals(message.getFirstChild().getName()) && "false".equals(message.getFirstChild().getAttribute("expert"))))
        .findAny()
        .orElse(null));
    Assert.assertNotNull("room-message-received(expert=true) was not received", initMessages.stream()
        .filter(throwablePredicate(message -> "room-message-received".equals(message.getFirstChild().getName()) && "true".equals(message.getFirstChild().getAttribute("expert"))))
        .findAny()
        .orElse(null));
    Assert.assertEquals(messageFromAdminText, messageFromAdmin.getFirstChild("body").getValue());
  }
}
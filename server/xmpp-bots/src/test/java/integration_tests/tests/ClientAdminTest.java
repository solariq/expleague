package integration_tests.tests;

import com.spbsu.commons.util.sync.StateLatch;
import integration_tests.BaseSingleBotsTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;

import java.util.Arrays;
import java.util.Queue;

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
    final int receivedByAdminMsgNum = 4;

    //Act
    adminBot.startReceivingMessages(receivedByAdminMsgNum, new StateLatch());
    final BareJID roomJID = clientBot.startRoom(topicText);
    final Queue<Message> initMessages = adminBot.getReceivedMessages();

    clientBot.startReceivingMessages(1, new StateLatch());
    adminBot.sendTextMessageToGroupChat(messageFromAdminText, roomJID);
    final Message messageFromAdmin = clientBot.waitAndGetReceivedMessage();

    //Assert
    final boolean[] messagesChecker = new boolean[receivedByAdminMsgNum];
    for (int i = 0; i < receivedByAdminMsgNum; i++) {
      final Message message = initMessages.poll();
      try {
        final Element firstChild = message.getFirstChild();
        if ("room-role-update".equals(firstChild.getName())) {
          if ("none".equals(firstChild.getAttribute("role"))) {
            messagesChecker[i] = true;
          } else if ("moderator".equals(firstChild.getAttribute("role"))) {
            messagesChecker[i] = true;
          }
        } else if ("room-state-changed".equals(firstChild.getName())) {
          Assert.assertEquals("0", firstChild.getAttribute("state"));
          messagesChecker[i] = true;
        } else if ("offer".equals(firstChild.getName())) {
          Assert.assertEquals(topicText, firstChild.getFirstChild("topic").getValue());
          messagesChecker[i] = true;
        } else {
          Assert.fail("Unexpected message is received");
        }
      } catch (XMLException e) {
        throw new RuntimeException(e);
      }
    }

    final boolean[] expectedChecker = new boolean[receivedByAdminMsgNum];
    Arrays.fill(expectedChecker, true);
    Assert.assertArrayEquals(expectedChecker, messagesChecker);
    Assert.assertEquals(messageFromAdminText, messageFromAdmin.getFirstChild("body").getValue());
  }
}
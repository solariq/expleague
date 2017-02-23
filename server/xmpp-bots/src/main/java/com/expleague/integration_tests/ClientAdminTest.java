package com.expleague.integration_tests;

import com.expleague.bots.AdminBot;
import com.expleague.bots.ClientBot;
import com.spbsu.commons.util.sync.StateLatch;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;

import java.util.Queue;

/**
 * User: Artem
 * Date: 15.02.2017
 * Time: 11:51
 */
public class ClientAdminTest {
  private ClientBot clientBot;
  private AdminBot adminBot;

  @Before
  public void setUp() throws JaxmppException {
    clientBot = new ClientBot(BareJID.bareJIDInstance("client-bot-1", "localhost"), "poassord");
    clientBot.start();
    clientBot.online();

    adminBot = new AdminBot(BareJID.bareJIDInstance("expert-bot-1", "localhost"), "poassord");
    adminBot.start();
    adminBot.online();
  }

  @After
  public void tearDown() throws JaxmppException {
    clientBot.offline();
    clientBot.stop();

    adminBot.offline();
    adminBot.stop();
  }

  @Test
  public void testClientAdminInteraction() throws JaxmppException {
    //Arrange
    final String topicText = "New topic";
    final String messageFromAdminText = "Message from admin";

    //Act
    adminBot.startReceivingMessages(new StateLatch());
    final BareJID roomJID = clientBot.startRoom(topicText);
    final Queue<Message> initMessages = adminBot.getReceivedMessages(3);

    clientBot.startReceivingMessages(new StateLatch());
    adminBot.sendToGroupChat(messageFromAdminText, roomJID);
    final Message messageFromAdmin = clientBot.getReceivedMessage();

    //Assert
    final boolean[] messagesChecker = new boolean[3];
    initMessages.forEach(message -> {
      try {
        final Element firstChild = message.getFirstChild();
        if ("room-role-update".equals(firstChild.getName())) {
          Assert.assertEquals("moderator", firstChild.getAttribute("role"));
          messagesChecker[0] = true;
        } else if ("room-state-changed".equals(firstChild.getName())) {
          Assert.assertEquals("0", firstChild.getAttribute("state"));
          messagesChecker[1] = true;
        } else if ("offer".equals(firstChild.getName())) {
          Assert.assertEquals(topicText, firstChild.getFirstChild("topic").getValue());
          messagesChecker[2] = true;
        } else {
          Assert.fail("Unexpected message is received");
        }
      } catch (XMLException e) {
        throw new RuntimeException(e);
      }
    });
    final boolean[] expectedChecker = new boolean[] {true, true, true};
    Assert.assertArrayEquals(expectedChecker, messagesChecker);
    Assert.assertEquals(messageFromAdminText, messageFromAdmin.getFirstChild("body").getValue());
  }
}
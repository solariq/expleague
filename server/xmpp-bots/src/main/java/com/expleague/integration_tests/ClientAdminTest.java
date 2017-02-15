package com.expleague.integration_tests;

import com.expleague.bots.AdminBot;
import com.expleague.bots.ClientBot;
import org.junit.*;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;

import java.util.List;

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
    clientBot.stop();
    adminBot.stop();
  }

  @Test
  public void testClientAdminInteraction() throws JaxmppException {
    //Arrange
    final String topicText = "New topic";
    final String messageFromAdminText = "Message from admin";
    final BareJID roomJID = clientBot.startRoom(topicText);

    //Act
    final List<Message> initMessages = adminBot.receiveMessages(2);
    adminBot.sendToGroupChat(messageFromAdminText, roomJID);
    final Message messageFromAdmin = clientBot.receiveMessage();

    //Assert
    Assert.assertEquals("moderator", initMessages.get(0).getFirstChild("room-role-update").getAttribute("role"));
    Assert.assertEquals(topicText, initMessages.get(1).getFirstChild("offer").getFirstChild("topic").getValue());
    Assert.assertEquals(messageFromAdminText, messageFromAdmin.getFirstChild("body").getValue());
  }
}
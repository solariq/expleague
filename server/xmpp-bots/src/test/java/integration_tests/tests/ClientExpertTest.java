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
  public void testExpertReceivesInvitation() throws JaxmppException {
    //Arrange
    final String topicText = "New topic";

    //Act
    adminBot.startReceivingMessages(4, new StateLatch());
    final BareJID roomJID = clientBot.startRoom(topicText);
    adminBot.getReceivedMessages();

    expertBot.startReceivingMessages(1, new StateLatch());
    adminBot.startWorkState(roomJID);
    final Message offerFromClient = expertBot.waitAndGetReceivedMessage();

    expertBot.startReceivingMessages(1, new StateLatch());
    expertBot.sendOk(offerFromClient);
    final Message invitation = expertBot.waitAndGetReceivedMessage();

    //Assert
    Assert.assertNotNull(offerFromClient.getFirstChild("offer"));
    Assert.assertNotNull(invitation.getFirstChild("offer"));
    Assert.assertNotNull(invitation.getFirstChild("invite"));
  }
}

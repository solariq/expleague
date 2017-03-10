package integration_tests;

import com.expleague.bots.AdminBot;
import com.expleague.bots.ClientBot;
import com.expleague.bots.ExpertBot;
import com.expleague.bots.utils.ExpectedMessage;
import com.spbsu.commons.util.Pair;
import com.spbsu.commons.util.sync.StateLatch;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

/**
 * User: Artem
 * Date: 28.02.2017
 * Time: 15:05
 */
public class BaseSingleBotsTest {
  protected ClientBot clientBot;
  protected AdminBot adminBot;
  protected ExpertBot expertBot;

  @Before
  public void setUp() throws JaxmppException {
    setUpAdmin();
    setUpExpert();
    setUpClient();
  }

  @After
  public void tearDown() throws JaxmppException {
    tearDownAdmin();
    tearDownExpert();
    tearDownClient();
  }

  private void setUpClient() throws JaxmppException {
    clientBot = new ClientBot(BareJID.bareJIDInstance("client-bot-1", "localhost"), "poassord");
    clientBot.start();
    clientBot.online();
  }

  private void setUpAdmin() throws JaxmppException {
    adminBot = new AdminBot(BareJID.bareJIDInstance("admin-bot-1", "localhost"), "poassord");
    adminBot.start();
    adminBot.online();
  }

  private void setUpExpert() throws JaxmppException {
    expertBot = new ExpertBot(BareJID.bareJIDInstance("expert-bot-1", "localhost"), "poassord");
    expertBot.start();
    expertBot.online();
  }

  private void tearDownClient() throws JaxmppException {
    clientBot.offline();
    clientBot.stop();
  }

  private void tearDownAdmin() throws JaxmppException {
    adminBot.offline();
    adminBot.stop();
  }

  private void tearDownExpert() throws JaxmppException {
    expertBot.offline();
    expertBot.stop();
  }

  protected String generateRandomString() {
    return UUID.randomUUID().toString();
  }

  protected void roomCloseStateByClientCancel(BareJID roomJID) throws JaxmppException {
    //Arrange
    final ExpectedMessage cancel = ExpectedMessage.create("cancel", null, null);
    final ExpectedMessage roomStateChanged = ExpectedMessage.create("room-state-changed", null, Collections.singletonList(new Pair<>("state", "8")));

    //Act
    adminBot.startReceivingMessages(Arrays.asList(cancel, roomStateChanged), new StateLatch());
    clientBot.sendCancel(roomJID);
    adminBot.waitForMessages();

    //Assert
    Assert.assertTrue("cancel was not received by admin", cancel.received());
    Assert.assertTrue("room-state-changed(8) was not received by admin", roomStateChanged.received());
  }

  protected BareJID obtainRoomOpenState() throws JaxmppException {
    final String topicText = generateRandomString();
    return openRoom(topicText);
  }

  protected BareJID obtainRoomWorkState() throws JaxmppException {
    final BareJID roomJID = obtainRoomOpenState();
    roomWorkState(roomJID);
    return roomJID;
  }

  protected BareJID obtainRoomDeliverState() throws JaxmppException {
    final BareJID roomJID = obtainRoomWorkState();
    roomDeliverState(roomJID);
    return roomJID;
  }

  private BareJID openRoom(String topicText) throws JaxmppException {
    //Arrange
    final ExpectedMessage roomRoleUpdateNone = ExpectedMessage.create("room-role-update", null, Collections.singletonList(new Pair<>("role", "none")));
    final ExpectedMessage roomRoleUpdateModer = ExpectedMessage.create("room-role-update", null, Collections.singletonList(new Pair<>("role", "moderator")));
    final ExpectedMessage roomStateChanged = ExpectedMessage.create("room-state-changed", null, Collections.singletonList(new Pair<>("state", "0")));
    final ExpectedMessage offer = ExpectedMessage.create(new String[]{"message", "offer", "topic"}, topicText, null);

    //Act
    adminBot.startReceivingMessages(Arrays.asList(roomRoleUpdateNone, roomRoleUpdateModer, roomStateChanged, offer), new StateLatch());
    final BareJID roomJID = clientBot.startRoom(topicText);
    adminBot.waitForMessages();

    //Assert
    Assert.assertTrue("room-role-update(none) was not received by admin", roomRoleUpdateNone.received());
    Assert.assertTrue("room-role-update(moderator) was not received by admin", roomRoleUpdateModer.received());
    Assert.assertNotNull("room-state-changed(0) was not received by admin", roomStateChanged.received());
    Assert.assertNotNull("offer was not received by admin", offer.received());

    return roomJID;
  }

  private void roomWorkState(BareJID roomJID) throws JaxmppException {
    //Arrange
    final ExpectedMessage offer = ExpectedMessage.create("offer", null, null);

    //Act
    expertBot.startReceivingMessages(Collections.singletonList(offer), new StateLatch());
    adminBot.startWorkState(roomJID);
    expertBot.waitForMessages();

    //Assert
    Assert.assertTrue("offer was not received by expert", offer.received());
  }

  private void roomDeliverState(BareJID roomJID) throws JaxmppException {
    //Arrange
    final ExpectedMessage invite = ExpectedMessage.create("invite", null, null);
    final ExpectedMessage start = ExpectedMessage.create("start", null, null);
    final ExpectedMessage expert = ExpectedMessage.create("expert", null, null);

    //Act
    expertBot.startReceivingMessages(Collections.singletonList(invite), new StateLatch());
    expertBot.sendOk(roomJID);
    expertBot.waitForMessages();

    clientBot.startReceivingMessages(Arrays.asList(start, expert), new StateLatch());
    expertBot.sendStart(roomJID);
    clientBot.waitForMessages();

    //Assert
    Assert.assertTrue("invite was not received by expert", invite.received());
    Assert.assertTrue("start was not received by client", start.received());
    Assert.assertTrue("expert was not received by client", expert.received());
  }
}

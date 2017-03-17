package integration_tests;

import com.expleague.bots.AdminBot;
import com.expleague.bots.ClientBot;
import com.expleague.bots.ExpertBot;
import com.expleague.bots.utils.ExpectedMessage;
import com.expleague.bots.utils.ExpectedMessageBuilder;
import com.expleague.model.*;
import com.spbsu.commons.util.sync.StateLatch;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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

  protected int generateRandomInt(int min, int max) {
    return ThreadLocalRandom.current().nextInt(min, max + 1);
  }

  protected void AssertAllExpectedMessagesAreReceived(ExpectedMessage[] notReceivedMessages) {
    if (notReceivedMessages.length > 0) {
      final StringBuilder stringBuilder = new StringBuilder();
      for (ExpectedMessage expectedMessage : notReceivedMessages) {
        stringBuilder.append(String.format("%s was/were not received or had incorrect attributes\n", expectedMessage));
      }
      Assert.fail(stringBuilder.toString());
    }
  }

  protected void roomCloseStateByClientCancel(BareJID roomJID) throws JaxmppException {
    //Arrange
    final ExpectedMessage cancel = new ExpectedMessageBuilder().has(Operations.Cancel.class).build();
    final ExpectedMessage roomStateChanged = new ExpectedMessageBuilder()
        .has(Operations.RoomStateChanged.class, rsc -> RoomState.CLOSED.equals(rsc.state()))
        .build();

    //Act
    clientBot.sendCancel(roomJID);
    final ExpectedMessage[] notReceivedMessages = adminBot.tryReceiveMessages(new StateLatch(), cancel, roomStateChanged);

    //Assert
    AssertAllExpectedMessagesAreReceived(notReceivedMessages);
  }

  protected void roomCloseStateByClientFeedback(BareJID roomJID) throws JaxmppException {
    //Arrange
    final Operations.Feedback feedback = new Operations.Feedback(generateRandomInt(1, 5));
    final ExpectedMessage roomStateChanged = new ExpectedMessageBuilder()
        .has(Operations.RoomStateChanged.class, rsc -> RoomState.CLOSED.equals(rsc.state()))
        .build();
    final ExpectedMessage expectedFeedback = new ExpectedMessageBuilder().has(Operations.Feedback.class, f -> feedback.stars() == f.stars()).build();

    //Act
    clientBot.sendFeedback(roomJID, feedback);
    final ExpectedMessage[] notReceivedMessages = adminBot.tryReceiveMessages(new StateLatch(), roomStateChanged, expectedFeedback);

    //Assert
    AssertAllExpectedMessagesAreReceived(notReceivedMessages);
  }

  protected BareJID obtainRoomOpenState() throws JaxmppException {
    //Arrange
    final String topicText = generateRandomString();
    final double started = System.currentTimeMillis() / 1000.;
    final Offer.Urgency urgency = Offer.Urgency.ASAP;
    final Offer.Location location = new Offer.Location(59.98062295379115, 30.32538469883643);
    final String imageUrl = generateRandomString();

    final ExpectedMessage roomRoleUpdateNone = new ExpectedMessageBuilder()
        .has(Operations.RoomRoleUpdate.class, rru -> Role.NONE.equals(rru.role()) && Affiliation.OWNER.equals(rru.affiliation()))
        .build();
    final ExpectedMessage roomRoleUpdateModer = new ExpectedMessageBuilder()
        .has(Operations.RoomRoleUpdate.class, rru -> Role.MODERATOR.equals(rru.role()) && Affiliation.OWNER.equals(rru.affiliation()))
        .build();
    final ExpectedMessage roomStateChanged = new ExpectedMessageBuilder()
        .has(Operations.RoomStateChanged.class, rsc -> RoomState.OPEN.equals(rsc.state()))
        .build();
    final ExpectedMessage offer = new ExpectedMessageBuilder()
        .has(Offer.class, o -> location.longitude() == o.location().longitude()
            && location.latitude() == o.location().latitude()
            && Arrays.stream(o.attachments()).anyMatch(a -> a instanceof Image && imageUrl.equals(((Image) a).url()))
            && topicText.equals(o.topic())
            && Offer.Urgency.ASAP.equals(o.urgency())
            && Double.compare(started, o.started()) == 0
        )
        .has(Operations.OfferChange.class)
        .build();

    //Act
    final BareJID roomJID = clientBot.startRoom(topicText, started, urgency, location, imageUrl);
    final ExpectedMessage[] notReceivedMessages = adminBot.tryReceiveMessages(new StateLatch(), roomRoleUpdateNone, roomRoleUpdateModer, roomStateChanged, offer);

    //Assert
    AssertAllExpectedMessagesAreReceived(notReceivedMessages);

    return roomJID;
  }

  protected BareJID obtainRoomWorkState() throws JaxmppException {
    final BareJID roomJID = obtainRoomOpenState();
    { //obtain work state
      //Arrange
      final ExpectedMessage offerCheck = new ExpectedMessageBuilder().has(Offer.class).has(Operations.Check.class).build();

      //Act
      adminBot.startWorkState(roomJID);
      final ExpectedMessage[] notReceivedMessages = expertBot.tryReceiveMessages(new StateLatch(), offerCheck);

      //Assert
      AssertAllExpectedMessagesAreReceived(notReceivedMessages);
    }
    return roomJID;
  }

  protected BareJID obtainRoomDeliverState() throws JaxmppException {
    final BareJID roomJID = obtainRoomWorkState();
    { //obtain deliver state
      //Arrange
      final ExpectedMessage invite = new ExpectedMessageBuilder().has(Offer.class).has(Operations.Invite.class).build();
      final ExpectedMessage start = new ExpectedMessageBuilder().has(Operations.Start.class).build();
      final ExpectedMessage expert = new ExpectedMessageBuilder().has(ExpertsProfile.class).build();

      //Act
      expertBot.sendOk(roomJID);
      final ExpectedMessage[] notReceivedByExpert = expertBot.tryReceiveMessages(new StateLatch(), invite);

      expertBot.sendStart(roomJID);
      final ExpectedMessage[] notReceivedByClient = clientBot.tryReceiveMessages(new StateLatch(), start, expert);

      //Assert
      AssertAllExpectedMessagesAreReceived(notReceivedByExpert);
      AssertAllExpectedMessagesAreReceived(notReceivedByClient);
    }
    return roomJID;
  }

}

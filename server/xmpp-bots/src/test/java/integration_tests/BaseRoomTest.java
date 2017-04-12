package integration_tests;

import com.expleague.bots.*;
import com.expleague.bots.utils.ReceivingMessage;
import com.expleague.bots.utils.ReceivingMessageBuilder;
import com.expleague.model.*;
import com.expleague.server.ExpLeagueServer;
import com.expleague.server.agents.GlobalChatAgent;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
import com.spbsu.commons.util.Pair;
import com.spbsu.commons.util.sync.StateLatch;
import junit.framework.TestCase;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * User: Artem
 * Date: 28.02.2017
 * Time: 15:05
 */
public class BaseRoomTest extends TestCase {
  protected static BotsManager botsManager = new BotsManager();
  private List<Pair<BareJID, ClientBot>> openRooms = new ArrayList<>();

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
    openRooms.forEach(pair -> {
      try {
        pair.second.online();
        pair.second.sendGroupchat(pair.first, new Operations.Cancel());
        pair.second.tryReceiveMessages(new StateLatch()); //sync
      } catch (JaxmppException e) {
        throw new RuntimeException(e);
      }
    });
    openRooms.clear();
    botsManager.stopAll();
  }

  protected String testName() {
    return getName();
  }

  protected String generateRandomString() {
    return UUID.randomUUID().toString();
  }

  @SuppressWarnings("SameParameterValue")
  protected int generateRandomInt(int min, int max) {
    return ThreadLocalRandom.current().nextInt(min, max + 1);
  }

  protected void assertThereAreNoFailedMessages(ReceivingMessage[] failedMessages) {
    if (failedMessages.length > 0) {
      final StringBuilder stringBuilder = new StringBuilder();
      for (ReceivingMessage receivingMessage : failedMessages) {
        if (receivingMessage.expected()) {
          stringBuilder.append(String.format("\n%s was/were NOT received from %s (or had incorrect attributes)\n", receivingMessage, receivingMessage.from()));
        } else {
          stringBuilder.append(String.format("\n%s was/were unexpectedly received from %s\n", receivingMessage, receivingMessage.from()));
        }
      }
      Assert.fail(stringBuilder.toString());
    }
  }

  protected String domain() {
    return ExpLeagueServer.config().domain();
  }

  protected JID groupChatJID(BareJID roomJID) {
    return new JID(GlobalChatAgent.ID, domain(), roomJID.getLocalpart());
  }

  protected JID domainJID() {
    return JID.parse(domain());
  }

  protected JID botRoomJID(BareJID roomJID, Bot bot) {
    return new JID(roomJID.getLocalpart(), roomJID.getDomain(), bot.jid().getLocalpart());
  }

  protected void roomCloseStateByClientCancel(BareJID roomJID, ClientBot clientBot, AdminBot adminBot) throws JaxmppException {
    //Arrange
    final ReceivingMessage cancel = new ReceivingMessageBuilder().from(botRoomJID(roomJID, clientBot)).has(Operations.Cancel.class).build();
    final ReceivingMessage roomStateChanged = new ReceivingMessageBuilder()
        .from(groupChatJID(roomJID))
        .has(Operations.RoomStateChanged.class, rsc -> RoomState.CLOSED.equals(rsc.state()))
        .build();

    //Act
    clientBot.sendGroupchat(roomJID, new Operations.Cancel());
    final ReceivingMessage[] notReceivedMessages = adminBot.tryReceiveMessages(new StateLatch(), cancel, roomStateChanged);

    //Assert
    assertThereAreNoFailedMessages(notReceivedMessages);
    openRooms.remove(Pair.create(roomJID, clientBot));
  }

  protected void roomCloseStateByClientFeedback(BareJID roomJID, ClientBot clientBot, AdminBot adminBot) throws JaxmppException {
    //Arrange
    final Operations.Feedback feedback = new Operations.Feedback(generateRandomInt(1, 5));
    final ReceivingMessage roomStateChanged = new ReceivingMessageBuilder()
        .from(groupChatJID(roomJID))
        .has(Operations.RoomStateChanged.class, rsc -> RoomState.CLOSED.equals(rsc.state()))
        .build();
    final ReceivingMessage expectedFeedback = new ReceivingMessageBuilder().from(botRoomJID(roomJID, clientBot)).has(Operations.Feedback.class, f -> feedback.stars() == f.stars()).build();

    //Act
    clientBot.sendGroupchat(roomJID, feedback);
    final ReceivingMessage[] notReceivedMessages = adminBot.tryReceiveMessages(new StateLatch(), roomStateChanged, expectedFeedback);

    //Assert
    assertThereAreNoFailedMessages(notReceivedMessages);
    openRooms.remove(Pair.create(roomJID, clientBot));
  }

  protected BareJID obtainRoomOpenState(String testName, ClientBot clientBot) throws JaxmppException {
    //Arrange
    final BareJID roomJID = generateRoomJID(testName, clientBot);
    final Offer offer = new Offer(
        JID.parse(clientBot.jid().toString()),
        generateRandomString(),
        Offer.Urgency.ASAP, new Offer.Location(59.98062295379115, 30.32538469883643),
        System.currentTimeMillis() / 1000.);
    final Message.Body message = new Message.Body(generateRandomString());
    final ReceivingMessage receivingMessage = new ReceivingMessageBuilder().has(Message.Body.class, body -> message.value().equals(body.value())).build();

    //Act
    clientBot.send(roomJID, offer);
    openRooms.add(Pair.create(roomJID, clientBot));
    clientBot.sendGroupchat(clientBot.jid(), message);
    clientBot.tryReceiveMessages(new StateLatch(), receivingMessage);

    return roomJID;
  }

  protected BareJID obtainRoomOpenState(String testName, ClientBot clientBot, AdminBot adminBot) throws JaxmppException {
    //Arrange
    final BareJID roomJID = generateRoomJID(testName, clientBot);
    final Offer offer = new Offer(
        JID.parse(clientBot.jid().toString()),
        generateRandomString(),
        Offer.Urgency.ASAP, new Offer.Location(59.98062295379115, 30.32538469883643),
        System.currentTimeMillis() / 1000.);
    final Image image = new Image(generateRandomString());
    offer.attach(image);

    final ReceivingMessageBuilder roomRoleUpdateNone = new ReceivingMessageBuilder()
        .has(Operations.RoomRoleUpdate.class, rru -> Role.NONE.equals(rru.role()) && Affiliation.OWNER.equals(rru.affiliation()));
    final ReceivingMessageBuilder roomRoleUpdateModer = new ReceivingMessageBuilder()
        .has(Operations.RoomRoleUpdate.class, rru -> Role.MODERATOR.equals(rru.role()) && Affiliation.OWNER.equals(rru.affiliation()));
    final ReceivingMessageBuilder roomStateChanged = new ReceivingMessageBuilder()
        .has(Operations.RoomStateChanged.class, rsc -> RoomState.OPEN.equals(rsc.state()));
    final ReceivingMessageBuilder expectedOffer = new ReceivingMessageBuilder()
        .has(Offer.class, o -> o.location() != null
            && offer.location().longitude() == o.location().longitude()
            && offer.location().latitude() == o.location().latitude()
            && Arrays.stream(o.attachments()).anyMatch(a -> a instanceof Image && image.url().equals(((Image) a).url()))
            && offer.topic().equals(o.topic())
            && Offer.Urgency.ASAP.equals(o.urgency())
            && Double.compare(offer.started(), o.started()) == 0
        )
        .has(Operations.OfferChange.class);

    //Act
    clientBot.send(roomJID, offer);
    openRooms.add(Pair.create(roomJID, clientBot));
    final ReceivingMessage[] notReceivedMessages = adminBot.tryReceiveMessages(new StateLatch(),
        roomRoleUpdateNone.from(groupChatJID(roomJID)).build(),
        roomRoleUpdateModer.from(groupChatJID(roomJID)).build(),
        roomStateChanged.from(groupChatJID(roomJID)).build(),
        expectedOffer.from(groupChatJID(roomJID)).build());

    //Assert
    assertThereAreNoFailedMessages(notReceivedMessages);
    return roomJID;
  }

  private BareJID generateRoomJID(String testName, ClientBot clientBot) {
    return BareJID.bareJIDInstance(testName + "-" + (System.nanoTime() / 1_000_000), "muc." + clientBot.jid().getDomain());
  }

  protected BareJID obtainRoomWorkState(String testName, ClientBot clientBot, AdminBot adminBot, ExpertBot... expertBots) throws JaxmppException {
    final BareJID roomJID = obtainRoomOpenState(testName, clientBot, adminBot);
    { //obtain work state
      //Arrange
      final Offer offer = new Offer(JID.parse(roomJID.toString()));
      final ReceivingMessage offerCheck = new ReceivingMessageBuilder().from(domainJID()).has(Offer.class).has(Operations.Check.class).build();
      final ReceivingMessage roomWorkState = new ReceivingMessageBuilder().from(groupChatJID(roomJID)).has(Operations.RoomStateChanged.class, rsc -> RoomState.WORK == rsc.state()).build();

      //Act
      adminBot.send(roomJID, offer);
      openRooms.add(Pair.create(roomJID, clientBot));
      final ReceivingMessage[] notReceivedMessages = adminBot.tryReceiveMessages(new StateLatch(), roomWorkState);
      //Assert
      assertThereAreNoFailedMessages(notReceivedMessages);

      for (ExpertBot expertBot : expertBots) {
        //Act
        final ReceivingMessage[] notReceivedOfferCheck = expertBot.tryReceiveMessages(new StateLatch(), offerCheck.copy());
        //Assert
        assertThereAreNoFailedMessages(notReceivedOfferCheck);
      }
    }
    return roomJID;
  }

  protected BareJID obtainRoomDeliverState(String testName, ClientBot clientBot, AdminBot adminBot, ExpertBot expertBot) throws JaxmppException {
    final BareJID roomJID = obtainRoomWorkState(testName, clientBot, adminBot, expertBot);
    { //obtain deliver state
      //Arrange
      final ReceivingMessage invite = new ReceivingMessageBuilder().from(roomJID).has(Offer.class).has(Operations.Invite.class).build();
      final ReceivingMessage startAndExpert = new ReceivingMessageBuilder().from(roomJID).has(Operations.Start.class).has(ExpertsProfile.class).build();

      //Act
      expertBot.sendGroupchat(roomJID, new Operations.Ok());
      final ReceivingMessage[] notReceivedInvite = expertBot.tryReceiveMessages(new StateLatch(), invite);
      //Assert
      assertThereAreNoFailedMessages(notReceivedInvite);

      //Act
      expertBot.sendGroupchat(roomJID, new Operations.Start());
      final ReceivingMessage[] notReceivedStart = clientBot.tryReceiveMessages(new StateLatch(), startAndExpert);
      //Assert
      assertThereAreNoFailedMessages(notReceivedStart);
      expertBot.sendGroupchat(roomJID, new Answer("Hello world!"));
    }
    return roomJID;
  }

  protected BareJID obtainRoomFeedbackState(String testName, ClientBot clientBot, AdminBot adminBot, ExpertBot expertBot) throws JaxmppException {
    final BareJID roomJID = obtainRoomDeliverState(testName, clientBot, adminBot, expertBot);
    { //obtain feedback state
      //Arrange
      final Answer answer = new Answer(generateRandomString());
      final ReceivingMessage expectedAnswer = new ReceivingMessageBuilder().from(botRoomJID(roomJID, expertBot)).has(Answer.class, a -> answer.value().equals(a.value())).build();

      //Act
      expertBot.sendGroupchat(roomJID, answer);
      final ReceivingMessage[] notReceivedMessages = clientBot.tryReceiveMessages(new StateLatch(), expectedAnswer);

      //Assert
      assertThereAreNoFailedMessages(notReceivedMessages);
    }
    return roomJID;
  }
}

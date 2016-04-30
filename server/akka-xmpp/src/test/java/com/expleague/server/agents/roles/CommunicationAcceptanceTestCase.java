package com.expleague.server.agents.roles;

import akka.actor.ActorRef;
import com.expleague.model.Answer;
import com.expleague.model.Offer;
import com.expleague.model.Operations;
import com.expleague.server.Roster;
import com.expleague.server.XMPPDevice;
import com.expleague.server.agents.ActorSystemTestCase;
import com.expleague.server.agents.LaborExchange;
import com.expleague.server.agents.XMPP;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.util.akka.MessageCaptureTestCase;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.control.register.RegisterQuery;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Presence;
import com.expleague.xmpp.stanza.Stanza;
import com.spbsu.commons.util.Factories;
import com.spbsu.commons.util.Pair;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * @author vpdelta
 */
@SuppressWarnings("unused")
public abstract class CommunicationAcceptanceTestCase extends ActorSystemTestCase {
  private static final Logger log = Logger.getLogger(CommunicationAcceptanceTestCase.class.getName());

  private MessageCaptureImpl messageCapture;

  public static class GoOnline {
  }

  public static class GoOffline {
  }

  public static class SendQuery {
    private final JID room;
    private final String text;

    public SendQuery(final JID room, final String text) {
      this.room = room;
      this.text = text;
    }

    public JID getRoom() {
      return room;
    }

    public String getText() {
      return text;
    }
  }

  public static class CancelQuery {
    private final JID room;

    public CancelQuery(final JID room) {
      this.room = room;
    }

    public JID getRoom() {
      return room;
    }
  }

  public static class SendQueryDone {
    private final JID room;

    public SendQueryDone(final JID room) {
      this.room = room;
    }

    public JID getRoom() {
      return room;
    }
  }

  public static class AcceptOffer {
  }

  public static class ResumeOffer {
  }

  public static class RejectOffer {
  }

  public static class SuspendOffer {
    private final long delayMs;

    public SuspendOffer(final long delayMs) {
      this.delayMs = delayMs;
    }

    public long getDelayMs() {
      return delayMs;
    }
  }

  public static class SendAnswer {
    private final JID room;
    private final String answer;

    public SendAnswer(final JID room, final String answer) {
      this.room = room;
      this.answer = answer;
    }

    public JID getRoom() {
      return room;
    }

    public String getAnswer() {
      return answer;
    }
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    messageCapture = new MessageCaptureImpl();
    MessageCaptureTestCase.setUpMessageCapture(messageCapture);
  }

  public class ScenarioTestKit extends TestKit {
    public XMPPDevice registerDevice(final JID jid) throws Exception {
      final RegisterQuery query = new RegisterQuery();
      query.username(jid.toString());
      query.name(jid.toString());
      query.passwd(jid.toString());
      return Roster.instance().register(query);
    }

    public Room registerRoom(final String prefix) {
      final JID roomJid = JID.parse("testroom-" + prefix + "-" + UUID.randomUUID() + "@muc.localhost");
      final ActorRef roomRef = register(roomJid);
      return new Room(roomJid, roomRef);
    }

    public Client registerClient(final String name) throws Exception {
      final JID jid = JID.parse(name + "@expleague.com");
      registerDevice(jid);
      final ActorRef actorRef = registerMock(jid, new ClientActor(jid));
      return new Client(jid, actorRef);
    }

    public Expert registerExpert(final String name) throws Exception {
      final JID jid = JID.parse(name + "@expleague.com/expert");
      registerDevice(jid);
      final ActorRef actorRef = registerMock(jid, new ExpertActor(jid));
      return new Expert(jid, actorRef);
    }

    public class Room {
      protected final JID jid;
      protected final ActorRef actorRef;

      public Room(final JID jid, final ActorRef actorRef) {
        this.jid = jid;
        this.actorRef = actorRef;
      }

      public JID getJid() {
        return jid;
      }

      public ActorRef getActorRef() {
        return actorRef;
      }
    }

    public class User {
      protected final JID jid;
      protected final ActorRef actorRef;

      public User(final JID jid, final ActorRef actorRef) {
        this.jid = jid;
        this.actorRef = actorRef;
      }

      public JID getJid() {
        return jid;
      }

      public ActorRef getActorRef() {
        return actorRef;
      }

      public void goOnline() {
        actorRef.tell(new GoOnline(), getRef());
      }

      public void goOffline() {
        actorRef.tell(new GoOffline(), getRef());
      }
    }

    public class Client extends User {
      public Client(final JID jid, final ActorRef actorRef) {
        super(jid, actorRef);
      }

      public Room query(final String text) {
        final Room room = registerRoom(text);
        actorRef.tell(new SendQuery(room.getJid(), text), getRef());
        return room;
      }

      public void cancel(final Room room) {
        actorRef.tell(new CancelQuery(room.getJid()), getRef());
      }

      public void done(final Room room) {
        actorRef.tell(new SendQueryDone(room.getJid()), getRef());
      }

      public void receiveStart(final Expert expert) throws Exception {
        messageCapture.expect("Start not received from " + expert, 10000,
          records -> records.stream()
            .filter(toActor(actorRef))
            .flatMap(messages())
            .filter(message -> message.has(Operations.Start.class))
            .count() >= 1
        );
      }

      public void receiveAnswer(final Expert expert, final String answer) throws Exception {
        messageCapture.expect("Answer not received from " + expert, 10000,
          records -> records.stream()
            .filter(toActor(actorRef))
            .flatMap(messages())
            .flatMap(items(Answer.class))
            .filter(a -> a.value().equals(answer))
            .count() == 1
        );
      }
    }

    public class Expert extends User {
      public Expert(final JID jid, final ActorRef actorRef) {
        super(jid, actorRef);
      }

      public void acceptOffer(final Offer offer) throws Exception {
        messageCapture.expect("Expert " + jid + " doesn't received invite", 10000,
          records -> records.stream()
            .flatMap(messages())
            .filter(message -> message.to().bareEq(jid))
            .filter(message -> message.has(Operations.Invite.class))
            .count() >= 1
        );
        actorRef.tell(new AcceptOffer(), getRef());
        final JID room = offer.room();
        messageCapture.expect("Room " + room + " doesn't received start", 10000,
          records -> records.stream()
            .flatMap(messages())
            .filter(message -> message.to().equals(room))
            .filter(message -> message.has(Operations.Start.class))
            .count() >= 1
        );
      }

      public void resumeOffer(final Offer offer) throws Exception {
        messageCapture.expect("Expert " + jid + " doesn't received resume", 10000,
          records -> records.stream()
            .flatMap(messages())
            .filter(message -> message.to().bareEq(jid))
            .filter(message -> message.has(Operations.Resume.class))
            .count() >= 1
        );
        actorRef.tell(new ResumeOffer(), getRef());
        final JID room = offer.room();
        messageCapture.expect("Room " + room + " doesn't received resume", 10000,
          records -> records.stream()
            .flatMap(messages())
            .filter(message -> message.to().equals(room))
            .filter(message -> message.has(Operations.Resume.class))
            .count() >= 1
        );
      }

      public void rejectOffer(final Offer offer) throws Exception {
        actorRef.tell(new RejectOffer(), getRef());
        messageCapture.expect("Room doesn't received start", 10000,
          records -> records.stream()
            .flatMap(messages())
            .filter(message -> message.to().equals(offer.room()))
            .filter(message -> message.has(Operations.Cancel.class))
            .count() >= 1
        );
      }

      public void suspendOffer(final Offer offer, final int delayMs) throws Exception {
        actorRef.tell(new SuspendOffer(delayMs), getRef());
        messageCapture.expect("Room doesn't received suspend", 10000,
          records -> records.stream()
            .flatMap(messages())
            .filter(message -> message.to().equals(offer.room()))
            .filter(message -> message.has(Operations.Suspend.class))
            .count() >= 1
        );
      }

      public void sendAnswer(final JID room, final String answer) throws Exception {
        actorRef.tell(new SendAnswer(room, answer), getRef());
      }

      public void sendAnswer(final Offer offer, final String answer) throws Exception {
        sendAnswer(offer.room(), answer);
      }

      protected Offer receiveOffer(final String topic) throws Exception {
        return receiveOffer(offer -> offer.topic().equals(topic));
      }

      protected Offer receiveOffer(final Offer... exclude) throws Exception {
        final Set<Offer> offers = Factories.hashSet(exclude);
        return receiveOffer(offer -> !offers.contains(offer));
      }

      protected Offer receiveOffer() throws Exception {
        return receiveOffer(o -> true);
      }

      protected Offer receiveOffer(final Predicate<Offer> offerFilter) throws Exception {
        final List<Offer> offers = messageCapture.expectAndGet("Offer not received", 10000,
          records -> records.stream()
            .filter(toActor(actorRef))
            .flatMap(messages())
            .filter(message -> message.has(Operations.Invite.class))
            .flatMap(offers())
            .filter(offerFilter)
            .collect(Collectors.toList())
        );
        return offers.get(offers.size() - 1);
      }

      protected void passCheck() throws Exception {
        messageCapture.expect("Check not passed", 10000,
          records -> records.stream()
            .flatMap(messages())
            .filter(message -> message.from().equals(jid))
            .filter(message -> message.has(Operations.Ok.class))
            .count() >= 1
        );
      }
    }
  }

  public class ExpertsGroup {
    private final ScenarioTestKit.Expert[] experts;

    public ExpertsGroup(final ScenarioTestKit.Expert[] experts) {
      this.experts = experts;
    }

    protected Pair<ScenarioTestKit.Expert, Offer> receiveOffer(final Predicate<Offer> offerFilter) throws Exception {
      final Map<ActorRef, ScenarioTestKit.Expert> ref2expert = new HashMap<>();
      for (ScenarioTestKit.Expert expert : experts) {
        ref2expert.put(expert.getActorRef(), expert);
      }

      final List<Pair<ScenarioTestKit.Expert, Offer>> candidates = messageCapture.expectAndGet("Offer not received", 10000,
        records -> records.stream()
          .filter(messageCaptureRecord -> ref2expert.containsKey(messageCaptureRecord.getTo()))
          .filter(messageCaptureRecord -> messageCaptureRecord.getMessage() instanceof Message)
          .filter(messageCaptureRecord -> ((Message) messageCaptureRecord.getMessage()).has(Operations.Invite.class))
          .filter(messageCaptureRecord -> ((Message) messageCaptureRecord.getMessage()).has(Offer.class))
          .filter(messageCaptureRecord -> offerFilter.test(((Message) messageCaptureRecord.getMessage()).get(Offer.class)))
          .map(messageCaptureRecord -> Pair.create(
            ref2expert.get(messageCaptureRecord.getTo()),
            ((Message) messageCaptureRecord.getMessage()).get(Offer.class)
          ))
          .collect(Collectors.toList())
      );

      return candidates.get(candidates.size() - 1);
    }
  }

  public class UserActor extends ActorAdapter {
    protected final JID jid;
    protected boolean isOnline;

    public UserActor(final JID jid) {
      this.jid = jid;
    }

    @ActorMethod
    public void tellPresence(GoOnline p) {
      isOnline = true;
      send(new Presence(jid, true));
    }

    @ActorMethod
    public void tellPresence(GoOffline p) {
      send(new Presence(jid, false));
    }

    public void send(final Stanza stanza) {
      stanza.from(jid);
      XMPP.send(stanza, context());
    }
  }

  public class ClientActor extends UserActor {
    public ClientActor(final JID jid) {
      super(jid);
    }

    @ActorMethod
    public void tellQuery(SendQuery sendQuery) {
      assertTrue(isOnline);
      final Offer offer = new Offer();
      offer.topic(sendQuery.getText());
      send(new Message(
        sendQuery.getRoom(),
        Message.MessageType.GROUP_CHAT,
        offer
      ));
    }

    @ActorMethod
    public void tellCancel(CancelQuery cancelQuery) {
      assertTrue(isOnline);
      send(new Message(
        cancelQuery.getRoom(),
        Message.MessageType.GROUP_CHAT,
        new Operations.Cancel()
      ));
    }

    @ActorMethod
    public void tellDone(SendQueryDone sendQueryDone) {
      assertTrue(isOnline);
      send(new Message(
        sendQueryDone.getRoom(),
        Message.MessageType.GROUP_CHAT,
        new Operations.Done()
      ));
    }
  }

  public class ExpertActor extends UserActor {
    public ExpertActor(final JID jid) {
      super(jid);
    }

    public void send(final Stanza stanza) {
      super.send(stanza);
      LaborExchange.Experts.tellTo(jid, stanza, self(), context());
    }

    @ActorMethod
    public void tellAnswer(SendAnswer sendAnswer) {
      assertTrue(isOnline);
      send(new Message(sendAnswer.getRoom(), Message.MessageType.GROUP_CHAT, new Answer(sendAnswer.getAnswer())));
    }

    @ActorMethod
    public void onOffer(AcceptOffer acceptOffer) {
      assertTrue(isOnline);
      log.finest("AcceptOffer with " + jid);
      send(new Message(jid, new Operations.Start()));
    }

    @ActorMethod
    public void onOffer(ResumeOffer resumeOffer) {
      assertTrue(isOnline);
      log.finest("ResumeOffer with " + jid);
      send(new Message(jid, new Operations.Resume()));
    }

    @ActorMethod
    public void onOffer(RejectOffer rejectOffer) {
      assertTrue(isOnline);
      log.finest("RejectOffer with " + jid);
      send(new Message(jid, new Operations.Cancel()));
    }

    @ActorMethod
    public void onOffer(SuspendOffer suspendOffer) {
      assertTrue(isOnline);
      log.finest("SuspendOffer with " + jid);
      final long startMs = System.currentTimeMillis();
      final Operations.Suspend suspend = new Operations.Suspend(
        startMs,
        startMs + suspendOffer.getDelayMs()
      );
      send(new Message(jid, suspend));
    }

    @ActorMethod
    public void receive(Message message) {
      if (isOnline) {
        if (message.has(Offer.class) && !message.has(Operations.Command.class)) {
          send(new Message(jid, new Operations.Ok()));
        }
      }
    }
  }

  public static Predicate<MessageCaptureRecord> toActor(final ActorRef actorRef) {
    return messageCaptureRecord -> messageCaptureRecord.getTo().path().equals(actorRef.path());
  }

  public static Function<MessageCaptureRecord, Stream<Message>> messages() {
    return messageCaptureRecord -> messageCaptureRecord.getMessage() instanceof Message
      ? Stream.of((Message) messageCaptureRecord.getMessage())
      : Stream.empty();
  }

  public static Function<Message, Stream<Offer>> offers() {
    return items(Offer.class);
  }

  public static <T> Function<Message, Stream<T>> items(final Class<T> cls) {
    return m -> m.has(cls) ? Stream.of(m.get(cls)) : Stream.empty();
  }
}
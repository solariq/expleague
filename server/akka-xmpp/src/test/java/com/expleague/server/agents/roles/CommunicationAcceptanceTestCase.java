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

import java.util.logging.Logger;

/**
 * @author vpdelta
 */
@SuppressWarnings("unused")
public abstract class CommunicationAcceptanceTestCase extends ActorSystemTestCase {
  private static final Logger log = Logger.getLogger(CommunicationAcceptanceTestCase.class.getName());

  private MessageCaptureImpl messageCapture;

  public static class GoOnline {}
  public static class GoOffline {}

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

  public static class AcceptOffer {}

  public static class RejectOffer {}

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
      return Roster.instance().register(query);
    }

    public Room registerRoom() {
      final JID roomJid = JID.parse("testroom" + System.currentTimeMillis() + "@muc.localhost");
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
        final Room room = registerRoom();
        actorRef.tell(new SendQuery(room.getJid(), text), getRef());
        return room;
      }

      public void receiveStart(final Expert expert) throws Exception {
        messageCapture.expect("Start not received from " + expert, 10000,
            records -> records.stream()
                .filter(messageCaptureRecord -> messageCaptureRecord.getTo().path().equals(actorRef.path()))
                .filter(messageCaptureRecord -> messageCaptureRecord.getMessage() instanceof Message)
                .filter(messageCaptureRecord -> ((Message) messageCaptureRecord.getMessage()).has(Operations.Start.class))
                .count() == 1
        );
        messageCapture.reset();
      }

      public void receiveAnswer(final Expert expert, final String answer) throws Exception {
        messageCapture.expect("Answer not received from " + expert, 10000,
            records -> records.stream()
                .filter(messageCaptureRecord -> messageCaptureRecord.getTo().path().equals(actorRef.path()))
                .filter(messageCaptureRecord -> messageCaptureRecord.getMessage() instanceof Message)
                .map(messageCaptureRecord -> (Message) messageCaptureRecord.getMessage())
                .filter(message -> message.has(Answer.class))
                .filter(message -> message.get(Answer.class).value().equals(answer))
                .count() == 1
        );
        messageCapture.reset();
      }
    }

    public class Expert extends User {
      public Expert(final JID jid, final ActorRef actorRef) {
        super(jid, actorRef);
      }

      public void acceptOffer(final Room room, final String offer) throws Exception {
        receiveOffer(room, offer);
        actorRef.tell(new AcceptOffer(), getRef());
      }

      public void rejectOffer(final Room room, final String offer) throws Exception {
        receiveOffer(room, offer);
        actorRef.tell(new RejectOffer(), getRef());
      }

      public void sendAnswer(final Room room, final String answer) throws Exception {
        actorRef.tell(new SendAnswer(room.getJid(), answer), getRef());
      }

      protected void receiveOffer(final Room room, final String offer) throws Exception {
        messageCapture.expect("Offer not received from " + room, 30000,
            records -> records.stream()
                .filter(messageCaptureRecord -> messageCaptureRecord.getTo().path().equals(actorRef.path()))
                .filter(messageCaptureRecord -> messageCaptureRecord.getMessage() instanceof Message)
                .map(messageCaptureRecord -> (Message) messageCaptureRecord.getMessage())
                .filter(message -> message.has(Offer.class))
                .filter(message -> message.get(Offer.class).topic().equals(offer))
                .count() >= 1
        );
        messageCapture.reset();
      }
    }
  }

  public class UserActor extends ActorAdapter {
    protected final JID jid;

    public UserActor(final JID jid) {
      this.jid = jid;
    }

    @ActorMethod
    public void tellPresence(GoOnline p) {
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
      send(new Message(
        sendQuery.getRoom(),
        Message.MessageType.GROUP_CHAT,
        new Message.Subject( // todo: get rid of json
          "{\"specific\":true,\"started\":1457627722.72493,\"local\":false,\"location\":{\"longitude\":-122.406417,\"latitude\":37.785834},\"topic\":\"" + sendQuery.getText() + "\",\"attachments\":\"\",\"urgency\":\"day\"}"
        )
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
      send(new Message(sendAnswer.getRoom(), Message.MessageType.GROUP_CHAT, new Answer(sendAnswer.getAnswer())));
    }

    @ActorMethod
    public void onOffer(AcceptOffer acceptOffer) {
      log.finest("AcceptOffer with " + jid);
      send(new Message(jid, new Operations.Start()));
    }

    @ActorMethod
    public void onOffer(RejectOffer rejectOffer) {
      log.finest("RejectOffer with " + jid);
      send(new Message(jid, new Operations.Cancel()));
    }

    @ActorMethod
    public void receive(Message message) {
      if (message.has(Offer.class) && !message.has(Operations.Command.class)) {
        send(new Message(jid, new Operations.Ok()));
      }
    }
  }
}
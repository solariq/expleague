package com.expleague.server.agents;

import akka.actor.ActorContext;
import com.expleague.model.*;
import com.expleague.model.Operations.*;
import com.expleague.server.XMPPDevice;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Message.MessageType;
import com.expleague.xmpp.stanza.Stanza;

import java.util.*;
import java.util.stream.Collectors;

import static com.expleague.model.RoomState.*;

/**
 * User: solar
 * Date: 16.12.15
 * Time: 13:18
 */
@SuppressWarnings("UnusedParameters")
public class GlobalChatAgent extends RoomAgent {
  public static final String ID = "global-chat";
  public Map<String, RoomStatus> rooms = new HashMap<>();

  public GlobalChatAgent(JID jid) {
    super(jid, XMPP.jid());
  }

  public static boolean isTrusted(JID from) {
    final XMPPDevice device = XMPPDevice.fromJid(from);
    return device != null && device.role() == XMPPDevice.Role.ADMIN;
  }

  @ActorMethod
  public void dump(DumpRequest dump) {
    final List<Message> rooms = this.rooms.values().stream().filter(room -> room.affiliation(dump.from()) == Affiliation.OWNER).map(RoomStatus::message).collect(Collectors.toList());
    sender().tell(rooms, self());
  }

  @Override
  protected Role suggestRole(JID who, Affiliation affiliation) {
    if (isRoom(who))
      return Role.PARTICIPANT;
    else if (isTrusted(who))
      return Role.MODERATOR;
    return Role.NONE;
  }

  @Override
  public Role role(JID jid) {
    if (isRoom(jid) || isTrusted(jid))
      return Role.PARTICIPANT;
    return super.role(jid);
  }

  @Override
  protected void process(Message msg, ProcessMode mode) {
    if (!isRoom(msg.from())) {
      super.process(msg, mode);
      return;
    }
    final RoomStatus status = rooms.compute(msg.from().local(), (local, s) -> s != null ? s : new RoomStatus(local));
    status.ts(msg.ts());
    final int changes = status.changes();
    if (msg.has(OfferChange.class))
      status.offer(msg.get(Offer.class), msg.get(OfferChange.class).by());
    if (msg.has(RoomStateChanged.class))
      status.state(msg.get(RoomStateChanged.class).state());
    if (msg.has(Feedback.class))
      status.feedback(msg.get(Feedback.class).stars());
    if (msg.has(RoomRoleUpdate.class)) {
      final RoomRoleUpdate update = msg.get(RoomRoleUpdate.class);
      status.affiliation(update.expert().local(), update.affiliation());
    }
    if (msg.has(RoomMessageReceived.class)) {
      final RoomMessageReceived received = msg.get(RoomMessageReceived.class);
      status.message(received.expert(), received.count());
    }
    if (changes < status.changes())
      super.process(msg, mode);
  }

  @Override
  public List<Stanza> archive() {
    final List<Stanza> result = new ArrayList<>();
    rooms.forEach((id, status) -> {
      if (status.currentOffer != null)
        result.add(status.message());
    });
    return result;
  }

  @Override
  protected void onStart() {
    rooms.forEach((room, state) -> {
      if (EnumSet.of(OPEN, CHAT, RESPONSE, WORK).contains(state.state))
        XMPP.whisper(XMPP.muc(room), new RoomAgent.Awake(), context()); // wake up room
    });
    super.onStart();
  }

  public static void tell(JID from, Item item, ActorContext context) {
    XMPP.send(new Message(from, XMPP.jid(ID), MessageType.GROUP_CHAT, item), context);
  }

  public static void tell(JID from, Stanza item, ActorContext context) {
    XMPP.send(item, context);
  }

  @Override
  protected boolean relevant(Stanza msg, JID to) {
    return !isRoom(to) && super.relevant(msg, to); // rooms must not see what happens at global chat
  }

  private boolean isRoom(JID from) {
    return from.domain().startsWith("muc.");
  }

  private static class RoomStatus {
    private String id;
    private Offer currentOffer;
    private RoomState state = OPEN;
    private Map<String, Affiliation> affiliations = new HashMap<>();
    private Map<String, Role> roles = new HashMap<>();
    private long modificationTs = -1;
    private int changes = 0;
    private int unread = 0;
    private int feedback = 0;

    public RoomStatus(String id) {
      this.id = id;
    }

    public void affiliation(String id, Affiliation affiliation) {
      final Affiliation a = affiliations.getOrDefault(id, Affiliation.NONE);
      if (affiliation != null && affiliation.priority() < a.priority()) {
        affiliations.put(id, affiliation);
        changes++;
      }
    }

    public void role(String id, Role role) {
      roles.put(id, role);
      changes++;
    }

    public void ts(long ts) {
      modificationTs = Math.max(ts, modificationTs);
    }

    public int changes() {
      return changes;
    }

    public void offer(Offer offer, JID by) {
      currentOffer = offer;
      changes++;
    }

    public void state(RoomState state) {
      this.state = state;
      if (state == CLOSED)
        unread = 0;
      changes++;
    }

    public void message(boolean expert, int count) {
      this.unread = expert ? 0 : this.unread + count;
      changes++;
    }

    public Message message() {
      final Message result = new Message("global-" + id + "-" + (modificationTs/1000));
      result.type(MessageType.GROUP_CHAT);
      result.append(currentOffer);
      result.append(new RoomStateChanged(state));
      result.append(new RoomMessageReceived(unread));
      result.from(XMPP.muc(id));
      if (feedback > 0)
        result.append(new Feedback(feedback));

      final Set<String> ids = new HashSet<>(roles.keySet());
      ids.addAll(affiliations.keySet());
      for (final String nick: ids) {
        final Role role = roles.getOrDefault(nick, Role.NONE);
        final Affiliation affiliation = affiliations.getOrDefault(nick, Affiliation.NONE);
        final RoomRoleUpdate update = new RoomRoleUpdate(XMPP.jid(nick), role, affiliation);
        result.append(update);
      }
      return result;
    }

    public void feedback(int stars) {
      this.feedback = stars;
    }

    public Affiliation affiliation(String fromId) {
      return affiliations.getOrDefault(fromId, Affiliation.NONE);
    }
  }
}

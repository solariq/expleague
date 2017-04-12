package com.expleague.server.agents;

import akka.actor.ActorContext;
import com.expleague.model.*;
import com.expleague.model.Operations.*;
import com.expleague.model.RoomState;
import com.expleague.server.Roster;
import com.expleague.server.XMPPDevice;
import com.expleague.server.XMPPUser;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.muc.MucHistory;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Message.MessageType;
import com.expleague.xmpp.stanza.Stanza;

import java.util.*;
import java.util.concurrent.TimeUnit;
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
    super(jid, false);
  }

  public static boolean isTrusted(JID from) {
    final XMPPUser user = Roster.instance().user(from.local());
    return user.authority() == ExpertsProfile.Authority.ADMIN;
  }

  @ActorMethod
  public void onDump(DumpRequest dump) {
    final List<Message> rooms = this.rooms.values().stream()
        .filter(room -> room.affiliation(dump.from()) == Affiliation.OWNER)
        .map(RoomStatus::message)
        .map(message -> participantCopy(message, XMPP.jid(dump.from())))
        .collect(Collectors.toList());
    sender().tell(rooms, self());
  }

  @Override
  protected Role suggestRole(JID who, Affiliation affiliation) {
    if (who.isRoom())
      return Role.PARTICIPANT;
    else if (isTrusted(who))
      return Role.MODERATOR;
    return Role.NONE;
  }

  @Override
  protected boolean update(JID from, Role role, Affiliation affiliation, ProcessMode mode) throws MembershipChangeRefusedException {
    return !from.isRoom() && super.update(from, role, affiliation, mode);
  }

  @Override
  protected void process(Message msg) {
    if (!msg.from().isRoom()) {
      super.process(msg);
      return;
    }
    final RoomStatus status = rooms.compute(msg.from().local(), (local, s) -> s != null ? s : new RoomStatus(local));
    final long ts = msg.ts();
    final int changes = status.changes();
    if (msg.has(OfferChange.class))
      status.offer(msg.get(Offer.class), msg.get(OfferChange.class).by(), ts);
    if (msg.has(RoomStateChanged.class))
      status.state(msg.get(RoomStateChanged.class).state(), ts);
    if (msg.has(Feedback.class))
      status.feedback(msg.get(Feedback.class).stars(), ts);
    if (msg.has(RoomRoleUpdate.class)) {
      final RoomRoleUpdate update = msg.get(RoomRoleUpdate.class);
      status.affiliation(update.expert().local(), update.affiliation());
    }
    if (msg.has(RoomMessageReceived.class)) {
      final RoomMessageReceived received = msg.get(RoomMessageReceived.class);
      status.message(received.expert(), received.count(), ts);
    }
    if (msg.has(Progress.class)) {
      final Progress progress = msg.get(Progress.class);
      if (progress.state() != null)
        status.order(progress.order(), progress.state(), ts);
    }
    if (msg.has(Start.class)) {
      final Start start = msg.get(Start.class);
      final ExpertsProfile profile = msg.get(ExpertsProfile.class);
      status.start(start.order(), profile.jid(), ts);
    }
    if (changes < status.changes())
      super.process(msg);
  }

  @Override
  public List<Stanza> archive(MucHistory history) {
    final List<Stanza> result = new ArrayList<>();
    if (history.recent()) {
      rooms.forEach((id, status) -> {
        if (status.currentOffer != null && (!EnumSet.of(CLOSED, FEEDBACK).contains(status.state) || status.lastModified() > System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)))
          result.add(status.message());
      });
    }
    else rooms.forEach((id, status) -> result.add(status.message()));
    return result;
  }

  @Override
  protected void onStart() {
    rooms.forEach((room, state) -> {
      if (state.currentOffer != null && EnumSet.of(OPEN, CHAT, RESPONSE, WORK).contains(state.state)) {
        XMPP.whisper(XMPP.muc(room), new RoomAgent.Awake(), context()); // wake up room
      }
    });
    super.onStart();
  }

  public static void tell(JID from, Item item, ActorContext context) {
    XMPP.send(new Message(from, XMPP.jid(ID), MessageType.GROUP_CHAT, item), context);
  }

  public static void tell(Stanza item, ActorContext context) {
    item.to(XMPP.jid(ID));
    XMPP.send(item, context);
  }

  private static class RoomStatus {
    private String id;
    private Offer currentOffer;
    private RoomState state = OPEN;
    private Map<String, Affiliation> affiliations = new HashMap<>();
    private Map<String, Role> roles = new HashMap<>();
    private Map<String, OrderStatus> orders = new HashMap<>();
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

    private void ts(long ts) {
      modificationTs = Math.max(ts, modificationTs);
    }

    public int changes() {
      return changes;
    }

    public void offer(Offer offer, JID by, long ts) {
      currentOffer = offer;
      changes++;
      ts(ts);
    }

    public void state(RoomState state, long ts) {
      this.state = state;
      if (state == CLOSED)
        unread = 0;
      if (state != WORK && state != VERIFY)
        orders.clear();
      changes++;
      ts(ts);
    }

    public void message(boolean expert, int count, long ts) {
      this.unread = expert ? 0 : this.unread + count;
      changes++;
      ts(ts);
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

      orders.forEach((order, status) -> {
        result.append(new Progress(order, status.state));
        if (status.expert != null)
          result.append(new Start(order, status.expert));
      });
      return result;
    }

    public void feedback(int stars, long ts) {
      this.feedback = stars;
      ts(ts);
    }

    public Affiliation affiliation(String fromId) {
      return affiliations.getOrDefault(fromId, Affiliation.NONE);
    }

    public void order(String order, OrderState state, long ts) {
      orders.compute(order, (o, v) -> v != null ? v : new OrderStatus()).state = state;
      ts(ts);
    }

    public void start(String order, JID jid, long ts) {
      orders.compute(order, (o, v) -> v != null ? v : new OrderStatus()).expert = jid;
      ts(ts);
    }

    public long lastModified() {
      return modificationTs;
    }
  }

  public static class OrderStatus {
    public OrderState state;
    public JID expert;
  }
}

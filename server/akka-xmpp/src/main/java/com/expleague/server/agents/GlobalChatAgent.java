package com.expleague.server.agents;

import akka.actor.ActorContext;
import akka.persistence.RecoveryCompleted;
import com.expleague.model.*;
import com.expleague.model.Operations.*;
import com.expleague.server.ExpLeagueServer;
import com.expleague.server.Roster;
import com.expleague.server.XMPPUser;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.muc.MucHistory;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Message.MessageType;
import com.expleague.xmpp.stanza.Stanza;
import org.apache.jackrabbit.commons.JcrUtils;
import org.jetbrains.annotations.Nullable;

import javax.jcr.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.expleague.model.RoomState.*;

/**
 * User: solar
 * Date: 16.12.15
 * Time: 13:18
 */
@SuppressWarnings("UnusedParameters")
public class GlobalChatAgent extends RoomAgent {
  private static final Logger log = Logger.getLogger(GlobalChatAgent.class.getName());
  public static final String ID = "global-chat";
  public Map<String, RoomStatus> rooms = new HashMap<>();

  private Repository repository;
  private Node roomStatusesNode;
  @Nullable
  private Session jcrSession;

  public GlobalChatAgent(JID jid) {
    super(jid, false);
    try {
      repository = JcrUtils.getRepository();
    } catch (RepositoryException e) {
      log.warning("Unable to start jackrabbit repository");
    }
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
    final RoomStatus status = rooms.compute(msg.from().local(), (local, s) -> s != null ? s : new RoomStatus(local, roomStatusesNode, jcrSession));
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

    if (msg.has(Clear.class)) {
      rooms.remove(msg.from().local());
      super.process(msg);
    }
    else if (changes < status.changes())
      super.process(msg);
  }

  @Override
  public List<Message> archive(MucHistory history) {
    final List<Message> result = new ArrayList<>();
    if (history.recent()) {
      rooms.forEach((id, status) -> {
        if (status.currentOffer != null && (!EnumSet.of(CLOSED, FEEDBACK).contains(status.state) || status.lastModified() > System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10)))
          result.add(status.message());
      });
    }
    else rooms.forEach((id, status) -> result.add(status.message()));
    return result;
  }

  @Override
  public <T> void persist(final T event, final Consumer<? super T> handler) {
    handler.accept(event);
  }

  @Override
  protected void preStart() throws Exception {
    if (!ExpLeagueServer.config().unitTest()) {
      jcrSession = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
      final Node rootNode = jcrSession.getRootNode();
      roomStatusesNode = JcrUtils.getOrAddNode(rootNode, "roomstatuses");
      final Iterable<Node> roomNodes = JcrUtils.getChildNodes(roomStatusesNode);
      for (Node node : roomNodes) {
        rooms.put(node.getName(), new RoomStatus(node.getName(), roomStatusesNode, jcrSession));
      }
    }
    super.preStart();
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

  @Override
  public void onReceiveRecover(Object o) throws Exception {
    if (!ExpLeagueServer.config().unitTest()) {
      if (o instanceof RecoveryCompleted) {
        deleteMessages();
      }
    }
    super.onReceiveRecover(o);
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

    private Node roomNode;
    private Node affiliationsNode;
    private Node rolesNode;
    private Node ordersNode;
    @Nullable
    private final Session jcrSession;

    public RoomStatus(String id, Node roomStatusesNode, @Nullable Session jcrSession) {
      this.id = id;
      this.jcrSession = jcrSession;
      if (jcrSession != null) {
        try {
          roomNode = JcrUtils.getOrAddNode(roomStatusesNode, id);
          affiliationsNode = JcrUtils.getOrAddNode(roomNode, "affiliations");
          final Iterable<Node> affiliationsChildNodes = JcrUtils.getChildNodes(affiliationsNode);
          for (Node node : affiliationsChildNodes) {
            final Affiliation affiliation = Affiliation.fromPriority((int) node.getProperty("priority").getLong());
            affiliations.put(node.getName(), affiliation);
          }

          rolesNode = JcrUtils.getOrAddNode(roomNode, "roles");
          final Iterable<Node> rolesChildNodes = JcrUtils.getChildNodes(rolesNode);
          for (Node node : rolesChildNodes) {
            final Role role = Role.fromPriority((int) node.getProperty("priority").getLong());
            roles.put(node.getName(), role);
          }

          ordersNode = JcrUtils.getOrAddNode(roomNode, "orders");
          final Iterable<Node> ordersChildNodes = JcrUtils.getChildNodes(ordersNode);
          for (Node node : ordersChildNodes) {
            final OrderState orderState = node.hasProperty("code") ? OrderState.fromCode((int) node.getProperty("code").getLong()) : null;
            final JID expert = node.hasProperty("expert") ? JID.parse(node.getProperty("expert").getString()) : null;
            final OrderStatus orderStatus = new OrderStatus(orderState, expert);
            orders.put(node.getName(), orderStatus);
          }

          modificationTs = JcrUtils.getLongProperty(roomNode, "modificationTs", modificationTs);
          changes = (int) JcrUtils.getLongProperty(roomNode, "changes", changes);
          unread = (int) JcrUtils.getLongProperty(roomNode, "unread", unread);
          feedback = (int) JcrUtils.getLongProperty(roomNode, "feedback", feedback);
          currentOffer = roomNode.hasProperty("offer") ? Offer.create(roomNode.getProperty("offer").getString()) : currentOffer;
          state = roomNode.hasProperty("state") ? RoomState.fromCode((int) roomNode.getProperty("state").getLong()) : state;

          jcrSession.save();
        } catch (RepositoryException re) {
          log.warning("Unable to load room status node: " + re);
        }
      }
    }

    public void affiliation(String id, Affiliation affiliation) {
      final Affiliation a = affiliations.getOrDefault(id, Affiliation.NONE);
      if (affiliation != null && affiliation.priority() < a.priority()) {
        affiliations.put(id, affiliation);
        changes++;

        applyJcrChanges(() -> {
          roomNode.setProperty("changes", changes);
          final Node affiliationNode = JcrUtils.getOrAddNode(affiliationsNode, id);
          affiliationNode.setProperty("priority", affiliation.priority());
        });
      }
    }

    public void role(String id, Role role) {
      roles.put(id, role);
      changes++;

      applyJcrChanges(() -> {
        roomNode.setProperty("changes", changes);
        final Node roleNode = JcrUtils.getOrAddNode(rolesNode, id);
        roleNode.setProperty("priority", role.priority());
      });
    }

    private long ts(long ts) {
      return Math.max(ts, modificationTs);
    }

    public int changes() {
      return changes;
    }

    public void offer(Offer offer, JID by, long ts) {
      currentOffer = offer;
      changes++;
      modificationTs = ts(ts);

      applyJcrChanges(() -> {
        roomNode.setProperty("offer", offer.toString());
        roomNode.setProperty("changes", changes);
        roomNode.setProperty("modificationTs", modificationTs);
      });
    }

    public void state(RoomState state, long ts) {
      this.state = state;
      if (state == CLOSED)
        unread = 0;

      final boolean ordersCleared;
      if (state != WORK && state != VERIFY && !orders.isEmpty()) {
        ordersCleared = true;
        orders.clear();
      }
      else {
        ordersCleared = false;
      }
      changes++;
      modificationTs = ts(ts);

      applyJcrChanges(() -> {
        if (ordersCleared) {
          final Iterable<Node> ordersIterable = JcrUtils.getChildNodes(ordersNode);
          for (Node node : ordersIterable) {
            node.remove();
          }
        }
        roomNode.setProperty("state", state.code());
        roomNode.setProperty("unread", unread);
        roomNode.setProperty("changes", changes);
        roomNode.setProperty("modificationTs", modificationTs);
      });
    }

    public void message(boolean expert, int count, long ts) {
      this.unread = expert ? 0 : this.unread + count;
      changes++;
      modificationTs = ts(ts);

      applyJcrChanges(() -> {
        roomNode.setProperty("unread", unread);
        roomNode.setProperty("changes", changes);
        roomNode.setProperty("modificationTs", modificationTs);
      });
    }

    public Message message() {
      final Message result = new Message("global-" + id + "-" + (modificationTs / 1000));
      result.type(MessageType.GROUP_CHAT);
      result.append(currentOffer);
      result.append(new RoomStateChanged(state));
      result.append(new RoomMessageReceived(unread));
      result.from(XMPP.muc(id));
      if (feedback > 0)
        result.append(new Feedback(feedback));

      final Set<String> ids = new HashSet<>(roles.keySet());
      ids.addAll(affiliations.keySet());
      for (final String nick : ids) {
        final Role role = roles.getOrDefault(nick, Role.NONE);
        final Affiliation affiliation = affiliations.getOrDefault(nick, Affiliation.NONE);
        final RoomRoleUpdate update = new RoomRoleUpdate(XMPP.jid(nick), role, affiliation);
        result.append(update);
      }

      orders.forEach((order, status) -> {
        if (status.state != null)
          result.append(new Progress(order, status.state));
        if (status.expert != null)
          result.append(new Start(order, status.expert));
      });
      return result;
    }

    public void feedback(int stars, long ts) {
      this.feedback = stars;
      modificationTs = ts(ts);

      applyJcrChanges(() -> {
        roomNode.setProperty("feedback", feedback);
        roomNode.setProperty("modificationTs", modificationTs);
      });
    }

    public Affiliation affiliation(String fromId) {
      return affiliations.getOrDefault(fromId, Affiliation.NONE);
    }

    public void order(String order, OrderState state, long ts) {
      orders.compute(order, (o, v) -> v != null ? v : new OrderStatus()).state = state;
      modificationTs = ts(ts);

      applyJcrChanges(() -> {
        roomNode.setProperty("modificationTs", modificationTs);
        final Node orderNode = JcrUtils.getOrAddNode(ordersNode, order);
        orderNode.setProperty("code", state.code());
      });
    }

    public void start(String order, JID jid, long ts) {
      orders.compute(order, (o, v) -> v != null ? v : new OrderStatus()).expert = jid;
      modificationTs = ts(ts);

      applyJcrChanges(() -> {
        roomNode.setProperty("modificationTs", modificationTs);
        final Node orderNode = JcrUtils.getOrAddNode(ordersNode, order);
        orderNode.setProperty("expert", jid.toString());
      });
    }

    public long lastModified() {
      return modificationTs;
    }

    private void applyJcrChanges(JcrUpdate jcrUpdate) {
      if (jcrSession != null) {
        try {
          jcrUpdate.apply();
          jcrSession.save();
        } catch (RepositoryException re) {
          log.warning("Unable to save into jackrabbit repository: " + re);
        }
      }
    }

    @FunctionalInterface
    private interface JcrUpdate {
      void apply() throws RepositoryException;
    }
  }

  public static class OrderStatus {
    public OrderState state;
    public JID expert;

    public OrderStatus() {
    }

    public OrderStatus(OrderState state, JID expert) {
      this.state = state;
      this.expert = expert;
    }
  }
}
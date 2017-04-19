package com.expleague.server.agents;

import akka.actor.ActorRef;
import akka.persistence.DeleteMessagesFailure;
import akka.persistence.DeleteMessagesSuccess;
import akka.persistence.RecoveryCompleted;
import com.expleague.model.Affiliation;
import com.expleague.model.Delivered;
import com.expleague.model.Role;
import com.expleague.server.Subscription;
import com.expleague.server.dao.Archive;
import com.expleague.util.akka.ActorMethod;
import com.expleague.util.akka.PersistentActorAdapter;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.control.DeliveryReceit;
import com.expleague.xmpp.muc.MucAdminQuery;
import com.expleague.xmpp.muc.MucHistory;
import com.expleague.xmpp.muc.MucXData;
import com.expleague.xmpp.stanza.Iq;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Message.MessageType;
import com.expleague.xmpp.stanza.Presence;
import com.expleague.xmpp.stanza.Stanza;
import com.expleague.xmpp.stanza.data.Err;
import com.spbsu.commons.util.Holder;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * User: solar
 * Date: 16.12.15
 * Time: 13:18
 */
@SuppressWarnings("UnusedParameters")
public class RoomAgent extends PersistentActorAdapter {
  private static final Logger log = Logger.getLogger(RoomAgent.class.getName());
  private final JID jid;
  private final List<Stanza> archive;
  private Map<JID, MucUserStatus> participants = new HashMap<>();
  private Archive.Dump dump;

  private Subscription subscription;
  private ProcessMode mode;

  public RoomAgent(JID jid, boolean archive) {
    this.jid = jid;
    if (archive)
      this.archive = new ArrayList<>();
    else
      this.archive = null;
  }

  public JID jid() {
    return jid;
  }

  public JID owner() {
    final Holder<JID> result = new Holder<>();
    participants.forEach((jid, status) -> {
      if (status.affiliation == Affiliation.OWNER)
        result.setValue(jid);
    });
    return result.getValue();
  }

  public final List<Stanza> archive() {
    return archive(new MucHistory());
  }

  protected ProcessMode mode() {
    return mode;
  }

  public List<Stanza> archive(MucHistory history) {
    if (archive != null)
      return history.filter(archive).collect(Collectors.toList());
    else
      return Collections.emptyList();
  }

  public <T> void persist(final T event, final Consumer<? super T> handler) {
    if (mode != ProcessMode.RECOVER)
      super.persist(event, handler);
    else
      handler.accept(event);
  }

  @ActorMethod
  public final void onPresence(Presence presence) {
    if (!filter(presence) || !update(presence))
      return;
    broadcast(presence);
  }

  @ActorMethod
  public final void onMessage(Message message) {
    if (!filter(message)) {
      final Message error = new Message(
          jid,
          message.from(),
          MessageType.ERROR,
          "Сообщение " + message.xmlString() + " не доставленно. Вы не являетесь участником комнаты!"
      );
      XMPP.send(error, context());
      return;
    }

    persist(message, msg -> {
      archive(msg);
      if (msg.to() != null && msg.to().resource().isEmpty()) // process only messages
        process(msg);
      broadcast(msg);
    });
  }

  @ActorMethod
  public final void onIq(Iq command) {
    if (process(command))
      persist(command, this::archive);
  }

  @ActorMethod
  public void onDump(DumpRequest req) {
    if (req.fromId == null) {
      sender().tell(archive(), self());
    }
    else {
      final JID jid = req.fromId.equals("owner") ? owner() : XMPP.jid(req.from());
      sender().tell(
          archive().stream()
              .filter(stanza -> checkDst(stanza, jid, true) && ((stanza.to() != null && stanza.to().hasResource()) || relevant(stanza, jid)))
              .collect(Collectors.toList()),
          self());
    }
  }

  @ActorMethod
  public void onDelivered(Delivered id) {
    delivered(id.id());
  }

  public void delivered(String id) {}

  public static class Awake {}
  @ActorMethod
  public final void onAwake(Awake awake) {}

  public static class DumpRequest {
    private String fromId;
    public DumpRequest() {}
    public DumpRequest(String from) {
      fromId = from;
    }

    public String from() {
      return fromId;
    }
  }

  public static class Replay {
    public Boolean success;

    Replay(boolean success) {
      this.success = success;
    }

    public Replay() {}
  }

  private ActorRef replayRequester;
  @ActorMethod
  public void onReplay(Replay replay) {
    replayRequester = sender();
    replay();
  }

  protected boolean process(Iq iq) {
    final JID from = iq.from();
    if (iq.get() instanceof MucAdminQuery && from != null && iq.type() == Iq.IqType.SET) {
      if (affiliation(from).priority() > Affiliation.ADMIN.priority()) {
        if (mode == ProcessMode.NORMAL) {
          //noinspection unchecked
          XMPP.send(Iq.error(iq).error(new Err(Err.Cause.NOT_ALLOWED, Err.ErrType.CANCEL, null)), context());
        }
        return false;
      }

      final MucAdminQuery query = (MucAdminQuery) iq.get();
      final JID user = XMPP.jid(query.nick());
      if (query.affiliation() != null) {
        update(user, null, query.affiliation());
        if (mode == ProcessMode.NORMAL) {
          //noinspection unchecked
          XMPP.send(Iq.error(iq).error(new Err(Err.Cause.NOT_ALLOWED, Err.ErrType.CANCEL, null)), context());
        }
        return mode != ProcessMode.RECOVER;
      }
    }
    else if (iq.get() instanceof DeliveryReceit) {
      delivered(((DeliveryReceit) iq.get()).id());
    }
    return false;
  }

  Set<String> knownIds = new HashSet<>();
  private void archive(Stanza iq) {
    if (archive != null) {
      if (knownIds.contains(iq.id()) || knownIds.contains(iq.strippedVitalikId()))
        return;
      knownIds.add(iq.id());
      archive.add(iq);
      dump.accept(iq);
    }
  }

  public Role role(JID from) {
    if (from.local().isEmpty() || from.equals(jid))
      return Role.MODERATOR;

    final MucUserStatus status = participants.get(from.bare());

    final Role result = status != null ? status.role : null;
    return result != null ? result : Role.NONE;
  }

  public Affiliation affiliation(JID from) {
    if (from.local().isEmpty())
      return Affiliation.ADMIN;

    final MucUserStatus status = participants.get(from.bare());
    return status != null ? status.affiliation : Affiliation.NONE;
  }

  protected boolean checkAffiliation(JID from, Affiliation affiliation) {
    return affiliation == null || affiliation(from).priority() <= affiliation.priority();
  }

  protected boolean checkRole(JID from, Affiliation affiliation, Role role) {
    if (role == null)
      return true;
    final JID bareFrom = from.bare();
    if (affiliation == null)
      affiliation = affiliation(bareFrom);
    return role.priority() >= suggestRole(bareFrom, affiliation).priority();
  }

  public void affiliation(JID from, Affiliation affiliation) {
    try {
      update(from, null, affiliation, ProcessMode.REPLAY);
    } catch (MembershipChangeRefusedException ignore) {}
  }

  public boolean update(JID from, Role role, Affiliation affiliation) {
    try {
      return update(from, role, affiliation, mode);
    }
    catch (MembershipChangeRefusedException ignored) {
      return false;
    }
  }

  protected boolean update(JID from, Role role, Affiliation affiliation, ProcessMode mode) throws MembershipChangeRefusedException {
    if (mode == ProcessMode.RECOVER)
      return true;
    final MucUserStatus status = participants.compute(from.bare(), (jid, s) -> s != null ? s : new MucUserStatus(jid, jid.local(), Affiliation.NONE));

    try {
      if (mode == ProcessMode.NORMAL) { // need to check if the update is valid
        if (!checkAffiliation(from, affiliation))
          throw new MembershipChangeRefusedException();
        if (!checkRole(from, affiliation, role))
          throw new MembershipChangeRefusedException();
      }
      boolean changed = false;
      if (role != null && role != status.role) {
        changed = true;
        status.role = role;
      }
      if (affiliation != null && affiliation != status.affiliation) {
        changed = true;
        status.affiliation = affiliation;
      }
      return changed;
    }
    finally {
      if (status.empty()) {
        participants.remove(from.bare());
        exit(from.bare());
      }
    }
  }

  public static class MembershipChangeRefusedException extends Exception {}

  protected void exit(JID from) {}

  protected void enter(JID jid, MucXData xData) {
    if (mode() != ProcessMode.NORMAL)
      return;
    participants.forEach((id, s) -> {
      if (id.bareEq(jid))
        return;
      XMPP.send(participantCopy(s.presence(), jid), context());
    });
    if (xData.has(MucHistory.class)) {
      final MucHistory history = xData.get(MucHistory.class);
      archive(history).forEach(stanza -> {
        final JID to = stanza.to();
        if (stanza instanceof Message && checkDst(stanza, jid, true) && ((to != null && to.hasResource()) || relevant(stanza, jid))) {
          XMPP.send(participantCopy(stanza, jid), context());
        }
      });
    }
  }

  public boolean update(Presence presence) {
    final JID from = presence.from();
    final Role role = role(from);
    try {
      if (presence.available()) {
        final MucXData xData = presence.has(MucXData.class) ? presence.get(MucXData.class) : new MucXData();
        if (xData.role() == null) {
          final Affiliation affiliation = xData.affiliation() == null ? affiliation(from) : xData.affiliation();
          xData.role(role != Role.NONE ? role : suggestRole(from, affiliation));
        }

        final boolean rc = update(from, xData.role(), xData.affiliation(), ProcessMode.NORMAL);
        enter(from, xData);
        return rc;
      }
      else
        return update(from, Role.NONE, null, ProcessMode.NORMAL);
    } catch (MembershipChangeRefusedException e) {
      return false;
    }
  }

  protected boolean filter(Presence pres) { return true; }
  protected boolean relevant(Stanza msg, JID to) {
    return !(msg instanceof Message) || ((Message) msg).type() == MessageType.GROUP_CHAT;
  }

  private boolean checkDst(Stanza stanza, JID to, boolean sendBack) {
    final JID msgTo = stanza.to();
    final MucUserStatus status = participants.get(to.bare());
    if (status == null) // not a member
      return false;
    if (!sendBack) {
      final JID msgFrom = stanza.from();
      if (msgFrom.bareEq(to))
        return false;
      if (msgFrom.bareEq(jid()) && msgFrom.hasResource()) {
        if (!msgFrom.resource().equals(status.nickname))
          return false;
      }
    }
    return msgTo == null || !msgTo.hasResource() || msgTo.resource().equals(status.nickname);
  }

  protected boolean filter(Message msg) {
    final JID from = msg.from();
    Role role = role(from);
    if (role == Role.NONE) {
      role = suggestRole(from, affiliation(from));
      update(from, role, null);
    }
    return !(
        msg.type() == MessageType.GROUP_CHAT && role.priority() > Role.PARTICIPANT.priority()
     || role.priority() > Role.VISITOR.priority()
    );
  }

  protected enum ProcessMode {
    NORMAL, REPLAY, RECOVER
  }
  private int msgIndex = 0;
  protected void process(Message msg) {
    final JID from = msg.from();
    if (role(from) == Role.NONE && mode == ProcessMode.NORMAL)
      update(from, suggestRole(from, affiliation(from)), null);

    if (mode == ProcessMode.NORMAL && ++msgIndex % 10 == 0)
      commit();
  }

  protected void onStart() {
    mode = ProcessMode.NORMAL;
  }

  protected void broadcast(Stanza stanza) {
    if (mode != ProcessMode.NORMAL)
      return;
    participants.forEach((jid, status) -> {
      if (status.role == Role.NONE && status.affiliation != Affiliation.OWNER)
        return;
      if (!checkDst(stanza, jid, false))
        return;
      if ((stanza.to() != null && stanza.to().hasResource()) || relevant(stanza, jid))
        XMPP.send(participantCopy(stanza, jid), context());
    });
  }

  protected <S extends Stanza> S participantCopy(final S stanza, final JID to) {
    if (stanza instanceof Message) {
      return stanza.<S>copy(to.local())
          .to(to)
          .from(roomAlias(stanza.from()));
    }
    return stanza.<S>copy()
      .to(to)
      .from(roomAlias(stanza.from()));
  }

  protected void commit() {
    if (mode() == ProcessMode.NORMAL)
      dump.commit();
  }

  @NotNull
  protected JID roomAlias(JID from) {
    if (from == null) {
      System.out.println();
    }
    final MucUserStatus status = participants.get(from);
    if (from.equals(jid()))
      return jid();
    return new JID(this.jid.local(), this.jid.domain(), status == null ? from.local() : status.nickname);
  }

  protected Role suggestRole(JID who, Affiliation affiliation) {
    if (participants.isEmpty())
      return Role.MODERATOR;
    final Role suggest;
    switch (affiliation) {
      case OWNER:
      case ADMIN:
        suggest = Role.MODERATOR;
        break;
      case MEMBER:
        suggest = Role.PARTICIPANT;
        break;
      case VISITOR:
        suggest = Role.VISITOR;
        break;
      default:
        suggest = Role.NONE;
    }
    return suggest;
  }

  @Override
  public String persistenceId() {
    return jid.local();
  }

  @Override
  public void onReceiveRecover(Object o) throws Exception {
    if (o instanceof Iq)
      onIq((Iq) o);
    else if (o instanceof Message)
      onMessage((Message) o);
    else if (o instanceof DeliveryReceit)
      delivered(((DeliveryReceit) o).id());
    else if (o instanceof RecoveryCompleted) {
      if (archive != null) {
        if (dump.size() > archive.size())
          replay();
        else
          commit();
      }
      onStart();
    }
  }

  protected void replay() {
    mode = ProcessMode.REPLAY;
    context().become(o -> {
      final boolean success;
      if (o instanceof DeleteMessagesSuccess) {
        participants.clear();
        archive.clear();
        dump.stream().forEach(stanza -> {
          if (stanza instanceof Message)
            onMessage((Message)stanza);
          else if (stanza instanceof Iq)
            onIq((Iq)stanza);
        });
        success = true;
      }
      else if (o instanceof DeleteMessagesFailure) {
        log.warning("Unable to wipe room " + jid());
        success = false;
      }
      else {
        stash();
        return;
      }
      context().unbecome();
      unstashAll();
      mode = ProcessMode.NORMAL;
      if (replayRequester != null)
        replayRequester.tell(new Replay(success), self());
    });
    deleteMessages();
  }

  @Override
  protected void preStart() throws Exception {
    super.preStart();
    subscription = new Subscription() {
      @Override
      public JID who() {
        return jid;
      }

      @Override
      public boolean relevant(JID jid) {
        return participants.containsKey(jid.bare());
      }
    };
    if (archive != null)
      archive.clear();
    XMPP.subscribe(subscription, context());
    dump = Archive.instance().dump(jid.local());
    mode = ProcessMode.RECOVER;
  }

  @Override
  protected void postStop() {
    XMPP.unsubscribe(subscription, context());
  }

  private class MucUserStatus {
    private JID jid;
    private Affiliation affiliation = Affiliation.NONE;
    private String nickname;
    private Role role = Role.NONE;

    public MucUserStatus(JID jid, String nick, Affiliation affiliation) {
      this.jid = jid;
      this.nickname = nick;
      this.affiliation = affiliation;
    }

    public Presence presence() {
      return new Presence(jid, role != Role.NONE, new MucXData(affiliation, role));
    }

    public boolean empty() {
      return affiliation == Affiliation.NONE && role == Role.NONE;
    }
  }
}

package com.expleague.server.agents;

import akka.persistence.RecoveryCompleted;
import com.expleague.model.Affiliation;
import com.expleague.model.Delivered;
import com.expleague.model.Role;
import com.expleague.server.Subscription;
import com.expleague.server.dao.Archive;
import com.expleague.util.akka.ActorMethod;
import com.expleague.util.akka.PersistentActorAdapter;
import com.expleague.xmpp.JID;
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

/**
 * User: solar
 * Date: 16.12.15
 * Time: 13:18
 */
@SuppressWarnings("UnusedParameters")
public class RoomAgent extends PersistentActorAdapter {
  private final JID jid;
  private List<Stanza> archive = new ArrayList<>();
  private Map<JID, MucUserStatus> participants = new HashMap<>();
  private Subscription subscription;
  private boolean started;

  public RoomAgent(JID jid) {
    this.jid = jid;
  }
  public RoomAgent(JID jid, JID owner) {
    this.jid = jid;
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

  public List<Stanza> archive() {
    return archive;
  }

  @ActorMethod
  public final void invoke(Presence presence) {
    if (archive.isEmpty() && presence.available())
      update(presence.from(), Role.MODERATOR, Affiliation.OWNER, true);
    if (!filter(presence) || !update(presence))
      return;
    broadcast(presence);
  }

  @ActorMethod
  public final void invoke(Message message) {
    if (archive.isEmpty())
      update(message.from(), Role.MODERATOR, Affiliation.OWNER, true);
    if (!filter(message)) {
      final Message error = new Message(
          jid,
          message.from(),
          MessageType.ERROR,
          "Сообщение от " + message.from() + " не доставленно. Вы не являетесь участником комнаты!"
      );
      XMPP.send(error, context());
      return;
    }

    persist(message, msg -> {
      archive.add(msg);
      process(msg);
      broadcast(msg);
    });
  }

  @ActorMethod
  public final void invoke(Iq command) {
    process(command, reply -> XMPP.send(reply, context()));
  }

  @ActorMethod
  public final void delivered(Delivered delivered) {
    if (delivered.resource() == null)
      this.delivered(delivered.id());
  }

  @ActorMethod
  public final void dump(DumpRequest req) {
    sender().tell(archive, self());
  }

  @ActorMethod
  public final void awake(Awake awake) {}

  public static class DumpRequest {
    String fromId;
    public DumpRequest() {}
    public DumpRequest(String from) {
      fromId = from;
    }
  }

  protected void process(Iq iqRequest, Consumer<Iq> answer) {
    final JID from = iqRequest.from();
    if (from != null && iqRequest.type() == Iq.IqType.SET && iqRequest.get() instanceof MucAdminQuery) {
      if (affiliation(from).priority() > Affiliation.ADMIN.priority()) {
        //noinspection unchecked
        answer.accept(Iq.error(iqRequest).error(new Err(Err.Cause.NOT_ALLOWED, Err.ErrType.CANCEL, null)));
        return;
      }

      persist(iqRequest, iq -> {
        final MucAdminQuery query = (MucAdminQuery) iq.get();
        if (query.affiliation() != null) {
          final Optional<MucUserStatus> knownUser = participants.values().stream()
              .filter(status -> status.nickname.equals(query.nick()))
              .findAny();
          final JID user = XMPP.jid(query.nick());
          if (query.affiliation() != Affiliation.NONE) {
            if (!knownUser.isPresent())
              participants.put(user, new MucUserStatus(user.local(), query.affiliation()));
            else
              knownUser.get().affiliation = query.affiliation();
          }
          else if (knownUser.isPresent() && knownUser.get().role != Role.NONE)
            participants.remove(user);
          answer.accept(Iq.answer(iq));
        }
      });
    }
  }

  public Role role(JID from) {
    if (from.local().isEmpty())
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
    return false;
  }

  protected boolean checkRole(JID from, Affiliation affiliation, Role role) {
    return !(role.priority() <= Role.MODERATOR.priority() && affiliation.priority() > Affiliation.ADMIN.priority())
        && !(role.priority() <= Role.PARTICIPANT.priority() && affiliation.priority() > Affiliation.MEMBER.priority())
        && !(role.priority() <= Role.VISITOR.priority() && affiliation.priority() > Affiliation.NONE.priority());
  }

  public boolean update(JID from, Role role, Affiliation affiliation, boolean enforce) {
    final Affiliation finalAffiliation = affiliation;
    final MucUserStatus status = participants.compute(from.bare(), (jid, s) -> {
      if (s != null) {
        if (s.role != Role.NONE && role == Role.NONE)
          exit(from);
        if (role == Role.NONE && finalAffiliation == Affiliation.NONE)
          return null;
        return s;
      }
      return new MucUserStatus(jid.local(), Affiliation.NONE);
    });

    if (status == null)
      return true;
    if (!enforce && affiliation != null && affiliation.priority() > status.affiliation.priority() && !checkAffiliation(from, affiliation))
      return false;
    affiliation = affiliation == null || status.affiliation.priority() < affiliation.priority() ? status.affiliation : affiliation;

    if (!enforce && !checkRole(from, affiliation, role))
      return false;
    boolean changed = false;
    if (status.affiliation != affiliation) {
      status.affiliation = affiliation;
      changed = true;
      process(Iq.create(jid(), XMPP.jid(), Iq.IqType.SET, new MucAdminQuery(from.local(), affiliation, null)), iq -> {});
    }
    if (status.role != role) {
      if (status.role == Role.NONE)
        enter(roomAlias(from));
      changed = true;
      status.role = role;
    }
    return changed;
  }

  protected void exit(JID from) {}

  protected void enter(JID jid) {
    participants.forEach((id, s) -> {
      if (id.local().equals(jid.resource()))
        return;
      XMPP.send(participantCopy(s.presence, id), context());
    });
  }

  public boolean update(Presence presence) {
    final JID from = presence.from();
    final MucXData xData = presence.has(MucXData.class) ? presence.get(MucXData.class) : new MucXData();
    if (!presence.available())
      xData.role(Role.NONE);
    else if (xData.role() == null) {
      Role role = role(from);
      if (role == Role.NONE)
        role = suggestRole(from, affiliation(from));
      xData.role(role);
    }
    if (xData.affiliation() == null)
      xData.affiliation(affiliation(from));
    if (!update(from, xData.role(), xData.affiliation(), false))
      return false;

    final MucUserStatus status = participants.get(from.bare());
    if (status != null)
      status.presence = presence;
    if (xData.has(MucHistory.class) && presence.available()) {
      xData.get(MucHistory.class).filter(archive()).forEach(stanza -> {
        if (stanza instanceof Message && relevant(stanza, from)) {
          XMPP.send(participantCopy(stanza, from), context());
        }
      });
    }
    return true;
  }

  protected boolean filter(Presence pres) { return true; }
  protected boolean relevant(Stanza msg, JID to) { return !(msg instanceof Message) || ((Message) msg).type() == MessageType.GROUP_CHAT; }
  protected boolean filter(Message msg) {
    final JID from = msg.from();
    Role role = role(from);
    if (role == Role.NONE) {
      role = suggestRole(from, affiliation(from));
      update(from, role, null, false);
    }
    return !(
        msg.type() == MessageType.GROUP_CHAT && role.priority() > Role.PARTICIPANT.priority()
     || role.priority() > Role.VISITOR.priority()
    );
  }

  private int msgIndex = 0;
  protected void process(Message msg) {
    if (!started)
      return;
    if (++msgIndex % 10 == 0)
      commit();
  }
  protected void delivered(String id) {}

  protected void onStart() {
    started = true;
  }

  protected void broadcast(Stanza stanza) {
    if (!started)
      return;
    participants.forEach((jid, status) -> {
      if (jid.bareEq(stanza.from()) || !relevant(stanza, jid))
        return;
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
    final Archive.Dump dump = Archive.instance().dump(jid.local());
    archive.forEach(dump::accept);
    dump.commit();
  }

  @NotNull
  protected JID roomAlias(JID from) {
    final MucUserStatus status = participants.get(from);
    return new JID(this.jid.local(), this.jid.domain(), status == null ? from.local() : status.nickname);
  }

  protected Role suggestRole(JID who, Affiliation affiliation) {
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
    if (o instanceof Stanza) {
      final Stanza stanza = (Stanza) o;
      if (o instanceof Iq)
        process((Iq) o, iq -> {});
      archive.add(stanza);
    }
    else if (o instanceof RecoveryCompleted) {
      if (archive.isEmpty()) {
        final Archive.Dump dump = Archive.instance().dump(jid.local());
        if (dump != null)
          dump.stream().forEach(archive::add);
      }
      onStart();
    }
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
    XMPP.subscribe(subscription, context());
  }

  @Override
  protected void postStop() {
    XMPP.unsubscribe(subscription, context());
  }

  private class MucUserStatus {
    private Affiliation affiliation = Affiliation.NONE;
    private String nickname;
    private Role role;
    private Presence presence = new Presence(jid(), false);

    public MucUserStatus(String nick, Affiliation affiliation) {
      this.nickname = nick;
      this.affiliation = affiliation;
      role = Role.NONE;
    }
  }

  public static class Awake {
  }
}

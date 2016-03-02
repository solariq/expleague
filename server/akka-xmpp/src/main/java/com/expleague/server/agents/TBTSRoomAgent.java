package com.expleague.server.agents;

import akka.actor.ActorContext;
import com.expleague.model.Offer;
import com.expleague.model.Operations;
import com.expleague.model.Operations.Cancel;
import com.expleague.model.Operations.Done;
import com.expleague.model.Operations.Start;
import com.expleague.server.ExpertManager;
import com.expleague.server.dao.Archive;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.util.akka.AkkaTools;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Iq;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Message.MessageType;
import com.expleague.xmpp.stanza.Presence;
import com.expleague.xmpp.stanza.Stanza;
import com.google.common.annotations.VisibleForTesting;
import com.spbsu.commons.func.Functions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * User: solar
 * Date: 16.12.15
 * Time: 13:18
 */
@SuppressWarnings("UnusedParameters")
public class TBTSRoomAgent extends ActorAdapter {
  private final List<Item> snapshot = new ArrayList<>();

  private final JID jid;
  private final Status status = new Status();
  private final PresenceTracker presenceTracker = new PresenceTracker();

  public TBTSRoomAgent(JID jid) {
    this.jid = jid.bare();
  }

  @Override
  protected void init() {
    Archive.instance().visitMessages(jid.local(), (authorId, message, ts) -> {
      final Item item = Item.create(message);
      snapshot.add(item);
      if (item instanceof Stanza) {
        status.accept((Stanza)item);
      }
      return true;
    });

    if (snapshot.isEmpty()) {
      invoke(new Message(jid, null, MessageType.GROUP_CHAT, "Welcome to room " + jid));
    }
  }

  @ActorMethod
  public void invoke(Operations.Resume resume) {
    if (status.isOpen()) {
      tellLaborExchange(status.offer());
    }
  }

  @ActorMethod
  public void invoke(Message msg) {
    final JID from = msg.from();

    if (msg.type() == MessageType.GROUP_CHAT && status.owner() != null && !status.isParticipant(from)) {
      final Message message = new Message(
        jid,
        from,
        MessageType.GROUP_CHAT,
        "Сообщение от " + from + " не доставленно. Вы не являетесь участником задания! Известные участники: " + status.participants.stream().map(JID::toString).collect(Collectors.joining(", ")) + "."
      );
      message.append(msg);
      XMPP.send(message, context());
      return;
    }

    if (msg.has(Start.class) || msg.has(Operations.Resume.class)) {
      enterRoom(from);
      XMPP.send(new Message(jid, status.owner(), msg.get(Operations.Command.class), ExpertManager.instance().profile(from.bare())), context());
    }
    else if (msg.has(Cancel.class) || msg.has(Done.class)) {
      if (from.bareEq(status.owner())) {
        tellLaborExchange(msg.get(Operations.Command.class));
      }
      else if (msg.has(Cancel.class)) {
        XMPP.send(new Message(jid, status.owner(), msg.get(Operations.Command.class), ExpertManager.instance().profile(from.bare())), context());
      }
    }
    else if (!from.bareEq(status.owner()) && msg.body().startsWith("{\"type\":\"pageVisited\"")) {
      XMPP.send(new Message(jid, status.owner(), msg.body()), context());
    }

    log(msg);

    if (from.bareEq(status.owner()) && !status.isOpen()) {
      final Offer offer = status.offer();
      if (offer != null) {
        invoke(new Message(XMPP.jid(), jid, new Operations.Create(), offer));
        tellLaborExchange(offer.copy());
      }
    }

    if (msg.type() == MessageType.GROUP_CHAT) {
      broadcast(msg);
    }
  }

  private void tellLaborExchange(final Object message) {
    LaborExchange.reference(context()).tell(message, self());
  }

  @ActorMethod
  public void invoke(Presence presence) {
    if (status.owner() == null) {
      enterRoom(presence.from());
    }
    else if (!status.isParticipant(presence.from())) {
      return;
    }

    if (!presenceTracker.updatePresence(presence)) {
      return;
    }

    log(presence);
    broadcast(presence);
  }

  @ActorMethod
  public void invoke(Iq command) {
    log(command);
    XMPP.send(Iq.answer(command), context());
    if (command.type() == Iq.IqType.SET) {
      XMPP.send(new Message(jid, command.from(), "Room set up and unlocked."), context());
    }
    // todo: why do we duplicate accept here? it is already called inside of log()
    status.accept(command);
  }

  private void broadcast(Stanza stanza) {
    status.participants.stream()
      .filter(jid -> !jid.bareEq(stanza.from()))
      .forEach(jid -> XMPP.send(copyFromRoomAlias(stanza, jid), context()));
  }

  private void enterRoom(JID jid) {
    if (jid.bareEq(this.jid)) {
      return;
    }

    if (status.isParticipant(jid) && !status.isWorker(jid)) {
      return;
    }

    snapshot.stream()
      .flatMap(Functions.instancesOf(Message.class))
      .filter(message -> message.type() == MessageType.GROUP_CHAT)
      .forEach(message -> XMPP.send(copyFromRoomAlias(message, jid), context()));
  }

  private <S extends Stanza> S copyFromRoomAlias(final S stanza, final JID to) {
    return stanza.<S>copy()
      .to(to)
      .from(roomAlias(stanza.from()));
  }

  @NotNull
  private JID roomAlias(JID from) {
    return new JID(this.jid.local(), this.jid.domain(), from.local());
  }

  public void log(Stanza stanza) { // saving everything to archive
    snapshot.add(stanza);
    status.accept(stanza);
    Archive.instance().log(jid.local(), stanza.from().toString(), stanza.xmlString());
  }

  @ActorMethod
  public void invoke(Class<?> c) {
    if (Status.class.equals(c)) {
      sender().tell(status.copy(), self());
    }
    else unhandled(c);
  }

  public static Status status(JID roomJid, ActorContext context) {
    return (Status) AkkaTools.ask(XMPP.agent(roomJid, context), Status.class);
  }

  public static class Status {
    private final Set<JID> participants = new HashSet<>();
    private JID owner = null;
    private JID lastWorker = null;
    private boolean open;
    private boolean lastActive;
    private Offer offer;

    public boolean isWorker(JID jid) {
      return offer != null && offer.hasWorker(jid);
    }

    @Nullable
    public JID lastWorker() {
      return lastWorker;
    }

    public JID owner() {
      return owner;
    }

    public boolean isOpen() {
      return open;
    }

    public boolean isLastWorkerActive() {
      return open && lastActive && lastWorker != null && !offer.hasSlacker(lastWorker);
    }

    public boolean isParticipant(JID jid) {
      return jid.local().isEmpty() || participants.contains(jid.bare());
    }

    @VisibleForTesting
    void accept(Stanza item) {
      if (item instanceof Message) {
        final Message msg = (Message) item;
        final JID bareSender = msg.from().bare();
        if (msg.has(Start.class)) {
          participants.add(bareSender);
          lastWorker = bareSender;
          offer.addWorker(ExpertManager.instance().profile(bareSender));
          lastActive = true;
          open = true;
        }
        if (msg.has(Operations.Create.class)) {
          open = true;
        }
        else if (msg.has(Done.class)) {
          if (msg.from().bareEq(owner())) {
            open = false;
            offer = null;
          }
          else {
            lastActive = false;
            open = false;
            participants.remove(bareSender);
          }
        }
        else if (msg.has(Cancel.class)) {
          if (msg.from().bareEq(owner())) {
            open = false;
            offer = null;
          }
          else {
            //noinspection StatementWithEmptyBody
            offer.addSlacker(bareSender);
            lastActive = false;
            participants.remove(bareSender);
          }
        }
        else if (msg.has(Message.Subject.class) || msg.has(Offer.class)) {
          offer = Offer.create(msg.to().bare(), bareSender, msg);
          owner = bareSender;
          participants.add(bareSender);
        }
      }
    }

    public boolean interview(JID worker) {
      return !offer.hasSlacker(worker);
    }

    public Offer offer() {
      return offer;
    }

    public Status() {
    }

    private Status(JID owner, Offer offer, JID lastWorker, boolean open, boolean lastActive, Set<JID> participants) {
      this.owner = owner;
      this.lastWorker = lastWorker;
      this.open = open;
      this.lastActive = lastActive;
      this.offer = offer;
      this.participants.addAll(participants);
    }

    public Status copy() {
      return new Status(owner, offer, lastWorker, open, lastActive, participants);
    }
  }

  // todo: look like XMPP.PresenceTracker but holds Statuses as values
  public static class PresenceTracker {
    private final Map<JID, Presence.Status> presenceMap = new HashMap<>();

    public boolean updatePresence(final Presence presence) {
      return !presence.status().equals(presenceMap.put(presence.from(), presence.status()));
    }
  }
}

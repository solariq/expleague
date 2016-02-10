package com.expleague.server.agents;

import akka.actor.ActorContext;
import com.expleague.model.Offer;
import com.expleague.model.Operations;
import com.expleague.model.Operations.Cancel;
import com.expleague.model.Operations.Done;
import com.expleague.model.Operations.Start;
import com.expleague.model.Operations.Sync;
import com.expleague.server.ExpertManager;
import com.expleague.server.dao.Archive;
import com.expleague.util.akka.AkkaTools;
import com.expleague.util.akka.UntypedActorAdapter;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Iq;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Message.MessageType;
import com.expleague.xmpp.stanza.Presence;
import com.expleague.xmpp.stanza.Stanza;
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
public class TBTSRoomAgent extends UntypedActorAdapter {
  private final List<Item> snapshot = new ArrayList<>();

  private final Map<JID, Presence.Status> presence = new HashMap<>();
  private final JID jid;
  private final Status status = new Status();

  public TBTSRoomAgent(JID jid) {
    this.jid = jid.bare();
    Archive.instance().visitMessages(jid.local(), new Archive.MessageVisitor() {
      @Override
      public boolean accept(String authorId, CharSequence message, long ts) {
        final Item item = Item.create(message);
        snapshot.add(item);
        if (item instanceof Stanza)
          status.accept((Stanza)item);
        return true;
      }
    });
    if (snapshot.isEmpty())
      invoke(new Message(jid, null, MessageType.GROUP_CHAT, "Welcome to room " + jid));
  }

  public void invoke(Operations.Resume resume) {
    if (status.isOpen())
      LaborExchange.reference(context()).tell(status.offer(), self());
  }

  public void invoke(Message msg) {
    if (msg.type() == MessageType.GROUP_CHAT && status.owner() != null && !status.isParticipant(msg.from())) {
      final Message message = new Message(jid, msg.from(), MessageType.GROUP_CHAT, "Сообщение от " + msg.from() + " не доставленно. Вы не являетесь участником задания! Известные участники: "
          + status.participants.stream().map(JID::toString).collect(Collectors.joining(", ")) + ".");
      message.append(msg);
      XMPP.send(message, context());
      return;
    }

    if (msg.has(Start.class) || msg.has(Operations.Resume.class)) {
      enterRoom(msg.from());
      XMPP.send(new Message(jid, status.owner(), msg.get(Operations.Command.class), ExpertManager.instance().profile(msg.from().bare())), context());
    }
    else if (msg.has(Cancel.class) || msg.has(Done.class)) {
      if (msg.from().bareEq(status.owner())) {
        LaborExchange.reference(context()).tell(msg.get(Operations.Command.class), self());
      }
      else if (msg.has(Cancel.class)) {
        XMPP.send(new Message(jid, status.owner(), msg.get(Operations.Command.class), ExpertManager.instance().profile(msg.from().bare())), context());
      }
    }
    else if (!msg.from().bareEq(status.owner()) && msg.body().startsWith("{\"type\":\"visitedPages\"")) {
      XMPP.send(new Message(jid, status.owner(), msg.body()), context());
    } else if (msg.has(Sync.class)) {
      final Sync sync = msg.get(Sync.class);
      System.out.println("sync event from expert: " + sync.func() + "\t" + sync.data());
    }
    log(msg);

    if (msg.from().bareEq(status.owner()) && !status.isOpen()) {
      final Offer offer = status.offer();
      if (offer != null) {
        invoke(new Message(XMPP.jid(), jid, new Operations.Create(), offer));
        LaborExchange.reference(context()).tell(offer.copy(), self());
      }
    }
    if (msg.type() == MessageType.GROUP_CHAT)
      broadcast(msg);
  }

  public void invoke(Presence presence) {
    if (status.owner() == null)
      enterRoom(presence.from());
    else if (!status.isParticipant(presence.from()))
      return;
    final JID from = presence.from();
    final Presence.Status currentStatus = this.presence.get(from);
    if (presence.status().equals(currentStatus))
      return;
    this.presence.put(from, presence.status());
    log(presence);
    broadcast(presence);
  }

  public void invoke(Iq command) {
    log(command);
    XMPP.send(Iq.answer(command), context());
    if (command.type() == Iq.IqType.SET)
      XMPP.send(new Message(jid, command.from(), "Room set up and unlocked."), context());
    status.accept(command);
  }

  private void broadcast(Stanza stanza) {
    for (final JID jid : status.participants) {
      if (jid.bareEq(stanza.from()))
        continue;

      final Stanza copy = stanza.copy();
      copy.to(jid);
      copy.from(roomAlias(stanza.from()));
      XMPP.send(copy, context());
    }
  }

  private void enterRoom(JID jid) {
    if (jid.bareEq(this.jid))
      return;
    if (!status.isParticipant(jid) || status.isWorker(jid)) {
      snapshot.stream().filter(s -> s instanceof Message && ((Message)s).type() == MessageType.GROUP_CHAT).map(s -> (Message)s).forEach(message -> {
        final Message copy = message.copy();
        copy.to(jid);
        copy.from(roomAlias(message.from()));
        XMPP.send(copy, context());
      });
    }
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
    public boolean lastActive;
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
      return open && lastWorker != null && !offer.hasSlacker(lastWorker);
    }

    public boolean isParticipant(JID jid) {
      return jid.local().isEmpty() || participants.contains(jid.bare());
    }

    private void accept(Stanza item) {
      if (item instanceof Message) {
        final Message msg = (Message) item;
        final JID bareSender = msg.from().bare();
        if (msg.has(Start.class)) {
          participants.add(bareSender);
          lastWorker = bareSender;
          offer.addWorker(bareSender);
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
}

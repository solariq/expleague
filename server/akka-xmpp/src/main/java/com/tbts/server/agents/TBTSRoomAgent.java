package com.tbts.server.agents;

import com.tbts.model.Offer;
import com.tbts.model.Operations;
import com.tbts.server.dao.Archive;
import com.tbts.util.akka.UntypedActorAdapter;
import com.tbts.xmpp.Item;
import com.tbts.xmpp.JID;
import com.tbts.xmpp.stanza.Iq;
import com.tbts.xmpp.stanza.Message;
import com.tbts.xmpp.stanza.Message.MessageType;
import com.tbts.xmpp.stanza.Presence;
import com.tbts.xmpp.stanza.Stanza;
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

  private final Set<JID> partisipants = new HashSet<>();
  private final Map<JID, Presence.Status> presence = new HashMap<>();
  private final JID jid;

  public TBTSRoomAgent(JID jid) {
    this.jid = jid.bare();
    Archive.instance().visitMessages(jid.local(), new Archive.MessageVisitor() {
      @Override
      public boolean accept(String authorId, CharSequence message, long ts) {
        snapshot.add(Item.create(message));
        return true;
      }
    });
    if (snapshot.isEmpty())
      invoke(new Message(jid, null, MessageType.GROUP_CHAT, "Welcome to room " + jid));
    partisipants.add(XMPP.jid().bare());
    if (owner() != null) {
      partisipants.add(owner);
    }
  }

  JID owner = null;
  @Nullable
  public JID owner() {
    if (owner != null)
      return owner;

    final Optional<Message> subject = snapshot.stream()
            .filter(s -> s instanceof Message).map(s -> (Message) s)
            .filter(m -> m.get(Message.Subject.class) != null)
            .findFirst();

    if (subject.isPresent()) {
      return owner = subject.get().from();
    }
    return null;
  }

  public void invoke(Operations.Resume resume) {
    if (isOpen())
      LaborExchange.reference(context()).tell(offer(), self());
  }

  private boolean isOpen() {
    boolean needToStart = false;
    for (final Item item : snapshot) {
      if (!(item instanceof Message))
        continue;
      if (((Message) item).has(Operations.Create.class))
        needToStart = true;
      if (((Message) item).has(Operations.Done.class))
        needToStart = false;
    }
    return needToStart;
  }

  public void invoke(Message msg) {
    if (msg.type() == MessageType.GROUP_CHAT) {
      if (owner() != null && !partisipant(msg.from())) {
        final Message message = new Message(jid, msg.from(), MessageType.GROUP_CHAT, "Сообщение от " + msg.from() + " не доставленно. Вы не являетесь участником задания! Известные участники: "
                + partisipants.stream().map(JID::toString).collect(Collectors.joining(", ")) + ".");
        message.append(msg);
        XMPP.send(message, context());
        return;
      }
      broadcast(msg);
    }
    log(msg);

    if (msg.has(Operations.Start.class)) {
      enterRoom(msg.from());
    }
    if (msg.has(Operations.Cancel.class) || msg.has(Operations.Done.class)) {
      exitRoom(msg.from());
    }

    if (msg.from().bareEq(owner()) && !isOpen()) {
      final Offer offer = offer();
      if (offer != null) {
        invoke(new Message(XMPP.jid(), jid, new Operations.Create(), offer));
        LaborExchange.reference(context()).tell(offer, self());
      }
    }
  }

  public void invoke(Presence presence) {
    if (owner() != null && !partisipant(presence.from()))
      return;
    enterRoom(presence.from());
    log(presence);
    final JID from = presence.from();
    final Presence.Status currentStatus = this.presence.get(from);
    if (presence.status().equals(currentStatus))
      return;
    this.presence.put(from, presence.status());
    broadcast(presence);
  }

  public void invoke(Iq command) {
    log(command);
    XMPP.send(Iq.answer(command), context());
    if (command.type() == Iq.IqType.SET)
      XMPP.send(new Message(jid, command.from(), "Room set up and unlocked."), context());
  }

  @Nullable
  private Offer offer() {
    final Optional<Message> subject = snapshot.stream()
            .filter(s -> s instanceof Message).map(s -> (Message) s)
            .filter(m -> m.get(Message.Subject.class) != null)
            .findFirst();
    if (subject.isPresent()) {
      final Offer offer = new Offer(jid, subject.get().from(), subject.get().get(Message.Subject.class));
      final List<JID> workers = new ArrayList<>();
      workers(workers);
      workers.stream().map(JID::bare).forEach(offer::addWorker);
      return offer;
    }
    return null;
  }

  private void broadcast(Stanza stanza) {
    for (final JID jid : partisipants) {
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
    if (!partisipants.contains(jid.bare())) {
      partisipants.add(jid.bare());
      snapshot.stream().filter(s -> s instanceof Message && ((Message)s).type() == MessageType.GROUP_CHAT).map(s -> (Message)s).forEach(message -> {
        final Message copy = message.copy();
        copy.to(jid);
        copy.from(roomAlias(message.from()));
        XMPP.send(copy, context());
      });
    }
  }

  private void exitRoom(JID jid) {
    partisipants.remove(jid.bare());
  }

  private boolean partisipant(JID jid) {
    return partisipants.contains(jid.bare());
  }

  @NotNull
  private JID roomAlias(JID from) {
    return new JID(this.jid.local(), this.jid.domain(), from.local());
  }

  public void log(Stanza stanza) { // saving everything to archive
    snapshot.add(stanza);
    Archive.instance().log(jid.local(), stanza.from().toString(), stanza.xmlString());
  }

  public void invoke(Class<?> c) {
    if (Status.class.equals(c)) {
      Status result = new Status();
      final List<JID> workers = new ArrayList<>();
      boolean lastActive = workers(workers);
      result.workers = workers.toArray(new JID[workers.size()]);
      result.open = isOpen();
      result.lastActive = lastActive;
      sender().tell(result, self());
    }
    else unhandled(c);
  }

  private boolean workers(List<JID> workers) {
    boolean lastActive = false;
    for (final Item item : snapshot) {
      if (item instanceof Message) {
        final Message msg = (Message) item;
        if (msg.has(Operations.Start.class)) {
          workers.add(msg.from());
          lastActive = true;
        }
        else if (msg.has(Operations.Done.class)) {
          lastActive = false;
        }
        else if (msg.has(Operations.Cancel.class)) {
          //noinspection StatementWithEmptyBody
          while(workers.remove(msg.from()));
          lastActive = false;
        }
      }
    }
    return lastActive;
  }

  public static class Status {
    private JID[] workers;
    private boolean open;
    public boolean lastActive;

    public JID[] workers() {
      return workers;
    }

    @Nullable
    public JID lastWorker() {
      return workers.length > 0 ? workers[workers.length - 1] : null;
    }

    public boolean isOpen() {
      return open;
    }

    public boolean isLastWorkerActive() {
      return lastActive;
    }
  }
}

package com.expleague.expert.xmpp;

import com.expleague.expert.xmpp.chat.ChatItem;
import com.expleague.expert.xmpp.events.*;
import com.expleague.model.Offer;
import com.expleague.model.Operations;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.stanza.Message;
import com.spbsu.commons.io.StreamTools;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Date;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Experts League
 * Created by solar on 11/02/16.
 */
public class ExpertTask {
  private static final Logger log = Logger.getLogger(ExpertTask.class.getName());
  final Writer communicationLog;
  private final ExpLeagueMember owner;
  private final Consumer<ExpertEvent> eventsReceiver;
  private final File root;
  private final Offer offer;

  public ExpertTask(ExpLeagueMember owner, Consumer<ExpertEvent> eventsReceiver, File root, Offer offer) throws IOException {
    this.owner = owner;
    this.eventsReceiver = eventsReceiver;
    this.root = root;
    this.offer = offer;
    if (!root.exists() && !root.mkdirs())
      throw new IOException("Unable to make task root directory!");
    StreamTools.writeChars(offer.xmlString(), new File(root, "offer.xml"));
    final File stateFile = new File(root, "state");
    if (stateFile.exists())
      state(State.valueOf(StreamTools.readFile(stateFile).toString().trim()));
    else
      state(State.SUSPEND);
    communicationLog = new FileWriter(new File(root, "communication.log"), true);
    items.filtered(ChatItem::outgoing).addListener((ListChangeListener<ChatItem>) c -> {
      final Message msg = new Message(owner.jid(), offer.room(), Message.MessageType.GROUP_CHAT);
      c.getAddedSubList().stream().map(ChatItem::toMessage).forEach(msg::append);
      ExpLeagueConnection.instance().send(msg);
      log(msg);
      eventsReceiver.accept(new ChatMessageEvent(this, msg, false));
    });
  }

  public void processCommand(Operations.Command command) {
    log(command);
    if (command instanceof Operations.Invite) {
      final Operations.Invite invite = (Operations.Invite) command;
      eventsReceiver.accept(new TaskInviteEvent(invite, this, invite.timeout));
      state(State.INVITE);
    }
    else if (command instanceof Operations.Cancel) {
      switch (state) {
        case INVITE:
          eventsReceiver.accept(new TaskSuspendedEvent(command, this));
          state(State.SUSPEND);
          break;
        case SUSPEND:
        case BUSY:
          eventsReceiver.accept(new TaskClosedEvent(command, this));
          state(State.CLOSED);
          break;
      }
    }
    else if (command instanceof Operations.Resume) {
      state(State.BUSY);
      ExpLeagueConnection.instance().send(new Message(owner.jid(), owner.system(), new Operations.Resume()));
    }
  }

  private ObservableList<ChatItem> items = new SimpleListProperty<>();
  public ObservableList<ChatItem> itemsProperty() {
    return items;
  }

  public Offer offer() {
    return offer;
  }
  
  public void accept() {
    if (state != State.INVITE) 
      throw new IllegalStateException();
    final Operations.Start start = new Operations.Start();
    ExpLeagueConnection.instance().send(new Message(owner.jid(), owner.system(), start));
    state(State.BUSY);
    eventsReceiver.accept(new TaskAcceptedEvent(start, this));
    log(start);
  }

  public void cancel() {
    final Operations.Cancel cancel = new Operations.Cancel();
    ExpLeagueConnection.instance().send(new Message(owner.jid(), owner.system(), cancel));
    state(State.CLOSED);
    eventsReceiver.accept(new TaskClosedEvent(cancel, this));
    log(cancel);
  }

  public void suspend() {
    if (state != State.BUSY)
      return;
    final Operations.Suspend suspend = new Operations.Suspend();
    state(State.SUSPEND);
    eventsReceiver.accept(new TaskSuspendedEvent(suspend, this));
    log(suspend);
  }

  private State state;
  private void state(State state) {
    this.state = state;
    try {
      StreamTools.writeChars(state.name(), new File(root, "state"));
    }
    catch (IOException e) {
      log.log(Level.SEVERE, "Unable to save state", e);
    }
  }

  public State state() {
    return state;
  }

  public void receive(Message message) {
    log(message);
    final ChatMessageEvent event = new ChatMessageEvent(this, message, true);
    eventsReceiver.accept(event);
  }

  private void log(Item item) {
    try {
      communicationLog.append(new Date().toString());
      communicationLog.append("\t");
      communicationLog.append(item.xmlString());
      communicationLog.append("\n");
      communicationLog.flush();
    }
    catch (IOException e) {
      log.log(Level.WARNING, "Unable to save message to " + item.xmlString() + " to communication log");
    }
  }

  enum State {
    INVITE,
    BUSY,
    SUSPEND,
    CLOSED,
  }
}

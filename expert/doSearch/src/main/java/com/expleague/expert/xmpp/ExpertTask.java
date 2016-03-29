package com.expleague.expert.xmpp;

import com.expleague.expert.forms.AnswerViewController;
import com.expleague.expert.xmpp.events.*;
import com.expleague.model.*;
import com.expleague.model.patch.Patch;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
import com.spbsu.commons.io.StreamTools;
import com.spbsu.commons.util.FileThrottler;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
  private ObservableList<Patch> patches = FXCollections.observableArrayList(new ArrayList<>());
  private int patchIndex = 0;

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

    final File patchworkFile = new File(root, "patchwork.md");
    if (patchworkFile.exists())
      patchwork = StreamTools.readFile(patchworkFile).toString();

    final File patchesRoot =  new File(root, "patches");
    if (patchesRoot.exists()) {
      //noinspection ConstantConditions
      for (final File patchFile: patchesRoot.listFiles()) {
        final CharSequence patchText = StreamTools.readFile(patchFile);
        final Patch patch = (Patch) Patch.create(patchText);
        patch.file(patchFile);
        patches.add(patch);
        final String patchFileName = patchFile.getName();
        final String index = patchFileName.substring(0, patchFileName.length() - ".xml".length());
        try {
          patchIndex = Math.max(patchIndex, Integer.parseInt(index) + 1);
        }
        catch (Exception ignored) {}
      }
    }
    //noinspection ResultOfMethodCallIgnored
    patchesRoot.mkdirs();
    patches.addListener((ListChangeListener<Patch>) c -> {
      while(c.next()) {
        if (c.wasAdded()) {
          for (Patch patch : c.getAddedSubList()) {
            final File file = new File(patchesRoot, (patchIndex++) + ".xml");
            patch.file(file);
            try {
              StreamTools.writeChars(patch.xmlString(), file);
            }
            catch (IOException e) {
              log.log(Level.SEVERE, "Unable to save patch to: " + file.getAbsolutePath());
            }
          }
        }
        else if (c.wasRemoved()) {
          c.getRemoved().stream().forEach(p -> p.file().delete());
        }
      }
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
          eventsReceiver.accept(new TaskInviteCanceledEvent(command, this));
          eventsReceiver.accept(new TaskSuspendedEvent(command, this));
          state(State.SUSPEND);
          break;
        case SUSPEND:
        case BUSY:
          eventsReceiver.accept(new TaskCanceledEvent(command, this));
          state(State.CLOSED);
          break;
      }
    }
    else if (command instanceof Operations.Progress) {
      final Operations.Progress progress = (Operations.Progress) command;

      if (progress.hasAssigned()) {
        tags.clear();
        progress.assigned().forEach(tags::add);
        eventsReceiver.accept(new TaskTagsAssignedEvent(progress, this));
      }
      else if (progress.phone() != null) {
        calls.add(progress.phone());
        eventsReceiver.accept(new TaskCallEvent(progress, this));
      }
    }
    else if (command instanceof Operations.Resume) {
      state(State.BUSY);
      eventsReceiver.accept(new TaskResumedEvent(offer, this));
      ExpLeagueConnection.instance().send(new Message(owner.system(), new Operations.Resume()));
    }
  }

  public Offer offer() {
    return offer;
  }
  
  public void accept() {
    if (state != State.INVITE) 
      throw new IllegalStateException();
    final Operations.Start start = new Operations.Start();
    ExpLeagueConnection.instance().send(new Message(owner.system(), start));
    state(State.BUSY);
    eventsReceiver.accept(new TaskAcceptedEvent(start, this));
    log(start);
  }

  public void cancel() {
    final Operations.Cancel cancel = new Operations.Cancel();
    ExpLeagueConnection.instance().send(new Message(owner.system(), cancel));
    state(State.CLOSED);
    eventsReceiver.accept(new TaskInviteCanceledEvent(cancel, this));
    eventsReceiver.accept(new TaskSuspendedEvent(cancel, this));
    log(cancel);
  }

  public void suspend() {
    if (state != State.BUSY && state != State.INVITE)
      return;
    final Operations.Suspend suspend = new Operations.Suspend();
    if (state == State.INVITE)
      eventsReceiver.accept(new TaskInviteCanceledEvent(new Operations.Cancel(), this));
    state(State.SUSPEND);
    eventsReceiver.accept(new TaskSuspendedEvent(suspend, this));
    log(suspend);
  }

  public void send(String msg) {
    ExpLeagueConnection.instance().send(new Message(offer.room(), Message.MessageType.GROUP_CHAT, msg));
  }

  public void progress(String item) {
    ExpLeagueConnection.instance().send(new Message(offer.room(), item));
  }

  public void answer() {
    final Answer answer = new Answer(patchwork());
    ExpLeagueConnection.instance().send(new Message(offer.room(), Message.MessageType.GROUP_CHAT, answer));
    patchwork("");
    state(State.CLOSED);
    eventsReceiver.accept(new TaskSuspendedEvent(answer, this));
    log(answer);
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

  public ObservableList<Patch> patchesProperty() {
    return patches;
  }

  private AnswerViewController editor;
  public AnswerViewController editor() {
    return editor;
  }

  public void editor(AnswerViewController editor) {
    this.editor = editor;
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

  private String patchwork = "";

  public void patchwork(String newValue) {
    patchwork = newValue;
    final File file = new File(root, "patchwork.md");
    throttler.schedule(file, 1000, () -> {
      try {
        StreamTools.writeChars(patchwork, file);
      }
      catch (IOException e) {
        log.log(Level.SEVERE, "Unable to save patchwork: " + file + ".");
      }
    });
  }

  public String patchwork() {
    return patchwork;
  }

  public JID owner() {
    return owner.jid();
  }

  public JID client() {
    return offer().client();
  }

  private final List<Tag> tags = new ArrayList<>();
  public void tag(Tag tag) {
    tags.add(tag);
    final Operations.Progress progress = new Operations.Progress(tags.toArray(new Tag[tags.size()]));
    ExpLeagueConnection.instance().send(new Message(offer.room(), Message.MessageType.NORMAL, progress));
    eventsReceiver.accept(new TaskTagsAssignedEvent(progress, this));
  }

  public void untag(Tag tag) {
    tags.remove(tag);
    final Operations.Progress progress = new Operations.Progress(tags.toArray(new Tag[tags.size()]));
    ExpLeagueConnection.instance().send(new Message(offer.room(), Message.MessageType.NORMAL, progress));
    eventsReceiver.accept(new TaskTagsAssignedEvent(progress, this));
  }

  public List<Tag> tags() {
    return tags;
  }

  private final List<String> calls = new ArrayList<>();
  public void call(String phone) {
    calls.add(phone);
    final Operations.Progress progress = new Operations.Progress(phone);
    ExpLeagueConnection.instance().send(new Message(offer.room(), Message.MessageType.NORMAL, progress));
    eventsReceiver.accept(new TaskCallEvent(progress, this));
  }

  public enum State {
    INVITE,
    BUSY,
    SUSPEND,
    CLOSED,
  }

  private static FileThrottler throttler = new FileThrottler();
}

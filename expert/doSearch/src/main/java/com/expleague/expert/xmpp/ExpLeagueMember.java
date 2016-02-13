package com.expleague.expert.xmpp;

import com.expleague.expert.profile.UserProfile;
import com.expleague.expert.xmpp.events.CheckEvent;
import com.expleague.expert.xmpp.events.TaskResumedEvent;
import com.expleague.expert.xmpp.events.TaskStartedEvent;
import com.expleague.expert.xmpp.events.TaskSuspendedEvent;
import com.expleague.model.Offer;
import com.expleague.model.Operations;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Iq;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Stanza;
import com.spbsu.commons.func.Action;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import org.jetbrains.annotations.Nullable;
import tigase.jaxmpp.core.client.Connector;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.stanzas.StreamPacket;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Experts League
 * Created by solar on 10/02/16.
 */
public class ExpLeagueMember extends WeakListenerHolderImpl<ExpertEvent> implements Connector.StanzaReceivedHandler{
  private static final Logger log = Logger.getLogger(ExpLeagueMember.class.getName());
  private final UserProfile profile;
  private ExpertTask task;

  @SuppressWarnings("FieldCanBeLocal")
  private final Action<ExpLeagueConnection.Status> connectionListener = status -> {
    if (status == ExpLeagueConnection.Status.DISCONNECTED && task != null) {
      task.suspend();
      task = null;
    }
  };

  public ExpLeagueMember(UserProfile profile) {
    this.profile = profile;
    ExpLeagueConnection.instance().addListener(connectionListener);
  }

  public JID jid() {
    final String id = profile.get(UserProfile.Key.EXP_LEAGUE_ID);
    return JID.parse(id);
  }

  public void processPacket(Stanza packet) {
    if (packet instanceof Iq)
      return;
    if (packet instanceof Message) {
      final Message message = (Message)packet;
      if (message.has(Offer.class) || message.has(Operations.Command.class)) {
        if (message.has(Operations.Invite.class) || message.has(Operations.Resume.class)) {
          if (task == null){
            Offer offer = message.get(Offer.class);
            try {
              this.task = new ExpertTask(this, this::invoke, profile.allocateTaskDir(offer.room().local()), offer);
              invoke(message.has(Operations.Invite.class) ? new TaskStartedEvent(offer, task) : new TaskResumedEvent(offer, task));
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
            task.processCommand(message.get(Operations.Command.class));
          }
          else log.severe("New task received while having active: " + task + " ignoring invitation!");
        }
        else if (!message.has(Operations.Command.class)){
          invoke(new CheckEvent(message));
          ExpLeagueConnection.instance().send(new Message(jid(), system(), new Operations.Ok()));
        }
        else if (task != null) {
          task.processCommand(message.get(Operations.Command.class));
        }
        else {
          log.severe("Command received: "+ message.get(Operations.Command.class) + " while no active tasks exist");
        }
      }
      else if (task != null) {
        task.receive(message);
      }
    }
  }

  @Nullable
  public ExpertTask task() {
    return task;
  }

  @Override
  protected void invoke(ExpertEvent e) {
    super.invoke(e);
    if (e instanceof TaskSuspendedEvent) {
      task = null;
    }
  }

  @Override
  public void onStanzaReceived(SessionObject sessionObject, StreamPacket streamPacket) {
    try {
      final Item item = Item.create(streamPacket.getAsString());
      if (item instanceof Stanza)
        processPacket((Stanza) item);
    }
    catch (XMLException e) {
      log.log(Level.SEVERE, "Unable to parse incoming message", e);
      throw new RuntimeException(e);
    }
  }

  public JID system() {
    return JID.parse(profile.get(UserProfile.Key.EXP_LEAGUE_DOMAIN));
  }
}

package com.expleague.expert.xmpp;

import com.expleague.expert.profile.UserProfile;
import com.expleague.expert.xmpp.events.*;
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

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Experts League
 * Created by solar on 10/02/16.
 */
public class ExpLeagueMember extends WeakListenerHolderImpl<ExpertEvent> {
  private static final Logger log = Logger.getLogger(ExpLeagueMember.class.getName());
  private final UserProfile profile;
  private ExpertTask task;

  @SuppressWarnings("FieldCanBeLocal")
  private final Action<ExpLeagueConnection.Status> connectionListener = status -> {
    if (status == ExpLeagueConnection.Status.DISCONNECTED && task != null) {
      task.suspend();
    }
  };

  public ExpLeagueMember(UserProfile profile) {
    this.profile = profile;
    ExpLeagueConnection.instance().addListener(connectionListener);
  }

  public JID jid() {
    return profile.get(UserProfile.Key.EXP_LEAGUE_ID);
  }

  boolean check = false;
  public void processPacket(Stanza packet) {
    log.info(">" + packet.from());
    if (packet instanceof Iq)
      return;
    if (packet instanceof Message) {
      final Message message = (Message)packet;
      if (message.has(Offer.class) || message.has(Operations.Command.class)) {
        if (message.has(Operations.Invite.class) || message.has(Operations.Resume.class)) {
          check = false;
          if (task == null){
            Offer offer = message.get(Offer.class);
            try {
              this.task = new ExpertTask(this, this::invoke, profile.allocateTaskDir(offer.room().local()), offer);
              invoke(new TaskStartedEvent(offer, task));
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
            task.processCommand(message.get(Item.class));
          }
          else log.severe("New task received while having active: " + task.offer() + " ignoring invitation!");
        }
        else if (!message.has(Operations.Command.class)){
          invoke(new CheckEvent(message));
          ExpLeagueConnection.instance().send(new Message(system(), new Operations.Ok()));
          check = true;
        }
        else if (task != null) {
          task.processCommand(message.get(Item.class));
        }
        else if (check && message.has(Operations.Cancel.class)) {
          invoke(new CheckCanceledEvent(message));
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

  public JID system() {
    return JID.parse(profile.get(UserProfile.Key.EXP_LEAGUE_DOMAIN));
  }

  public void jid(JID boundJid) {
    profile.set(UserProfile.Key.EXP_LEAGUE_ID, boundJid);
  }
}

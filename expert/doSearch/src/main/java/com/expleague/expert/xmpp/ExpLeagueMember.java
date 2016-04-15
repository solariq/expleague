package com.expleague.expert.xmpp;

import com.expleague.expert.profile.UserProfile;
import com.expleague.expert.xmpp.events.CheckCanceledEvent;
import com.expleague.expert.xmpp.events.CheckEvent;
import com.expleague.expert.xmpp.events.TaskStartedEvent;
import com.expleague.expert.xmpp.events.TaskSuspendedEvent;
import com.expleague.model.Offer;
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

import static com.expleague.model.Operations.*;

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
      if (message.has(Offer.class)) {
        if (message.has(Invite.class) || message.has(Resume.class)) {
          check = false;
          if (task == null) {
            Offer offer = message.get(Offer.class);
            try {
              this.task = new ExpertTask(this, this::invoke, profile.allocateTaskDir(offer.room().local()), offer);
              invoke(new TaskStartedEvent(offer, task));
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
            task.processCommand(message.get(Command.class));
          } else log.severe("New task received while having active: " + task.offer() + " ignoring invitation!");
        }
        else {
          invoke(new CheckEvent(message));
          ExpLeagueConnection.instance().send(new Message(system(), new Ok()));
          check = true;
        }
      }
      else if (message.has(Command.class) || message.has(Progress.class)) {
        if (task != null) {
          task.processCommand(message.get(Item.class));
        }
        else if (check && message.has(Cancel.class)) {
          invoke(new CheckCanceledEvent(message));
        }
        else {
          log.severe("Command received: " + message.get(Command.class) + " while no active tasks exist");
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

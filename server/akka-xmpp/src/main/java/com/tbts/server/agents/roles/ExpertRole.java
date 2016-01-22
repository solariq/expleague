package com.tbts.server.agents.roles;

import akka.actor.AbstractFSM;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.util.Timeout;
import com.tbts.model.ExpertManager;
import com.tbts.model.Offer;
import com.tbts.model.Operations;
import com.tbts.server.agents.LaborExchange;
import com.tbts.server.agents.XMPP;
import com.tbts.util.akka.AkkaTools;
import com.tbts.xmpp.JID;
import com.tbts.xmpp.stanza.Message;
import com.tbts.xmpp.stanza.Presence;
import com.tbts.xmpp.stanza.Stanza;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 17.12.15
 * Time: 14:16
 */
public class ExpertRole extends AbstractFSM<ExpertRole.State, ExpertRole.Task> {
  private static final Logger log = Logger.getLogger(ExpertRole.class.getName());
  public static final FiniteDuration CHOICE_TIMEOUT = Duration.create(60, TimeUnit.SECONDS);
  public static final FiniteDuration CHECK_TIMEOUT = Duration.create(10, TimeUnit.SECONDS);
  public static final FiniteDuration INVITE_TIMEOUT = Duration.create(5, TimeUnit.MINUTES);
  private Cancellable timer;

  {
    startWith(State.OFFLINE, null);

    when(State.OFFLINE,
        matchEvent(
            Presence.class,
            (presence, task) -> presence.available(),
            (presence, zero) -> zero == null ? goTo(State.READY) : goTo(State.BUSY)
        ).event(Operations.Resume.class,
            (resume, zero) -> zero == null,
            (resume, zero) -> stay().using(new Task().appendVariant(resume.offer(), sender()))
        ));
    when(State.READY,
        matchEvent(
            Offer.class,
            (offer, task) -> {
              return goTo(State.CHECK).using((task != null ? task : new Task()).appendVariant(offer, sender()));
            }
        ).event(
            Presence.class,
            (presence, task) -> presence.available() ? stay() : goTo(State.OFFLINE)
        ).event(Operations.Resume.class,
            (resume, zero) -> zero == null,
            (resume, zero) -> goTo(State.BUSY).using(new Task().appendVariant(resume.offer(), sender()))
        ).event(Timeout.class,
            (to, zero) -> {
              final Task task = new Task();
              task.chosen = true;
              return stay().using(task);
            }
        )
    );
    when(State.CHECK,
        matchEvent( // expert accepted check
            Message.class,
            (msg, task) -> msg.get(Operations.Ok.class) != null,
            (msg, task) -> {
              task.broker().tell(new Operations.Ok(), self());
              timer.cancel();
              timer = null;
              return stay().using(task);
            }
        ).event( // expert canceled check
            (msg, task) -> msg instanceof Message && ((Message) msg).get(Operations.Cancel.class) != null,
            (msg, task) -> {
              task.broker().tell(new Operations.Cancel(), self());
              timer.cancel();
              timer = null;
              return goTo(State.READY).using(null);
            }
        ).event( // expert canceled check
            (msg, task) -> msg instanceof Timeout || (msg instanceof Message && ((Message) msg).get(Operations.Cancel.class) != null),
            (msg, task) -> {
              timer = null;
              if (task.chosen()) {
                XMPP.send(new Message(XMPP.jid(), jid(), task.offer(), new Operations.Cancel()), context());
                task.broker().tell(new Operations.Cancel(), self());
                return goTo(State.READY).using(null);
              }
              else {
                timer = AkkaTools.scheduleTimeout(context(), CHECK_TIMEOUT, self());
                XMPP.send(new Message(XMPP.jid(), jid(), task.choose()), context());
                return stay();
              }
            }
        ).event( // broker sent invitation
            Operations.Invite.class,
            (invite, task) -> task.broker().equals(sender()),
            (invite, task) -> {
              final Message message = new Message(task.offer().room(), jid(), task.offer());
              invite.form(message, task.offer());
              XMPP.send(message, context());
              timer = AkkaTools.scheduleTimeout(context(), INVITE_TIMEOUT, self());
              return goTo(State.INVITE);
            }
        ).event(
            Presence.class,
            (presence, task) -> !presence.available(),
            (presence, task) -> {
              if (task != null)
                task.broker().tell(new Operations.Cancel(), self());
              timer.cancel();
              timer = null;
              return goTo(State.OFFLINE).using(null);
            }
        ).event(
            Presence.class,
            (presence, task) -> presence.available(),
            (presence, task) -> stay()
        ).event(
            Offer.class,
            (offer, task) -> stay().using(task.appendVariant(offer, sender()))
        )
    );
    when(State.INVITE,
        matchEvent(
            Stanza.class,
            (stanza, task) -> {
              if (task.offer().room().bareEq(stanza.to())) {
                timer.cancel();
                timer = null;
                if (stanza instanceof Message && ((Message) stanza).has(Operations.Cancel.class)) {
                  task.broker().tell(new Operations.Cancel(), self());
                  return goTo(State.READY).using(null);
                }
                else {
                  task.broker().tell(new Operations.Start(), self());
                  XMPP.send(new Message(jid(), task.offer().room(), new Operations.Start()), context());
                  return goTo(State.BUSY);
                }
              }
              return stay();
            }
        ).event(
            Timeout.class,
            (timeout, task) -> {
              timer = null;
              XMPP.send(new Message(XMPP.jid(), jid(), task.offer(), new Operations.Cancel()), context());
              task.broker().tell(new Operations.Cancel(), self());
              return goTo(State.READY).using(null);
            }
        )
    );
    when(State.BUSY,
        matchEvent(
            Presence.class,
            (presence, task) -> {
              if (presence.available() && presence.to() == null) {
                XMPP.send(new Message(task.offer().room(), jid(), new Operations.Resume(task.offer())), context());
                XMPP.send(new Message(jid(), task.offer().room(), Message.MessageType.GROUP_CHAT, new Operations.Resume(task.offer())), context());
                task.broker().tell(new Operations.Resume(task.offer()), self());
              }
              else if (!presence.available()) {
                XMPP.send(new Message(jid(), task.offer().room(), Message.MessageType.GROUP_CHAT, new Operations.Suspend()), context());
                task.broker().tell(new Operations.Suspend(), self());
              }
              return stay();
            }
        ).event(Message.class,
            (msg, task) -> {
              if (msg.get(Operations.Cancel.class) != null) {
                task.broker().tell(new Operations.Cancel(), self());
              }
              return goTo(State.READY).using(null);
            }
        )
    );

    whenUnhandled(
        matchEvent(Operations.Resume.class,
            (resume, task) -> {
              XMPP.send(new Message(jid(), resume.offer().room(), new Operations.Done()), context());
              return stay().replying(new Operations.Cancel());
            }
        ).event(Offer.class,
            (offer, task) -> stay().replying(new Operations.Cancel())
        )
    );

//    whenUnhandled(matchAnyEvent((state, data) -> stay().replying(new Operations.Cancel())));

    onTransition((from, to) -> {
      final Offer first = nextStateData() != null && nextStateData().chosen() ? nextStateData().offer() : null;
      ExpertManager.instance().record(jid()).entry(first, to);
      if (from != to)
        context().parent().tell(to, self());
    });

    onTransition((from, to) -> {
      final Offer first = nextStateData() != null && nextStateData().chosen()? nextStateData().offer() : null;
      log.fine(from + " -> " + to + (first != null ? " " + first : ""));
    });

    onTransition((from, to) -> {
      if (to == State.READY) {
        timer = AkkaTools.scheduleTimeout(context(), CHOICE_TIMEOUT, self());
        LaborExchange.reference(context()).tell(self(), self());
      }
    });
    initialize();
  }

  public JID jid() {
    return JID.parse(self().path().name());
  }

  public class Task {
    private List<Offer> offers = new ArrayList<>();
    private List<ActorRef> brokers = new ArrayList<>();
    boolean chosen = false;

    public Task appendVariant(Offer offer, ActorRef broker) {
      offers.add(offer);
      brokers.add(broker);
      return this;
    }

    public Offer choose() {
      for (int i = 0; i < offers.size(); i++) {
        final Offer offer = offers.get(i);
        if (offer.hasWorker(jid())) {
          offers = Collections.singletonList(offer);
          brokers = Collections.singletonList(brokers.get(i));
          return offer;
        }
      }
      offers = Collections.singletonList(offers.get(0));
      brokers = Collections.singletonList(brokers.get(0));
      return offers.get(0);
    }

    public boolean chosen() {
      return chosen;
    }

    public ActorRef broker() {
      if (brokers.size() != 1)
        throw new IllegalStateException("Multiple tasks on state when broker is needed");
      return brokers.get(0);
    }

    public Offer offer() {
      if (offers.size() != 1)
        throw new IllegalStateException("Multiple tasks on state when offer is needed");
      return offers.get(0);
    }
  }

  public enum State {
    CHECK,
    INVITE,
    BUSY,
    READY,
    OFFLINE,
  }
}

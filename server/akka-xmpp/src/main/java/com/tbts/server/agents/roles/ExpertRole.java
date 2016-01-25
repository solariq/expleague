package com.tbts.server.agents.roles;

import akka.actor.AbstractFSM;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.FSM;
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

import java.util.*;
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

  @Override
  public void logTermination(Reason reason) {
    super.logTermination(reason);
    log.warning(reason.toString());
  }

  {
    startWith(State.OFFLINE, new Task(true));

    when(State.OFFLINE,
        matchEvent(
            Presence.class,
            (presence, task) -> presence.available(),
            (presence, task) -> task.isEmpty() ? goTo(State.READY).using(new Task(true)) : goTo(State.BUSY)
        ).event(Operations.Resume.class,
            (resume, task) -> stay().using(task.appendVariant(resume.offer(), sender()))
        ));
    when(State.READY,
        matchEvent(
            Offer.class,
            (offer, task) -> {
              task.appendVariant(offer, sender());
              if (task.immediate() || offer.hasWorker(jid()))
                task.choose();
              return goTo(State.CHECK);
            }
        ).event(
            Presence.class,
            (presence, task) -> presence.available() ? stay() : goTo(State.OFFLINE)
        ).event(Operations.Resume.class,
            (resume, zero) -> zero == null,
            (resume, zero) -> goTo(State.BUSY).using(new Task(true).appendVariant(resume.offer(), sender()))
        ).event(Timeout.class,
            (to, zero) -> {
              return stay().using(new Task(true));
            }
        )
    );
    when(State.CHECK,
        matchEvent( // expert accepted check
            Message.class,
            (msg, task) -> msg.get(Operations.Ok.class) != null,
            (msg, task) -> {
              task.broker().tell(new Operations.Ok(), self());
              stopTimer();
              return stay().using(task);
            }
        ).event(
            Message.class, // expert canceled check
            (msg, task) -> msg.get(Operations.Cancel.class) != null,
            (msg, task) -> {
              task.broker().tell(new Operations.Cancel(), self());
              stopTimer();
              return goTo(State.READY).using(new Task(true));
            }
        ).event(Timeout.class, // expert check timed out
            (msg, task) -> {
              timer = null;
              if (task.chosen()) { // CHECK_TIMEOUT
                XMPP.send(new Message(XMPP.jid(), jid(), task.offer(), new Operations.Cancel()), context());
                task.broker().tell(new Operations.Cancel(), self());
                return goTo(State.READY).using(new Task(false));
              }
              task.choose();
              return stay();
            }
        ).event( // broker sent invitation
            Operations.Invite.class,
            (invite, task) -> task.broker().equals(sender()),
            (invite, task) -> {
              final Message message = new Message(task.offer().room(), jid(), task.offer());
              invite.form(message, task.offer());
              XMPP.send(message, context());
              stopTimer();
              timer = AkkaTools.scheduleTimeout(context(), INVITE_TIMEOUT, self());
              return goTo(State.INVITE);
            }
        ).event( // broker canceled offer
            Operations.Cancel.class,
            (invite, task) -> task.broker().equals(sender()),
            (invite, task) -> {
              return goTo(State.READY).using(new Task(true));
            }
        ).event(
            Presence.class,
            (presence, task) -> !presence.available(),
            (presence, task) -> {
              if (!task.isEmpty()) {
                task.choose();
                task.broker().tell(new Operations.Cancel(), self());
              }
              stopTimer();
              return goTo(State.OFFLINE).using(new Task(true));
            }
        ).event(
            Presence.class,
            (presence, task) -> presence.available(),
            (presence, task) -> stay()
        ).event(
            Offer.class,
            (offer, task) -> {
              task.appendVariant(offer, sender());
              if (offer.hasWorker(jid())){
                stopTimer();
                task.choose();
              }
              return stay();
            }
        )
    );
    when(State.INVITE,
        matchEvent(
            Presence.class,
            (presence, task) -> presence.available() && task.offer().room().bareEq(presence.to()),
            (stanza, task) -> {
              if (task.offer().room().bareEq(stanza.to())) { // old way
                stopTimer();
                task.broker().tell(new Operations.Start(), self());
                XMPP.send(new Message(jid(), task.offer().room(), new Operations.Start()), context());
                return goTo(State.BUSY);
              }
              return stay();
            }
        ).event(
            Message.class,
            (message, task) -> {
              if (message.has(Operations.Cancel.class)) {
                stopTimer();
                task.broker().tell(new Operations.Cancel(), self());
                return goTo(State.READY).using(new Task(true));
              }
              if (message.has(Operations.Start.class)) {
                stopTimer();
                task.broker().tell(new Operations.Start(), self());
                XMPP.send(new Message(jid(), task.offer().room(), new Operations.Start()), context());
                return goTo(State.BUSY);
              }
              return stay();
            }
        ).event(
            Timeout.class,
            (timeout, task) -> {
              timer = null;
              XMPP.send(new Message(XMPP.jid(), jid(), task.offer(), new Operations.Cancel()), context());
              task.broker().tell(new Operations.Cancel(), self());
              return goTo(State.READY).using(new Task(false));
            }
        )
    );
    when(State.BUSY,
        matchEvent(
            Presence.class,
            (presence, task) -> {
              if (presence.available() && presence.to() == null) {
                XMPP.send(new Message(task.offer().room(), jid(), new Operations.Resume(task.offer())), context());
                XMPP.send(new Message(jid(), task.offer().room(), new Operations.Resume(task.offer())), context());
                task.broker().tell(new Operations.Resume(task.offer()), self());
              }
              else if (!presence.available()) {
                XMPP.send(new Message(jid(), task.offer().room(), new Operations.Suspend()), context());
                task.broker().tell(new Operations.Suspend(), self());
              }
              return stay();
            }
        ).event(Message.class,
            (msg, task) -> {
              if (msg.get(Operations.Cancel.class) != null) {
                task.broker().tell(new Operations.Cancel(), self());
                XMPP.send(new Message(jid(), task.offer().room(), new Operations.Cancel()), context());
                return goTo(State.READY).using(new Task(true));
              }
              if (msg.has(Operations.Done.class) || msg.body().startsWith("{")){ // hack for answer
                XMPP.send(new Message(jid(), task.offer().room(), new Operations.Done()), context());
                return goTo(State.READY).using(new Task(false));
              }
              return stay();
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
        LaborExchange.reference(context()).tell(self(), self());
      }
    });
    initialize();
  }

  private void stopTimer() {
    if (timer != null)
      timer.cancel();
  }

  public JID jid() {
    return JID.parse(self().path().name());
  }

  public class Task {
    private List<Offer> offers = new ArrayList<>();
    private List<ActorRef> brokers = new ArrayList<>();
    private final boolean immediate;
    private boolean chosen = false;

    public Task(boolean immediate) {
      this.immediate = immediate;
      if (!immediate) {
        timer = AkkaTools.scheduleTimeout(context(), CHOICE_TIMEOUT, self());
      }
    }

    public Task appendVariant(Offer offer, ActorRef broker) {
      offers.add(offer);
      brokers.add(broker);
      return this;
    }

    public Offer choose() {
      int winner = 0;
      for (int i = 0; i < offers.size(); i++) {
        final Offer offer = offers.get(i);
        if (offer.hasWorker(jid())) {
          winner = i;
          break;
        }
      }

      for (int i = 0; i < brokers.size(); i++) {
        if (i != winner) {
          final ActorRef looser = brokers.get(i);
          looser.tell(new Operations.Cancel(), self());
        }
      }
      XMPP.send(new Message(XMPP.jid(), jid(), offers.get(winner)), context());
      timer = AkkaTools.scheduleTimeout(context(), CHECK_TIMEOUT, self());
      offers = Collections.singletonList(offers.get(winner));
      brokers = Collections.singletonList(brokers.get(winner));
      chosen = true;
      return offers.get(0);
    }

    public boolean chosen() {
      return chosen;
    }

    public ActorRef broker() {
      if (brokers.size() != 1)
        throw new IllegalStateException("Multiple or zero tasks on state when broker is needed");
      return brokers.get(0);
    }

    public Offer offer() {
      if (offers.size() != 1)
        throw new IllegalStateException("Multiple or zero tasks on state when offer is needed");
      return offers.get(0);
    }

    public boolean immediate() {
      return immediate;
    }

    public boolean isEmpty() {
      return brokers.isEmpty();
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

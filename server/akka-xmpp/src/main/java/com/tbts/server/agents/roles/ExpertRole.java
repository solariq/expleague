package com.tbts.server.agents.roles;

import akka.actor.*;
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

import static com.tbts.model.Operations.*;

/**
 * User: solar
 * Date: 17.12.15
 * Time: 14:16
 */
public class ExpertRole extends AbstractLoggingFSM<ExpertRole.State, ExpertRole.Task> {
  private static final Logger log = Logger.getLogger(ExpertRole.class.getName());
  public static final FiniteDuration CHOICE_TIMEOUT = Duration.create(60, TimeUnit.SECONDS);
  public static final FiniteDuration CHECK_TIMEOUT = Duration.create(10, TimeUnit.SECONDS);
  public static final FiniteDuration INVITE_TIMEOUT = Duration.create(5, TimeUnit.MINUTES);
  private Cancellable timer;

  @Override
  public int logDepth() {
    return 5;
  }

  {
    startWith(State.OFFLINE, new Task(true));

    when(State.OFFLINE,
        matchEvent(
            Presence.class,
            (presence, task) -> {
                if (presence.available()) {
                    stopTimer();
                    if (!task.isEmpty()) {
                        XMPP.send(new Message(XMPP.jid(), jid(), new Resume(task.offer())), context());
                        timer = AkkaTools.scheduleTimeout(context(), INVITE_TIMEOUT, self());
                        return goTo(State.INVITE);
                    }
                    return goTo(State.READY).using(new Task(true));
                }
                return stay();
            }
        ).event(Resume.class,
            (resume, task) -> stay().using(task.appendVariant(resume.offer(), sender()))
        ).event(Timeout.class,
            (to, task) -> stay().using(new Task(true))
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
        ).event(Resume.class,
            (resume, zero) -> zero == null,
            (resume, zero) -> {
              stopTimer();
              return goTo(State.BUSY).using(new Task(true).appendVariant(resume.offer(), sender()));
            }
        ).event(Timeout.class,
            (to, zero) -> {
              return stay().using(new Task(true));
            }
        )
    );
    when(State.CHECK,
        matchEvent( // expert accepted check
            Message.class,
            (msg, task) -> msg.get(Ok.class) != null,
            (msg, task) -> {
              stopTimer();
              task.broker().tell(new Ok(), self());
              return stay().using(task);
            }
        ).event(
            Message.class, // expert canceled check
            (msg, task) -> msg.get(Cancel.class) != null,
            (msg, task) -> {
              stopTimer();
              task.broker().tell(new Cancel(), self());
              return goTo(State.READY).using(new Task(true));
            }
        ).event(Timeout.class, // expert check timed out
            (msg, task) -> {
              timer = null;
              if (task.chosen()) { // CHECK_TIMEOUT
                XMPP.send(new Message(XMPP.jid(), jid(), task.offer(), new Cancel()), context());
                task.broker().tell(new Cancel(), self());
                return goTo(State.READY).using(new Task(false));
              }
              task.choose();
              return stay();
            }
        ).event( // broker sent invitation
            Invite.class,
            (invite, task) -> task.broker().equals(sender()),
            (invite, task) -> {
              stopTimer();
              final Message message = new Message(task.offer().room(), jid(), task.offer());
              invite.form(message, task.offer());
              XMPP.send(message, context());
              timer = AkkaTools.scheduleTimeout(context(), INVITE_TIMEOUT, self());
              return goTo(State.INVITE);
            }
        ).event( // broker canceled offer
            Cancel.class,
            (invite, task) -> task.broker().equals(sender()),
            (invite, task) -> {
              stopTimer();
              return goTo(State.READY).using(new Task(true));
            }
        ).event(
            Presence.class,
            (presence, task) -> !presence.available(),
            (presence, task) -> {
              stopTimer();
              if (!task.isEmpty()) {
                task.choose();
                task.broker().tell(new Cancel(), self());
              }
              return goTo(State.OFFLINE).using(new Task(true));
            }
        ).event(
            Presence.class,
            (presence, task) -> presence.available(),
            (presence, task) -> stay()
        ).event(
            Offer.class,
            (offer, task) -> {
              if (!task.chosen()) {
                task.appendVariant(offer, sender());
                if (offer.hasWorker(jid())){
                  stopTimer();
                  task.choose();
                }
              }
              else sender().tell(new Cancel(), self());
              return stay();
            }
        )
    );
    when(State.INVITE,
        matchEvent(
            Presence.class,
            (presence, task) -> presence.available() && task.offer().room().bareEq(presence.to()),
            (stanza, task) -> {
              stopTimer();
              task.broker().tell(new Start(), self());
              return goTo(State.BUSY);
            }
        ).event(
            Message.class,
            (message, task) -> {
              if (message.has(Cancel.class)) {
                stopTimer();
                task.broker().tell(new Cancel(), self());
                return goTo(State.READY).using(new Task(true));
              }
              if (message.has(Start.class)) {
                stopTimer();
                task.broker().tell(new Start(), self());
                return goTo(State.BUSY);
              }
              return stay();
            }
        ).event(
            Timeout.class,
            (timeout, task) -> {
              timer = null;
              log.fine("Expert timed out the invitation: " + timeout.duration());
              XMPP.send(new Message(XMPP.jid(), jid(), task.offer(), new Cancel()), context());
              task.broker().tell(new Cancel(), self());
              return goTo(State.READY).using(new Task(false));
            }
        ).event(
            Presence.class,
            (presence, task) -> {
              if (!presence.available()) {
                stopTimer();
                XMPP.send(new Message(XMPP.jid(), jid(), task.offer(), new Cancel()), context());
                task.broker().tell(new Cancel(), self());
                return goTo(State.READY).using(new Task(true));
              }
              return stay();
            }
        )
    );
    when(State.BUSY,
        matchEvent(
            Presence.class,
            (presence, task) -> {
              if (!presence.available()) {
                task.broker().tell(new Suspend(), self());
                return goTo(State.OFFLINE);
              }
              return stay();
            }
        ).event(Message.class,
            (msg, task) -> {
              if (msg.get(Cancel.class) != null) {
                task.broker().tell(new Cancel(), self());
                return goTo(State.READY).using(new Task(true));
              }
              if (msg.has(Done.class) || msg.body().startsWith("{")){ // hack for answer
                task.broker().tell(new Done(), self());
                return goTo(State.READY).using(new Task(false));
              }
              return stay();
            }
        )
    );

    whenUnhandled(
        matchEvent(Offer.class,
            (offer, task) -> stay().replying(new Cancel())
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

    onTermination(
        matchStop(Normal(),
            (state, data) -> log.fine("ExpertRole stopped")
        ).stop(Shutdown(),
            (state, data) -> log.warning("ExpertRole shut down on " + data + " events: " + getLog().mkString("\n\t"))
        ).stop(Failure.class,
            (reason, data, state) -> log.warning("ExpertRole terminated on " + data + " in state " + state + " events: " + getLog().mkString("\n\t"))
        )
    );
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
          looser.tell(new Cancel(), self());
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

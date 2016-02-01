package com.tbts.server.agents.roles;

import akka.actor.AbstractLoggingFSM;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.FSM;
import akka.util.Timeout;
import com.tbts.model.ExpertManager;
import com.tbts.model.Offer;
import com.tbts.server.agents.LaborExchange;
import com.tbts.server.agents.XMPP;
import com.tbts.util.akka.AkkaTools;
import com.tbts.xmpp.JID;
import com.tbts.xmpp.stanza.Message;
import com.tbts.xmpp.stanza.Presence;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
  public static final FiniteDuration CHOICE_TIMEOUT = Duration.create(10, TimeUnit.SECONDS);
  public static final FiniteDuration CHECK_TIMEOUT = Duration.create(10, TimeUnit.SECONDS);
  public static final FiniteDuration INVITE_TIMEOUT = Duration.create(5, TimeUnit.MINUTES);
  private Cancellable timer;

  @Override
  public int logDepth() {
    return 5;
  }

  private String explanation = "";
  {
    startWith(State.OFFLINE, new Task(true));

    when(State.OFFLINE,
        matchEvent(
            Presence.class,
            (presence, task) -> {
              if (presence.available()) {
                explain("Online presence received");
                stopTimer();
                if (!task.isEmpty()) {
                  explain(", resuming task " + task.offer().room());
                  XMPP.send(new Message(XMPP.jid(), jid(), new Resume(task.offer(), INVITE_TIMEOUT)), context());
                  timer = AkkaTools.scheduleTimeout(context(), INVITE_TIMEOUT, self());
                  return goTo(State.INVITE);
                }
                explain(", no active task, going to labor exchange");
                LaborExchange.reference(context()).tell(self(), self());
                return goTo(State.READY).using(new Task(true));
              }
              return stay();
            }
        ).event(Resume.class,
            (resume, task) -> {
              explain("Resume command received from " + sender());
              return stay().using(task.appendVariant(resume.offer(), sender()));
            }
        ).event(Timeout.class,
            (to, task) -> {
              explain("Timeout"+ (to.duration()) + " while being offline");
              return stay().using(new Task(true));
            }
        ));
    when(State.READY,
        matchEvent(
            Offer.class,
            (offer, task) -> {
              explain("Offer accepted");
              task.appendVariant(offer, sender());
              if (task.immediate() || offer.hasWorker(jid())) {
                explain(", the task type is 'immediate', choose process started");
                task.choose();
              }
              return goTo(State.CHECK);
            }
        ).event(
            Presence.class,
            (presence, task) -> presence.available() ? stay() : goTo(State.OFFLINE)
        ).event(Resume.class,
            (resume, zero) -> zero == null,
            (resume, zero) -> {
              explain("Resume command received from " + sender() + " sending resume command to the expert");
              stopTimer();
              XMPP.send(new Message(XMPP.jid(), jid(), new Resume(resume.offer(), INVITE_TIMEOUT)), context());
              return goTo(State.BUSY).using(new Task(true).appendVariant(resume.offer(), sender()));
            }
        ).event(Timeout.class,
            (to, zero) -> {
              explain("Timeout " + to.duration() + " waiting next task from the same room. Going to labor exchange.");
              LaborExchange.reference(context()).tell(self(), self());
              return stay().using(new Task(true));
            }
        )
    );
    when(State.CHECK,
        matchEvent( // expert accepted check
            Message.class,
            (msg, task) -> msg.get(Ok.class) != null,
            (msg, task) -> {
              explain("Received Ok message from expert, forwarding it to broker");
              stopTimer();
              task.broker().tell(new Ok(), self());
              return goTo(State.INVITE).using(task);
            }
        ).event(
            Message.class, // expert canceled check
            (msg, task) -> msg.get(Cancel.class) != null,
            (msg, task) -> {
              explain("Received Cancel message from expert, forwarding it to broker, going to labor exchange");
              stopTimer();
              task.broker().tell(new Cancel(), self());
              return laborExchange();
            }
        ).event(Timeout.class, // expert check timed out
            (msg, task) -> {
              timer = null;
              explain("Received timeout");
              if (task.chosen()) { // CHECK_TIMEOUT
                explain(" during expert check. Sending cancel to broker and going to labor exchange.");
                task.broker().tell(new Cancel(), self());
                return laborExchange();
              }
              explain(" on choose process.");
              task.choose();
              return stay();
            }
        ).event( // broker canceled offer
            Cancel.class,
            (invite, task) -> task.broker().equals(sender()),
            (invite, task) -> {
              explain("Broker canceled check. Going to labor exchange.");
              stopTimer();
              return laborExchange();
            }
        ).event(
            Presence.class,
            (presence, task) -> !presence.available(),
            (presence, task) -> {
              explain("Expert has gone offline. Cancelling the check.");
              stopTimer();
              task.brokers.stream().forEach(b -> b.tell(new Cancel(), self()));
              return goTo(State.OFFLINE).using(new Task(true));
            }
        ).event(
            Presence.class,
            (presence, task) -> presence.available(),
            (presence, task) -> stay()
        ).event(
            Offer.class,
            (offer, task) -> {
              explain("Received one more offer");
              if (!task.chosen()) {
                task.appendVariant(offer, sender());
                if (offer.hasWorker(jid())){
                  explain(" the new offer has priority because this expert is one of the active workers of the task.");
                  stopTimer();
                  task.choose();
                }
              }
              else {
                explain(" while waiting the response from the expert. Cancelling it");
                sender().tell(new Cancel(), self());
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
              stopTimer();
              explain("Expert has shown in the room. Assuming he has accepted the invitation.");
              task.broker().tell(new Start(), self());
              return goTo(State.BUSY);
            }
        ).event( // broker sent invitation
            Invite.class,
            (invite, task) -> task.broker().equals(sender()),
            (invite, task) -> {
              explain("Invitation from broker received, forwarding it to expert.");
              stopTimer();
              final Message message = new Message(task.offer().room(), jid(), task.offer());
              invite.timeout = INVITE_TIMEOUT.toMillis();
              invite.form(message, task.offer());
              XMPP.send(message, context());
              timer = AkkaTools.scheduleTimeout(context(), INVITE_TIMEOUT, self());
              return stay();
            }
        ).event( // broker sent cancel
            Cancel.class,
            (cancel, task) -> task.broker().equals(sender()),
            (cancel, task) -> {
              stopTimer();
              explain("Cancel command received from broker, forwarding to expert and going to labor exchange.");
              final Message message = new Message(task.offer().room(), jid(), cancel, task.offer());
              XMPP.send(message, context());
              return laborExchange();
            }
        ).event(
            Message.class,
            (message, task) -> {
              if (message.has(Cancel.class)) {
                explain("Cancel received from expert. Forwarding it to broker and going to labor exchange.");
                stopTimer();
                task.broker().tell(new Cancel(), self());
                return laborExchange();
              }
              if (message.has(Start.class)) {
                explain("Expert started working on " + task.offer().room().local());
                stopTimer();
                task.broker().tell(new Start(), self());
                return goTo(State.BUSY);
              }
              explain("Ignoring message: " + message);
              return stay();
            }
        ).event(
            Timeout.class,
            (timeout, task) -> {
              explain("Timeout (" + timeout.duration() + ") received. Sending cancel to both expert and broker. Going to labor exchange.");
              timer = null;
              XMPP.send(new Message(XMPP.jid(), jid(), task.offer(), new Cancel()), context());
              task.broker().tell(new Cancel(), self());
              return laborExchange();
            }
        ).event(
            Presence.class,
            (presence, task) -> {
              if (!presence.available()) {
                explain("Expert has gone offline during invitation. Sending suspend to broker.");
                stopTimer();
                task.broker().tell(new Cancel(), self());
                return goTo(State.OFFLINE).using(new Task(true));
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
                explain("Expert has gone offline during task execution. Sending suspend to broker.");
                task.broker().tell(new Suspend(), self());
                return goTo(State.OFFLINE);
              }
              return stay();
            }
        ).event(Message.class,
            (msg, task) -> {
              if (msg.get(Cancel.class) != null) {
                explain("Expert has canceled task during execution. Notifying broker and going to labor exchange.");
                task.broker().tell(new Cancel(), self());
                return laborExchange();
              }
              if (msg.has(Done.class) || (msg.body().startsWith("{") && msg.type() == Message.MessageType.GROUP_CHAT)){ // hack for answer
                explain("Expert has finished task execution. Notifying broker and going to labor exchange with slight suspension to let user send a message to restart the task.");
                task.broker().tell(new Done(), self());
                return goTo(State.READY).using(new Task(false));
              }
              explain("Ignoring message inside busy state: " + msg);
              return stay();
            }
        ).event(Cancel.class,
            (cancel, task) -> {
              explain("Broker canceled task. Sending expert cancel.");
              XMPP.send(new Message(XMPP.jid(), jid(), new Cancel(), task.offer()), context());
              return laborExchange();
            }
        )
    );

    whenUnhandled(
        matchEvent(Offer.class,
            (offer, task) -> {
              explain("Received offer during improper state. Ignoring.");
              return stay();
            }
        )
    );

//    whenUnhandled(matchAnyEvent((state, data) -> stay().replying(new Operations.Cancel())));

    onTransition((from, to) -> {
      final Offer first = nextStateData() != null && nextStateData().chosen() ? nextStateData().offer() : null;
      ExpertManager.instance().record(jid()).entry(first, to);
      if (from != to)
        context().parent().tell(to, self());
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

  @Override
  public void preStart() throws Exception {
    super.preStart();
    log.fine("Expert " + jid() + " started");
  }

  @Override
  public void postStop() {
    super.postStop();
    log.fine("Expert " + jid() + "exited");
  }

  @Override
  public void processEvent(FSM.Event<Task> event, Object source) {
    final State from = stateName();
    super.processEvent(event, source);
    final State to = stateName();
    final Offer first = stateData() != null && stateData().chosen() ? stateData().offer() : null;
    log.fine("Expert " + jid() + " state change " + from + " -> " + to +
        (first != null ? " " + first.room().local() : "")
        + " " + explanation);
    explanation = "";
  }

  private FSM.State<State, Task> laborExchange() {
    LaborExchange.reference(context()).tell(self(), self());
    return goTo(State.READY).using(new Task(true));
  }

  private void explain(String s) {
    explanation += s;
  }

  private void stopTimer() {
    if (timer != null)
      timer.cancel();
    timer = null;
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
      explain(" Choosing the offer between " + offers.size() + " variants.");
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

      explain(" Sending offer " + offers.get(winner).room().local() + " to expert.");
      XMPP.send(new Message(XMPP.jid(), jid(), offers.get(winner)), context());
      stopTimer();
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

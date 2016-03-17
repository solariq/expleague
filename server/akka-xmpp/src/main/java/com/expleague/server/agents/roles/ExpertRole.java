package com.expleague.server.agents.roles;

import akka.actor.AbstractLoggingFSM;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.FSM;
import akka.util.Timeout;
import com.expleague.model.Answer;
import com.expleague.model.Offer;
import com.expleague.server.agents.LaborExchange;
import com.expleague.server.agents.XMPP;
import com.expleague.util.akka.AkkaTools;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Presence;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static com.expleague.model.Operations.*;

/**
 * User: solar
 * Date: 17.12.15
 * Time: 14:16
 */
public class ExpertRole extends AbstractLoggingFSM<ExpertRole.State, ExpertRole.Variants> {
  private static final Logger log = Logger.getLogger(ExpertRole.class.getName());
  public static final FiniteDuration CHOICE_TIMEOUT = Duration.create(5, TimeUnit.SECONDS);
  public static final FiniteDuration CONTINUATION_TIMEOUT = Duration.create(30, TimeUnit.SECONDS);
  public static final FiniteDuration INVITE_TIMEOUT = Duration.create(5, TimeUnit.MINUTES);
  private Cancellable timer;

  @Override
  public int logDepth() {
    return 5;
  }

  private String explanation = "";
  {
    startWith(State.OFFLINE, new Variants());

    when(State.OFFLINE,
        matchEvent(Presence.class,
            (presence, task) -> {
              if (presence.available()) {
                explain("Online presence received. Going to labor exchange.");
                return laborExchange(task);
              }
              return stay();
            }
        )
    );

    // todo: Ok can be received here and will be unhandled
    when(State.READY,
        matchEvent(Presence.class,
            (presence, task) -> presence.available() ? stay() : goTo(State.OFFLINE)
        ).event(Offer.class,
            (offer, task) -> {
              explain("Offer received appending as variant");
              task.appendVariant(offer, sender());
              return stay();
            }
        ).event(Resume.class,
            (resume, task) -> {
              explain("Resume command received from " + sender() + " sending resume command to the expert");
              XMPP.send(new Message(XMPP.jid(), jid(), new Resume(), resume.offer()), context());
              return goTo(State.INVITE).using(task.enforce(resume.offer(), sender()));
            }
        ).event(Timeout.class,
            (to, task) -> {
              if (!task.choose()) {
                timer = AkkaTools.scheduleTimeout(context(), CHOICE_TIMEOUT, self());
                return stay();
              }
              explain("Sending offer " + task.offer().room().local() + " to expert.");
              XMPP.send(new Message(XMPP.jid(), jid(), task.offer()), context());
              return goTo(State.CHECK);
            }
        ).event(Cancel.class,
          (cancel, task) -> {
            task.removeVariant(sender());
            return stay();
          }
        )
    );
    when(State.CHECK,
        matchEvent(Presence.class,
            (presence, task) -> {
              if (presence.available())
                return stay();
              explain("Expert has gone offline. Cancelling the check.");
              task.broker().tell(new Cancel(), self());
              return goTo(State.OFFLINE).using(task.clean());
            }
        ).event(Message.class,  // expert accepted check
            (msg, task) -> msg.get(Ok.class) != null,
            (msg, task) -> {
              explain("Received Ok message from expert, forwarding it to broker");
              task.broker().tell(new Ok(), self());
              return goTo(State.INVITE).using(task);
            }
        ).event(Message.class, // expert canceled check
            (msg, task) -> msg.get(Cancel.class) != null,
            (msg, task) -> {
              explain("Received Cancel message from expert, forwarding it to broker, going to labor exchange");
              task.broker().tell(new Cancel(), self());
              return laborExchange(task).using(task.clean());
            }
        ).event(Cancel.class, // broker canceled offer
            (cancel, task) -> task.broker().equals(sender()),
            (cancel, task) -> {
              explain("Broker canceled check. Canceling check and going to labor exchange.");
              XMPP.send(new Message(XMPP.jid(), jid(), cancel), context());
              return laborExchange(task);
            }
        )
    );
    when(State.INVITE,
        matchEvent(Presence.class,
            (presence, task) -> {
              if (!presence.available()) {
                explain("Expert has gone offline during invitation. Sending ignore to broker.");
                task.broker().tell(new Ignore(), self());
                return goTo(State.OFFLINE).using(task.clean());
              }
              return stay();
            }
        ).event(Invite.class,  // broker sent invitation
            (invite, task) -> task.broker().equals(sender()),
            (invite, task) -> {
              explain("Invitation from broker received, forwarding it to expert.");
              invite.timeout = INVITE_TIMEOUT.toSeconds();
              final Message message = new Message(task.offer().room(), jid(), task.offer(), invite);
              XMPP.send(message, context());
              timer = AkkaTools.scheduleTimeout(context(), INVITE_TIMEOUT, self());
              return stay();
            }
        ).event(Cancel.class,  // broker sent cancel
            (cancel, task) -> task.broker().equals(sender()),
            (cancel, task) -> {
              explain("Cancel command received from broker, forwarding to expert and going to labor exchange.");
              final Message message = new Message(task.offer().room(), jid(), cancel, task.offer());
              XMPP.send(message, context());
              return laborExchange(task);
            }
        ).event(Message.class,
            (message, task) -> {
              if (message.has(Cancel.class)) {
                explain("Cancel received from expert. Forwarding it to broker and going to labor exchange.");
                stopTimer();
                task.broker().tell(new Cancel(), self());
                return laborExchange(task);
              }
              if (message.has(Start.class)) {
                explain("Expert started working on " + task.offer().room().local());
                stopTimer();
                task.broker().tell(new Start(), self());
                return goTo(State.BUSY);
              }
              if (message.has(Resume.class)) {
                explain("Expert resumed working on " + task.offer().room().local());
                stopTimer();
                task.broker().tell(new Resume(), self());
                return goTo(State.BUSY);
              }
              explain("Ignoring message: " + message);
              return stay();
            }
        ).event(Timeout.class,
            (timeout, task) -> {
              explain("Timeout (" + timeout.duration() + ") received. Sending ignore to broker and cancel to expert. Going to labor exchange.");
              XMPP.send(new Message(XMPP.jid(), jid(), task.offer(), new Cancel()), context());
              task.broker().tell(new Ignore(), self());
              return laborExchange(task);
            }
        )
    );
    when(State.BUSY,
        matchEvent(Presence.class,
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
                return laborExchange(task);
              }
              if (msg.has(Done.class) || msg.has(Answer.class)) { // hack for answer
                explain("Expert has finished task execution. Notifying broker and going to labor exchange with slight suspension to let user send a message to restart the task.");
                timer = AkkaTools.scheduleTimeout(context(), CONTINUATION_TIMEOUT, self());
                task.broker().tell(new Done(), self());
                return stay();
              }
              explain("Ignoring message inside busy state: " + msg);
              return stay();
            }
        ).event(Cancel.class,
            (cancel, task) -> task.broker().equals(sender()),
            (cancel, task) -> {
              explain("Broker canceled task. Sending expert cancel.");
              XMPP.send(new Message(XMPP.jid(), jid(), new Cancel(), task.offer()), context());
              return laborExchange(task);
            }
        ).event(Timeout.class,
            (timeout, task) -> {
              explain("Tired to wait for continuation of the task. Going to labor exchange.");
              return laborExchange(task);
            }
        ).event(Offer.class, // continue the task
            (offer, task) -> offer.room().equals(task.offer().room()),
            (offer, task) -> {
              return goTo(State.INVITE).using(task.enforce(offer, sender())).replying(new Ok());
            }
        )
    );

    whenUnhandled(
        matchEvent(Offer.class,
            (offer, task) -> {
              explain("Received offer during improper state. Ignoring.");
              return stay().replying(new Ignore());
            }
        )
    );

    onTransition((from, to) -> {
      if (from != to) {
        context().parent().tell(to, self());
        if (timer != null) {
          timer.cancel();
          timer = null;
        }
        if (to == State.READY)
          timer = AkkaTools.scheduleTimeout(context(), CHOICE_TIMEOUT, self());
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
  public void processEvent(FSM.Event<Variants> event, Object source) {
    final State from = stateName();
    super.processEvent(event, source);
    if (explanation.isEmpty())
      return;

    final State to = stateName();
    final Offer first = stateData() != null && stateData().offers.size() == 1 ? stateData().offer() : null;
    log.fine("Expert " + jid() + " state change " + from + " -> " + to +
        (first != null ? " " + first.room().local() : "")
        + " " + explanation);
    explanation = "";
  }

  private FSM.State<State, Variants> laborExchange(Variants task) {
    LaborExchange.tell(context(), self(), self());
    return goTo(State.READY).using(task.clean());
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
    return LaborExchange.Experts.jid(self());
  }

  public class Variants {
    private List<Offer> offers = new ArrayList<>();
    private List<ActorRef> brokers = new ArrayList<>();

    public Variants appendVariant(Offer offer, ActorRef broker) {
      if (offers.indexOf(offer) != -1 || brokers.indexOf(broker) != -1) {
        return this;
      }
      offers.add(offer);
      brokers.add(broker);
      return this;
    }

    public Variants removeVariant(ActorRef broker) {
      final int indexOf = brokers.indexOf(broker);
      if (indexOf != -1) {
        offers.remove(indexOf);
        brokers.remove(indexOf);
      }
      return this;
    }

    public boolean choose() {
      if (offers.isEmpty())
        return false;
      int winner = 0;
      explain("Choosing the offer between " + offers.size() + " variants.");
      for (int i = 0; i < offers.size(); i++) {
        final Offer offer = offers.get(i);
        if (offer.filter().isPrefered(jid())) {
          winner = i;
          break;
        }
      }

      for (int i = 0; i < brokers.size(); i++) {
        if (i != winner) {
          final ActorRef looser = brokers.get(i);
          looser.tell(new Ignore(), self());
        }
      }

      offers = Collections.singletonList(offers.get(winner));
      brokers = Collections.singletonList(brokers.get(winner));
      return true;
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

    public Variants enforce(Offer offer, ActorRef sender) {
      offers = Collections.singletonList(offer);
      brokers = Collections.singletonList(sender);
      return this;
    }

    public Variants clean() {
      offers = new ArrayList<>();
      brokers = new ArrayList<>();
      return this;
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

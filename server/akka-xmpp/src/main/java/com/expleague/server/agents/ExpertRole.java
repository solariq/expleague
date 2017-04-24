package com.expleague.server.agents;

import akka.actor.AbstractLoggingFSM;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.FSM;
import akka.util.Timeout;
import com.expleague.model.Answer;
import com.expleague.model.Offer;
import com.expleague.server.ExpLeagueServer;
import com.expleague.util.akka.AkkaTools;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Presence;
import com.google.common.collect.Lists;
import scala.concurrent.duration.FiniteDuration;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import static com.expleague.model.Operations.*;

/**
 * User: solar
 * Date: 17.12.15
 * Time: 14:16
 */
public class ExpertRole extends AbstractLoggingFSM<ExpertRole.State, ExpertRole.Variants> {
  private static final Logger log = Logger.getLogger(ExpertRole.class.getName());
  public static final FiniteDuration CHOICE_TIMEOUT = ExpLeagueServer.config().timeout("expert-role.choice-timeout");
  public static final FiniteDuration INVITE_TIMEOUT = ExpLeagueServer.config().timeout("expert-role.invite-timeout");
  private Cancellable timer;

  @Override
  public int logDepth() {
    return 5;
  }


  public FSM.State<ExpertRole.State, ExpertRole.Variants> goTo1(ExpertRole.State state) {
    return goTo(state);
  }

  private String explanation = "";

  {
    startWith(State.OFFLINE, new Variants());
    if (XMPP.online(context()).contains(jid().bare()))
      laborExchange(new Variants());

    when(State.OFFLINE,
        matchEvent(Presence.class,
            (presence, task) -> {
              if (presence.available()) {
                explain("Online presence received. Going to labor exchange.");
                return laborExchange(task);
              }
              return stay();
            }
        ).event(Resume.class,
            (resume, task) -> {
              explain("Resume skipped, the expert is offline");
              return stay();
            }
        )
    );

    // todo: Ok can be received here and will be unhandled
    when(State.READY,
        matchEvent(Presence.class,
            (presence, task) -> presence.available() || presence.to() != null ? stay() : goTo1(State.OFFLINE)
        ).event(ActorRef.class, // broker
            (offer, task) -> stay().replying(self())
        ).event(Offer.class,
            (offer, task) -> {
              explain("Offer received appending as variant");
              task.appendVariant(offer, sender(), new Check());
              return stay();
            }
        ).event(Resume.class,
            (resume, task) -> {
              explain("Resume received, appending as variant " + resume.offer().room().local());
              task.appendVariant(resume.offer(), sender(), resume);
              return stay();
            }
        ).event(Timeout.class,
            (to, task) -> {
              final TaskOption taskOption = task.choose();
              if (taskOption == null) {
                timer = AkkaTools.scheduleTimeout(context(), CHOICE_TIMEOUT, self());
                return stay();
              }
              final Command sourceCommand = taskOption.getSourceCommand();
              if (sourceCommand instanceof Resume) {
                explain("Resume command received from " + sender() + " sending resume command to the expert");
                XMPP.send(new Message(XMPP.jid(), jid(), sourceCommand, taskOption.getOffer()), context());
                return goTo1(State.INVITE);
              }
              else {
                explain("Sending offer " + task.offer().room().local() + " to expert.");
                XMPP.send(new Message(XMPP.jid(), jid(), task.offer(), new Check()), context());
                return goTo1(State.CHECK);
              }
            }
        ).event(Cancel.class,
          (cancel, task) -> {
            task.removeVariant(sender());
            return stay();
          }
        ).event(Message.class, (message, task) -> stay())
    );
    when(State.CHECK,
        matchEvent(Presence.class,
            (presence, task) -> {
              if (presence.available() || presence.to() != null)
                return stay();
              explain("Expert has gone offline. Cancelling the check.");
              task.broker().tell(new Ignore(), self());
              return goTo1(State.OFFLINE).using(task.clean());
            }
        ).event(Message.class,  // expert accepted check
            (msg, task) -> msg.get(Ok.class) != null,
            (msg, task) -> {
              explain("Received Ok message from expert, forwarding it to broker");
              task.broker().tell(new Ok(), self());
              return goTo1(State.INVITE).using(task);
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
            (presence, task) -> presence.from().bareEq(jid()),
            (presence, task) -> {
              if (!presence.available() && presence.to() == null) {
                explain("Expert has gone offline during invitation. Sending ignore to broker.");
                task.broker().tell(new Ignore(), self());
                return goTo1(State.OFFLINE).using(task.clean());
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
                return goTo1(State.BUSY);
              }
              if (message.has(Resume.class)) {
                explain("Expert resumed working on " + task.offer().room().local());
                stopTimer();
                task.broker().tell(new Resume(), self());
                return goTo1(State.BUSY);
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
              if (!presence.available() && presence.to() == null) {
                explain("Expert has gone offline during task execution. Sending suspend to broker.");
                task.broker().tell(new Suspend(), self());
                return goTo1(State.OFFLINE);
              }
              return stay();
            }
        ).event(Message.class,
            (msg, task) -> {
              if (msg.has(Cancel.class)) {
                explain("Expert has canceled task during execution. Notifying broker and going to labor exchange.");
                task.broker().tell(new Cancel(), self());
                return laborExchange(task);
              }
              else if (msg.has(Suspend.class)) {
                explain("Expert has suspended task execution. Sending suspend to broker.");
                task.broker().tell(msg.get(Suspend.class), self());
                task.removeVariant(task.broker());
                return goTo1(State.READY);
              }
              else if (msg.has(Progress.class)) {
                if (msg.to() == null || msg.to().local().isEmpty())
                  task.broker().tell(msg.get(Progress.class), self());
                return stay();
              }

              else if (msg.has(Done.class) || msg.has(Answer.class)) { // hack for answer
                explain("Expert has finished task execution. Notifying broker and going to labor exchange.");
                task.broker().tell(new Done(), self());
                return laborExchange(task);
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
            (offer, task) -> goTo1(State.INVITE).using(task.enforce(offer, sender())).replying(new Ok())
        )
    );

    whenUnhandled(
        matchEvent(Offer.class,
            (offer, task) -> {
              explain("Received offer during improper state. Ignoring.");
              return stay().replying(new Ignore());
            }
        ).
        event(ActorRef.class, // broker
            (broker, task) -> stay().replying(new Ignore()))
    );

    onTransition((from, to) -> {
      if (from != to) {
        LaborExchange.reference(context()).tell(new StatusChange(from.name(), to.name()), self());
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
    try {
      final State from = stateName();
      super.processEvent(event, source);
      if (explanation.isEmpty())
        return;

      final State to = stateName();
      final Offer first = stateData() != null && stateData().taskOptions.size() == 1 ? stateData().offer() : null;
      log.fine("Expert " + jid() + " state change " + from + " -> " + to +
          (first != null ? " " + first.room().local() : "")
          + " " + explanation);
      explanation = "";
    }
    catch (Throwable th) {
      th.printStackTrace();
    }
  }

  private FSM.State<State, Variants> laborExchange(Variants task) {
    LaborExchange.tell(context(), self(), self());
    return goTo1(State.READY).using(task.clean());
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
    private List<TaskOption> taskOptions = new ArrayList<>();

    public Variants appendVariant(Offer offer, ActorRef broker, Command sourceCommand) {
      final TaskOption taskOption = new TaskOption(broker, offer, sourceCommand);
      if (taskOptions.indexOf(taskOption) != -1) {
        return this;
      }
      taskOptions.add(taskOption);
      return this;
    }

    public Variants removeVariant(ActorRef broker) {
      taskOptions.removeIf(taskOption -> taskOption.getBroker().equals(broker));
      return this;
    }

    public TaskOption choose() {
      if (taskOptions.isEmpty())
        return null;

      boolean winnerPreferred = false;
      int winner = 0;
      explain("Choosing the offer between " + taskOptions.size() + " variants.");
      long winnerETA = Long.MAX_VALUE;
      for (int i = 0; i < taskOptions.size(); i++) {
        final Offer offer = taskOptions.get(i).getOffer();
        final long currentETA = offer.expires().getTime();
        final boolean prefered = offer.filter().isPrefered(jid());
        if ((prefered && !winnerPreferred) || (currentETA < winnerETA && prefered == winnerPreferred)) {
          winner = i;
          winnerPreferred = prefered;
          winnerETA = currentETA;
        }
      }

      for (int i = 0; i < taskOptions.size(); i++) {
        if (i != winner) {
          final ActorRef looser = taskOptions.get(i).getBroker();
          looser.tell(new Ignore(), self());
        }
      }

      final TaskOption result = taskOptions.get(winner);
      taskOptions = Lists.newArrayList(result);
      return result;
    }

    public ActorRef broker() {
      if (taskOptions.size() != 1)
        throw new IllegalStateException("Multiple or zero tasks on state when broker is needed");
      return taskOptions.get(0).getBroker();
    }

    public Offer offer() {
      if (taskOptions.size() != 1)
        throw new IllegalStateException("Multiple or zero tasks on state when offer is needed");
      return taskOptions.get(0).getOffer();
    }

    public Variants enforce(Offer offer, ActorRef sender) {
      taskOptions = Lists.newArrayList(new TaskOption(sender, offer, new Check()));
      return this;
    }

    public Variants clean() {
      taskOptions = new ArrayList<>();
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

package com.expleague.server.agents;

import akka.actor.AbstractFSM;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.FSM;
import akka.util.Timeout;
import com.expleague.model.Operations.*;
import com.expleague.server.ExpLeagueServer;
import com.expleague.util.akka.AkkaTools;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
import com.spbsu.commons.random.FastRandom;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.expleague.server.agents.ExpLeagueOrder.Role.*;
import static com.expleague.server.agents.ExpLeagueOrder.Status.IN_PROGRESS;
import static com.expleague.server.agents.ExpLeagueOrder.Status.SUSPENDED;
import static com.expleague.server.agents.LaborExchange.Experts;
import static com.expleague.server.agents.LaborExchange.experts;

/**
 * User: solar
 * Date: 18.12.15
 * Time: 22:50
 */
public class BrokerRole extends AbstractFSM<BrokerRole.State, ExpLeagueOrder.State> {
  private static final Logger log = Logger.getLogger(BrokerRole.class.getName());

  public static final FiniteDuration RETRY_TIMEOUT = ExpLeagueServer.config().timeout("broker-role.retry-timeout");

  private String explanation = "";
  private Cancellable timeout;

  private final FastRandom rng = new FastRandom();

  public BrokerRole(final ActorRef laborExchange) {
    startWith(State.UNEMPLOYED, null);
    when(State.UNEMPLOYED,
        matchEvent(ExpLeagueOrder.class,
            (order, zero) -> {
              explain("Received new order: " + order + ".");
              sender().tell(new Ok(), self());
              order.broker(self());

              if (order.status() == IN_PROGRESS) {// server was shut down
                order.state().suspend();
              }

              final JID expertOnTask = order.of(ACTIVE).findAny().orElse(null);
              if (order.status() == SUSPENDED && expertOnTask != null) {
                final long activationTimestampMs = order.getActivationTimestampMs();
                final long currentTimeMillis = System.currentTimeMillis();
                final long suspendIntervalMs = activationTimestampMs - currentTimeMillis;
                if (suspendIntervalMs > 0) {
                  AkkaTools.scheduleTimeout(context(), Duration.create(suspendIntervalMs, TimeUnit.MILLISECONDS), self());
                  return goTo(State.SUSPENDED);
                }
                else {
                  //noinspection ConstantConditions
                  explain("Trying to get active worker " + expertOnTask.local() + " back to the order.");
                  Experts.tellTo(expertOnTask, new Resume(order.offer()), self(), context());
                  return goTo(State.STARVING).using(order.state());
                }
              }
              else {
                explain("No active work on order found.");
                return lookForExpert(order.state()).using(order.state());
              }
            }
        )
    );

    when(State.STARVING,
        matchEvent(Ok.class,
            (ok, task) -> {
              explain("Received agreement from expert " + Experts.jid(sender()).local());
              final JID expert = Experts.jid(sender());
              if (task.interview(expert)) {
                explain("Expert passed interview, sending him an invitation.");
                Experts.tellTo(expert, new Invite(), self(), context());
                XMPP.send(new Message(expert, XMPP.jid(), new Invite()), context());
                task.invite(expert);
                return goTo(State.INVITE);
              }
              else {
                explain("Expert failed interview, canceling.");
                Experts.tellTo(expert, new Cancel(), self(), context());
                task.refused(expert);
                return stay();
              }
            }
        ).event(ActorRef.class,
            (expert, task) -> {
              if (task.order().status() == SUSPENDED) {
                if (task.role(Experts.jid(expert)) == ACTIVE) {
                  explain("Worker returned online, sending resume.");
                  expert.tell(new Resume(task.order().offer()), self());
                  return goTo(State.INVITE);
                }
                explain("Task is in progress. Continue waiting for active worker.");
                return stay();
              }
              final JID jid = Experts.jid(expert);
              explain("Labor exchange send us new candidate: " + jid + ".");
              if (task.interview(jid) && task.check(jid)) {
                explain("Have not seen him before, sending offer.");
                expert.tell(task.order().offer(), self());
              }
              else explain("This candidate has already refused our invitation/check/failed interview. Ignoring. Current role: " + task.role(jid));
              return stay();
            }
        ).event(Resume.class,
            (resume, task) -> {
              explain("Expert resumed his work. Sending notification to the room.");
              final JID expert = Experts.jid(sender());
              task.enter(expert);
              XMPP.send(new Message(expert, task.jid(), new Resume()), context());
              return goTo(State.WORK_TRACKING);
            }
        ).event(Cancel.class,
            (cancel, task) -> !task.jid().bareEq(XMPP.jid(sender())),
            (cancel, task) -> {
              final JID expert = Experts.jid(sender());
              explain("Expert " + expert + " refused the check.");
              return stay().using(task.refused(expert));
            }
        ).event(Ignore.class,
            (cancel, task) -> {
              final JID expert = Experts.jid(sender());
              explain("Expert " + expert + " ignored the check.");
              return stay().using(task.ignored(expert));
            }
        ).event(Cancel.class,
            (cancel, task) -> task.jid().bareEq(XMPP.jid(sender())),
            (cancel, task) -> cancelTask(task)
        ).event(Timeout.class,
            (to, task) -> {
              timeout = AkkaTools.scheduleTimeout(context(), RETRY_TIMEOUT.plus(FiniteDuration.apply(rng.nextDouble(), ExpLeagueServer.config().timeUnit("broker-role.retry-timeout-delta"))), self());
              return task.order().status() != SUSPENDED ? lookForExpert(task) : stay();
            }
        )
    );

    when(State.INVITE,
        matchEvent(Start.class,
            (start, task) -> task.role(Experts.jid(sender())) == INVITED,
            (start, task) -> {
              final JID expert = Experts.jid(sender());
              explain("Expert " + expert + " started working on task " + task.order().room().local() + ".");
              task.experts()
                  .filter(jid -> !jid.bareEq(expert))
                  .forEach(jid -> Experts.tellTo(jid, new Cancel(), self(), context()));
              task.enter(expert);
              XMPP.send(new Message(expert, task.jid(), new Start()), context());
              return goTo(State.WORK_TRACKING);
            }
        ).event(Resume.class,
          (start, task) -> task.role(Experts.jid(sender())) == ACTIVE,
          (start, task) -> {
            final JID expert = Experts.jid(sender());
            explain("Expert " + expert + " resumed working on task " + task.order().room().local() + ".");
            task.enter(expert);
            XMPP.send(new Message(expert, task.jid(), new Resume()), context());
            return goTo(State.WORK_TRACKING);
          }
        ).event(// from invitation
            (cancel, task) -> task.role(Experts.jid(sender())) == INVITED && (cancel instanceof Cancel || cancel instanceof Ignore),
            (cancel, task) -> {
              final JID expert = Experts.jid(sender());
              explain("Expert " + expert + " declined invitation");
              if (cancel instanceof Cancel) {
                XMPP.send(new Message(expert, task.jid(), new Cancel()), context());
                task.refused(expert);
              }
              else task.ignored(expert);
              JID candidate;
              //noinspection StatementWithEmptyBody
              while ((candidate = task.nextCandidate()) != null && !task.interview(candidate));
              if (candidate != null) {
                explain("Have found candidate from queue, that fits: " + candidate + ". Sending him invitation.");
                Experts.tellTo(candidate, new Invite(), self(), context());
                XMPP.send(new Message(candidate, XMPP.jid(), new Invite()), context());
                task.invite(candidate);
                return stay();
              }
              else if (!task.order().of(INVITED).findAny().isPresent()) {
                return lookForExpert(task);
              }
              return stay();
            }
        ).event(ActorRef.class,
            (expertRef, task) -> {
              final JID expert = Experts.jid(expertRef);
              explain("Labor exchange send us new candidate: " + expert + ".");
              if (task.role(expert) == ACTIVE) {
                explain("Working expert emerged! Sending her resume.");
                expertRef.tell(new Resume(task.order().offer()), self());
              }
              else if (task.interview(expert) && task.check(expert)) {
                explain("Have not seen him before, sending offer.");
                Experts.tellTo(expert, task.offer(), self(), context());
              }
              else explain("This candidate has already refused our invitation/check/failed interview. Ignoring. Role: " + task.role(expert));
              return stay();
            }
        ).event(Cancel.class, // from check
            (cancel, task) -> task.role(Experts.jid(sender())) == CHECKING,
            (cancel, task) -> {
              explain("Expert " + Experts.jid(sender()) + " is not ready");
              return stay().using(task.refused(Experts.jid(sender())));
            }
        ).event(Cancel.class, // from room
            (cancel, task) -> task.jid().bareEq(XMPP.jid(sender())),
            (cancel, task) -> cancelTask(task)
        ).event(Ok.class, // from check
            (ok, task) -> {
              explain("Received agreement from expert " + Experts.jid(sender()).local());
              final JID expert = Experts.jid(sender());
              if (task.interview(expert)) {
                explain("Expert passed interview, adding him to queue.");
                if (task.invite(expert)) {
                  Experts.tellTo(expert, new Invite(), self(), context());
                  XMPP.send(new Message(expert, XMPP.jid(), new Invite()), context());
                }
                else if (task.role(expert) == NONE)
                  Experts.tellTo(expert, new Cancel(), self(), context());
                return stay();
              }
              else {
                explain("Expert failed interview, canceling.");
                Experts.tellTo(expert, new Cancel(), self(), context());
                task.refused(expert);
                return stay();
              }
            }
        )
    );

    when(State.WORK_TRACKING,
        matchEvent(Done.class,
            (done, task) -> task.role(Experts.jid(sender())) == ACTIVE,
            (done, task) -> {
              explain("Expert has finished working on the " + task.order().room().local() + ". Sending notification to the room.");
              task.order().broker(laborExchange);
              task.close();
              return goTo(State.UNEMPLOYED).using(null);
            }
        ).event(Cancel.class, // cancel from expert
            (cancel, task) -> task.role(Experts.jid(sender())) == ACTIVE,
            (cancel, task) -> {
              explain("Expert canceled task. Looking for other worker.");
              final JID expert = Experts.jid(sender());
              task.refused(expert);
              XMPP.send(new Message(expert, task.jid(), cancel), context());
              return lookForExpert(task).using(task.enter(null));
            }
        ).event(Cancel.class, // cancel from the room
            (cancel, task) -> task.jid().bareEq(XMPP.jid(sender())),
            (cancel, task) -> cancelTask(task)
        ).event(Suspend.class,
            (suspend, task) -> {
              XMPP.send(new Message(Experts.jid(sender()), task.jid(), suspend), context());
              final long endTimestampMs = suspend.getEndTimestampMs();
              final long currentTimeMillis = System.currentTimeMillis();
              final long suspendIntervalMs = endTimestampMs - currentTimeMillis;
              if (suspendIntervalMs > 0) {
                explain("Expert delayed his work for " + (suspendIntervalMs / 60 / 1000) + " minutes. Sending notification to the room.");
                task.suspend(endTimestampMs);
                AkkaTools.scheduleTimeout(context(), Duration.create(suspendIntervalMs, TimeUnit.MILLISECONDS), self());
                return goTo(State.SUSPENDED);
              }
              else {
                explain("Expert suspended his work. Sending notification to the room.");
                task.suspend();
                return goTo(State.STARVING);
              }
            }
        )
    );

    when(State.SUSPENDED,
      matchEvent(Timeout.class,
        (to, task) -> {
          explain("Suspend delay expired. Looking for expert");
          return lookForExpert(task);
        }
      )
    );

    whenUnhandled(matchEvent(
        ActorRef.class,
        (expertRef, task) -> {
          final JID expert = Experts.jid(expertRef);
          explain("Unemployed expert " + expert + " ignored in improper state");
          return stay();
        }
    ).event(ExpLeagueOrder.class,
        (offer, task) -> {
          explain("Already working on " + task.order().room() + ".");
          return stay().replying(new Cancel());
        }
    ));

    onTransition((from, to) -> {
      if (from != to) {
        if (this.timeout != null)
          this.timeout.cancel();
        if (to == State.STARVING)
          this.timeout = AkkaTools.scheduleTimeout(context(), RETRY_TIMEOUT, self());
        else
          this.timeout = null;
      }
    });
    onTermination(
        matchStop(Normal(),
            (state, data) -> log.fine("BrokerRole stopped " + data.order().room())
        ).stop(Shutdown(),
            (state, data) -> log.warning("BrokerRole shut down on " + data)
        ).stop(Failure.class,
            (reason, data, state) -> log.warning("ExpertRole terminated on " + data + " in state " + state)
        )
    );

    initialize();
  }

  @Override
  public void processEvent(FSM.Event<ExpLeagueOrder.State> event, Object source) {
    try {
      final State from = stateName();
      super.processEvent(event, source);
      final State to = stateName();
      log.fine("Broker " + (stateData() != null ? "on task " + stateData().order().room().local() + " " : "")
          + from + " -> " + to
          + ". " + explanation);
      explanation = "";
    }
    catch (Exception e) {
      log.log(Level.SEVERE, "Exception during event handling", e);
    }
  }

  private FSM.State<State, ExpLeagueOrder.State> cancelTask(ExpLeagueOrder.State orderState) {
    explain("The order was canceled by client. Sending all experts cancel.");
    orderState.experts().forEach(jid -> Experts.tellTo(jid, new Cancel(), self(), context()));
    orderState.cancel();
    return goTo(State.UNEMPLOYED).using(null);
  }

  private void explain(String explanation) {
    if (!explanation.isEmpty() && !explanation.startsWith(" "))
      explanation += " ";
    this.explanation += explanation;
  }

  private FSM.State<State, ExpLeagueOrder.State> lookForExpert(ExpLeagueOrder.State orderState) {
    explain("Going to labor exchange to find expert.");
    orderState.experts().forEach(
      jid -> Experts.tellTo(jid, new Cancel(), self(), context())
    );
    orderState.nextRound();
    experts(context()).tell(orderState.order().offer(), self());
    return stateName() != State.STARVING ? goTo(State.STARVING) : stay();
  }

  @Override
  public void logTermination(Reason reason) {
    super.logTermination(reason);
    log.warning(reason.toString());
  }

  enum State {
    UNEMPLOYED,
    STARVING,
    INVITE,
    WORK_TRACKING,
    SUSPENDED
  }
}

package com.expleague.server.agents.roles;

import akka.actor.*;
import akka.util.Timeout;
import com.expleague.model.Offer;
import com.expleague.model.Operations.*;
import com.expleague.server.agents.TBTSRoomAgent;
import com.expleague.server.agents.XMPP;
import com.expleague.util.akka.AkkaTools;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static com.expleague.server.agents.LaborExchange.*;

/**
 * User: solar
 * Date: 18.12.15
 * Time: 22:50
 */
public class BrokerRole extends AbstractFSM<BrokerRole.State, BrokerRole.Task> {
  public static int SIMULTANEOUSLY_INVITED = 3;
  private static final Logger log = Logger.getLogger(BrokerRole.class.getName());
  public class Task {
    public final Offer offer;
    private TBTSRoomAgent.Status roomStatus;
    private final Set<JID> checking = new HashSet<>();
    private final Set<JID> candidates = new HashSet<>();
    private final Set<JID> refused = new HashSet<>();
    private final Set<JID> invited = new HashSet<>();
    private JID onTask;

    public Task(Offer offer, TBTSRoomAgent.Status roomStatus) {
      this.offer = offer;
      this.roomStatus = roomStatus;
    }

    public Task check(JID expert) {
      if (candidates.size() < SIMULTANEOUSLY_INVITED) {
        checking.add(expert.bare());
        Experts.tellTo(expert, offer, self(), context());
      }
      return this;
    }

    public Task invite(JID expert) {
      checking.remove(expert.bare());
      if (invited.size() < SIMULTANEOUSLY_INVITED) {
        invited.add(expert.bare());
        Experts.tellTo(expert, new Invite(), self(), context());
        XMPP.send(new Message(expert, jid(), new Invite()), context());
      }
      else if (candidates.size() < SIMULTANEOUSLY_INVITED)
        candidates.add(expert.bare());
      else
        Experts.tellTo(expert, new Cancel(), self(), context());
      return this;
    }

    public Task refusedCheck(JID jid) {
      refused.add(jid);
      checking.remove(jid);
      return this;
    }

    public boolean refusedInvitation(JID expert) {
      refused.add(expert);
      invited.remove(expert);
      final JID next = next();
      if (next != null) {
        invite(next);
        return true;
      }
      return false;
    }

    public Task enter(JID expert) {
      onTask = expert;
      Stream.of(checking, candidates, invited).flatMap(Collection::stream)
          .filter(jid -> !jid.bareEq(expert))
          .forEach(jid -> Experts.tellTo(jid, new Cancel(), self(), context()));
      checking.clear();
      candidates.clear();
      invited.clear();
      return this;
    }

    public Task cancel() {
      if (onTask != null)
        Experts.tellTo(onTask, new Cancel(), self(), context());
      Stream.of(checking, candidates, invited).flatMap(Collection::stream)
          .forEach(jid -> Experts.tellTo(jid, new Cancel(), self(), context()));
      checking.clear();
      candidates.clear();
      invited.clear();
      onTask = null;
      return this;
    }

    public boolean invited(JID from) {
      return invited.contains(from.bare());
    }

    private JID next() {
      final Iterator<JID> iterator = candidates.iterator();
      if (!iterator.hasNext())
        return null;
      final JID next = iterator.next();
      iterator.remove();
      return next;
    }

    public boolean onTask(JID expert) {
      return expert.bareEq(onTask);
    }

    public JID jid() {
      return offer.room();
    }

    public void refuse(JID expert) {
      refused.add(expert);
      Experts.tellTo(expert, new Cancel(), self(), context());
    }

    public Task exit() {
      invited.clear();
      onTask = null;
      refused.clear();
      candidates.clear();
      return this;
    }
  }

  public static final FiniteDuration RETRY_TIMEOUT = Duration.apply(2, TimeUnit.MINUTES);

  private String explanation = "";
  private Cancellable timeout;

  {
    startWith(State.UNEMPLOYED, null);
    when(State.UNEMPLOYED,
        matchEvent(Offer.class,
            (offer, zero) -> {
              explain("Received new task: " + offer + ".");
              final ActorRef agent = XMPP.register(offer.room(), context());
              final TBTSRoomAgent.Status status = AkkaTools.ask(agent, TBTSRoomAgent.Status.class);
              final JID lastWorker = status.lastWorker();
              sender().tell(new Ok(), self());
              if (status.isLastWorkerActive()) {
                //noinspection ConstantConditions
                explain("Trying to get active worker " + lastWorker.local() + " back to the task.");
                Experts.tellTo(lastWorker, new Resume(offer), self(), context());
                final Task task = new Task(offer, status);
                task.onTask = lastWorker;
                return goTo(State.WORK_TRACKING).using(task.enter(lastWorker));
              }
              else {
                explain("No active work on task found.");
                final Task task = new Task(offer, status);
                if (lastWorker != null) {
                  explain("Trying to get the recent active worker (" + lastWorker + ") back to the task.");
                  nextTimer(AkkaTools.scheduleTimeout(context(), RETRY_TIMEOUT, self()));
                  return goTo(State.STARVING).using(task.check(lastWorker));
                }
                else return lookForExpert(task).using(task);
              }
            }
        )
    );

    when(State.STARVING,
        matchEvent(Ok.class,
            (ok, task) -> {
              nextTimer(null);
              explain("Received agreement from expert " + Experts.jid(sender()).local());
              final JID expert = Experts.jid(sender());
              if (interview(expert, task)) {
                explain("Expert passed interview, sending him an invitation.");
                return goTo(State.INVITE).using(task.invite(expert));
              }
              else {
                explain("Expert failed interview, canceling.");
                task.refuse(expert);
                return stay();
              }
            }
        ).event(ActorRef.class,
            (expert, task) -> {
              final JID jid = Experts.jid(expert);
              explain("Labor exchange send us new candidate: " + jid + ".");
              if (!task.refused.contains(jid)) {
                explain("Have not seen him before, sending offer.");
                task.check(jid);
              }
              else explain("This candidate has already refused our invitation/check/failed interview. Ignoring.");
              return stay();
            }
        ).event(Cancel.class,
            (cancel, task) -> !task.jid().bareEq(XMPP.jid(sender())),
            (cancel, task) -> {
              nextTimer(null);
              final JID expert = Experts.jid(sender());
              explain("Expert " + expert + " refused the check.");
              return stay().using(task.refusedCheck(expert));
            }
        ).event(Cancel.class,
            (cancel, task) -> task.jid().bareEq(XMPP.jid(sender())),
            (cancel, task) -> cancelTask(task)
        ).event(Timeout.class,
            (to, task) -> lookForExpert(task)
        )
    );

    when(State.INVITE,
        matchEvent(Start.class,
            (start, task) -> task.invited(Experts.jid(sender())),
            (start, task) -> {
              final JID expert = Experts.jid(sender());
              explain("Expert " + expert + " started working on task " + task.offer.room().local() + ".");
              XMPP.send(new Message(expert, task.jid(), new Start()), context());
              return goTo(State.WORK_TRACKING).using(task.enter(expert));
            }
        ).event(Cancel.class, // from invitation
            (cancel, task) -> task.invited(Experts.jid(sender())),
            (cancel, task) -> {
              final JID expert = Experts.jid(sender());
              explain("Expert " + expert + " declined invitation");
              XMPP.send(new Message(expert, task.jid(), new Cancel()), context());
              return task.refusedInvitation(expert) ? stay() : lookForExpert(task);
            }
        ).event(Ignore.class, // from invitation
            (cancel, task) -> task.invited(Experts.jid(sender())),
            (cancel, task) -> {
              final JID expert = Experts.jid(sender());
              explain("Expert " + expert + " ignored invitation");
              return task.refusedInvitation(expert) ? stay() : lookForExpert(task);
            }
        ).event(ActorRef.class,
            (expert, task) -> {
              final JID jid = Experts.jid(expert);
              explain("Labor exchange send us new candidate: " + jid + ".");
              if (!task.refused.contains(jid)) {
                explain("Have not seen him before, sending offer.");
                task.check(jid);
              }
              else explain("This candidate has already refused our invitation/check/failed interview. Ignoring.");
              return stay();
            }
        ).event(Cancel.class, // from check
            (cancel, task) -> !task.invited(Experts.jid(sender())) && !task.jid().bareEq(XMPP.jid(sender())),
            (cancel, task) -> {
              explain("Expert " + Experts.jid(sender()) + " is not ready");
              return stay().using(task.refusedCheck(Experts.jid(sender())));
            }
        ).event(Cancel.class, // from room
            (cancel, task) -> task.jid().bareEq(XMPP.jid(sender())),
            (cancel, task) -> cancelTask(task)
        ).event(Ok.class, // from check
            (ok, task) -> {
              explain("Received agreement from expert " + Experts.jid(sender()).local());
              final JID expert = Experts.jid(sender());
              if (interview(expert, task)) {
                explain("Expert passed interview, adding him to queue.");
                return stay().using(task.invite(expert));
              }
              else {
                explain("Expert failed interview, canceling.");
                task.refuse(expert);
                return stay();
              }
            }
        )
    );

    when(State.WORK_TRACKING,
        matchEvent(Done.class,
            (done, task) -> task.onTask(Experts.jid(sender())),
            (done, task) -> {
              explain("Expert has finished working on the " + task.offer.room().local() + ". Sending notification to the room.");
              XMPP.send(new Message(Experts.jid(sender()), task.jid(), done), context());
              reference(context()).tell(new Done(), self());
              return goTo(State.UNEMPLOYED).using(null);
            }
        ).event(Cancel.class, // cancel from expert
            (cancel, task) -> task.onTask(Experts.jid(sender())),
            (cancel, task) -> {
              explain("Expert canceled task. Looking for other worker.");
              XMPP.send(new Message(Experts.jid(sender()), task.jid(), cancel), context());
              return lookForExpert(task).using(task.exit());
            }
        ).event(Cancel.class, // cancel from the room
            (cancel, task) -> task.jid().bareEq(XMPP.jid(sender())),
            (cancel, task) -> cancelTask(task)
        ).event(Suspend.class,
            (suspend, task) -> {
              explain("Expert suspended his work. Sending notification to the room.");
              XMPP.send(new Message(Experts.jid(sender()), task.jid(), new Suspend()), context());
              return stay();
            }
        ).event(Resume.class,
            (suspend, task) -> {
              explain("Expert resumed his work. Sending notification to the room.");
              XMPP.send(new Message(Experts.jid(sender()), task.jid(), new Resume()), context());
              return stay();
            }
        ).event(ActorRef.class,
            (expert, task) -> task.onTask(Experts.jid(expert)),
            (expert, task) -> {
              explain("Worker returned online, sending resume.");
              expert.tell(new Resume(task.offer), self());
              return stay();
            }
        )
    );

    whenUnhandled(matchEvent(
        ActorRef.class,
        (expert, task) -> {
          explain("Unemployed expert ignored in improper state");
          return stay();
        }
    ).event(Offer.class,
        (offer, task) -> {
          explain("Already working on " + task.offer.room() + ".");
          return stay().replying(new Cancel());
        }
    ));

    onTermination(
        matchStop(Normal(),
            (state, data) -> log.fine("BrokerRole stopped" + data.offer)
        ).stop(Shutdown(),
            (state, data) -> log.warning("BrokerRole shut down on " + data)
        ).stop(Failure.class,
            (reason, data, state) -> log.warning("ExpertRole terminated on " + data + " in state " + state)
        )
    );

    initialize();
  }

  @Override
  public void processEvent(FSM.Event<Task> event, Object source) {
    try {
      final State from = stateName();
      super.processEvent(event, source);
      final State to = stateName();
      log.fine("Broker " + (stateData() != null ? "on task " + stateData().offer.room().local() + " " : "")
          + from + " -> " + to
          + ". " + explanation);
      explanation = "";
    }
    catch (Exception e) {
      log.log(Level.SEVERE, "Exception during event handling", e);
    }
  }

  private FSM.State<State, Task> cancelTask(Task task) {
    explain("The task was canceled by client. Sending all experts cancel.");
    nextTimer(null);
    task.cancel();
    return goTo(State.UNEMPLOYED).using(null);
  }

  private void nextTimer(Cancellable timeout) {
    if (this.timeout != null)
      this.timeout.cancel();
    this.timeout = timeout;
  }

  private void explain(String explanation) {
    if (!explanation.isEmpty() && !explanation.startsWith(" "))
      explanation += " ";
    this.explanation += explanation;
  }

  private FSM.State<State, Task> lookForExpert(Task task) {
    explain("Going to labor exchange to find expert.");
    task.roomStatus = TBTSRoomAgent.status(task.offer.room(), context());
    task.refused.clear();
    experts(context()).tell(task.offer, self());
    nextTimer(AkkaTools.scheduleTimeout(context(), RETRY_TIMEOUT, self()));
    return stateName() != State.STARVING ? goTo(State.STARVING) : stay();
  }

  private boolean interview(JID expert, Task task) {
    task.roomStatus = TBTSRoomAgent.status(task.offer.room(), context());
    return task.roomStatus.interview(expert);
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
  }
}

package com.tbts.server.agents.roles;

import akka.actor.*;
import akka.util.Timeout;
import com.tbts.model.Offer;
import com.tbts.model.Operations.*;
import com.tbts.server.agents.TBTSRoomAgent;
import com.tbts.server.agents.XMPP;
import com.tbts.util.akka.AkkaTools;
import com.tbts.xmpp.JID;
import com.tbts.xmpp.stanza.Message;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static com.tbts.server.agents.LaborExchange.*;

/**
 * User: solar
 * Date: 18.12.15
 * Time: 22:50
 */
public class BrokerRole extends AbstractFSM<BrokerRole.State, BrokerRole.Task> {
  private static final Logger log = Logger.getLogger(BrokerRole.class.getName());
  public class Task {
    public final Offer offer;
    private TBTSRoomAgent.Status roomStatus;
    private final Queue<JID> candidates = new ArrayDeque<>();
    private final Set<JID> refused = new HashSet<>();
    private JID invited;
    private JID onTask;

    public Task(Offer offer, TBTSRoomAgent.Status roomStatus) {
      this.offer = offer;
      this.roomStatus = roomStatus;
    }

    public Task candidate(JID expert) {
      candidates.add(expert);
      Experts.tellTo(expert, offer, self(), context());
      return this;
    }

    public Task enter(JID expert) {
      invited = expert;
      onTask = expert;
      return this;
    }

    public boolean invited(JID from) {
      return from.bareEq(invited);
    }

    public JID next() {
      return candidates.poll();
    }

    public boolean onTask(JID expert) {
      return expert.bareEq(onTask);
    }

    public Task invite(JID expert) {
      invited = expert;
      JID jid;
      while((jid = next()) != null) {
        if (!jid.bareEq(expert))
          Experts.tellTo(jid, new Cancel(), self(), context());
      }
      candidates.add(expert);
      sender().tell(new Invite(), self());
      return this;
    }

    public Task refused(JID jid) {
      refused.add(jid);
      return this;
    }

    public JID jid() {
      return offer.room();
    }

    public void refuse(JID expert) {
      refused.add(expert);
      sender().tell(new Cancel(), self());
    }

    public Task exit() {
      invited = null;
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
              if (status.isLastWorkerActive()) {
                //noinspection ConstantConditions
                explain("Trying to get active worker " + lastWorker.local() + " back to the task.");
                Experts.tellTo(lastWorker, new Resume(offer), self(), context());
                final Task task = new Task(offer, status);
                task.onTask = lastWorker;
                return goTo(State.WORK_TRACKING).using(task.candidate(lastWorker).invite(lastWorker).enter(lastWorker)).replying(new Ok());
              }
              else {
                explain("No active work on task found.");
                final Task task = new Task(offer, status);
                if (lastWorker != null) {
                  explain("Trying to get the recent active worker (" + lastWorker + ") back to the task.");
                  nextTimer(AkkaTools.scheduleTimeout(context(), RETRY_TIMEOUT, self()));
                  task.candidate(lastWorker);
                  return goTo(State.STARVING).using(task.candidate(lastWorker)).replying(new Ok());
                }
                else return lookForExpert(task).using(task).replying(new Ok());
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
                XMPP.send(new Message(expert, task.jid(), new Invite()), context());
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
                task.candidate(jid);
              }
              else explain("This candidate has already refused our invitation/check/failed interview. Ignoring.");
              return stay();
            }
        ).event(Cancel.class,
            (cancel, task) -> !task.jid().bareEq(XMPP.jid(sender())),
            (cancel, task) -> {
              nextTimer(null);
              explain("Expert " + Experts.jid(sender()) + " refused the check.");
              return stay().using(task.refused(Experts.jid(sender())));
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
              explain("Expert " + Experts.jid(sender()) + " started working on task " + task.offer.room().local() + ".");
              XMPP.send(new Message(Experts.jid(sender()), task.jid(), new Start()), context());
              return goTo(State.WORK_TRACKING).using(task.enter(task.invited));
            }
        ).event(Cancel.class, // from invitation
            (cancel, task) -> task.invited(Experts.jid(sender())),
            (cancel, task) -> {
              explain("Expert " + Experts.jid(sender()) + " declined invitation");
              XMPP.send(new Message(Experts.jid(sender()), task.jid(), new Cancel()), context());
              return lookForExpert(task);
            }
        ).event(Cancel.class, // from check
            (cancel, task) -> !task.invited(Experts.jid(sender())) && !task.jid().bareEq(XMPP.jid(sender())),
            (cancel, task) -> {
              explain("Expert " + Experts.jid(sender()) + " is not ready");
              return stay().using(task.refused(Experts.jid(sender())));
            }
        ).event(Cancel.class, // from room
            (cancel, task) -> task.jid().bareEq(XMPP.jid(sender())),
            (cancel, task) -> cancelTask(task)
        ).event(Ok.class, // from check
            (ok, task) -> {
              explain("Check from " + Experts.jid(sender()) + " received when invitation is already sent to " + task.invited + ". Freeing candidate.");
              return stay().replying(new Cancel());
            }
        )
    );

    when(State.WORK_TRACKING,
        matchEvent(Done.class,
            (done, task) -> task.onTask(Experts.jid(sender())),
            (done, task) -> {
              explain("Expert has finished working on the " + task.offer.room().local() + ". Sending notification to the room.");
              XMPP.send(new Message(Experts.jid(sender()), task.jid(), done), context());
              reference(context()).tell(Done.class, self());
              return goTo(State.UNEMPLOYED).using(null);
            }
        ).event(Cancel.class, // cancel from expert
            (cancel, task) -> task.onTask(JID.parse(sender().path().name())),
            (cancel, task) -> {
              explain("Expert canceled task. Looking for other worker.");
              XMPP.send(new Message(Experts.jid(sender()), task.jid(), cancel), context());
              return lookForExpert(task).using(task.exit());
            }
        ).event(Cancel.class, // cancel from the room
            (cancel, task) -> task.jid().bareEq(JID.parse(sender().path().name())),
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
            (state, data) -> log.warning("BrokerRole shut down on " + data.offer)
        ).stop(Failure.class,
            (reason, data, state) -> log.warning("ExpertRole terminated on " + data + " in state " + state)
        )
    );

    initialize();
  }

  @Override
  public void processEvent(FSM.Event<Task> event, Object source) {
    final State from = stateName();
    super.processEvent(event, source);
    final State to = stateName();
    log.fine("Broker " + (stateData() != null ? "on task " + stateData().offer.room().local() + " " : "")
        + from + " -> " + to
        + ". " + explanation);
    explanation = "";
  }

  private FSM.State<State, Task> cancelTask(Task task) {
    nextTimer(null);
    explain("The task was canceled by client. Sending all experts cancel.");

    JID expert;
    while ((expert = task.next()) != null)
      Experts.tellTo(expert, new Cancel(), self(), context());
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
    final ActorSelection roomAgent = XMPP.agent(task.offer.room(), context());
    task.roomStatus = AkkaTools.ask(roomAgent, TBTSRoomAgent.Status.class);
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

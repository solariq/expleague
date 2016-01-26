package com.tbts.server.agents.roles;

import akka.actor.AbstractFSM;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.FSM;
import akka.util.Timeout;
import com.spbsu.commons.util.Pair;
import com.tbts.model.ExpertManager;
import com.tbts.model.Offer;
import com.tbts.model.Operations;
import com.tbts.model.Operations.Cancel;
import com.tbts.server.agents.LaborExchange;
import com.tbts.server.agents.TBTSRoomAgent;
import com.tbts.server.agents.XMPP;
import com.tbts.util.akka.AkkaTools;
import com.tbts.xmpp.JID;
import com.tbts.xmpp.stanza.Message;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static com.tbts.model.Operations.*;
import static com.tbts.server.agents.LaborExchange.*;

/**
 * User: solar
 * Date: 18.12.15
 * Time: 22:50
 */
public class BrokerRole extends AbstractFSM<BrokerRole.State, BrokerRole.Task> {
  private static final Logger log = Logger.getLogger(BrokerRole.class.getName());
  public static class Task {
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
      return this;
    }

    public Task enter(JID expert) {
      invited = null;
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
      return this;
    }

    public boolean isWorker(JID expert) {
      for (final JID jid : roomStatus.workers()) {
        if (jid.bareEq(expert))
          return true;
      }
      return false;
    }

    public Task refused(JID jid) {
      refused.add(jid);
      return this;
    }

    public JID jid() {
      return offer.room();
    }
  }

  public static final FiniteDuration RETRY_TIMEOUT = Duration.apply(2, TimeUnit.MINUTES);

  private Cancellable timeout;

  {
    startWith(State.UNEMPLOYED, null);
    when(State.UNEMPLOYED,
        matchEvent(Offer.class,
            (offer, zero) -> {
              final ActorRef agent = XMPP.register(offer.room(), context());
              final TBTSRoomAgent.Status status = AkkaTools.ask(agent, TBTSRoomAgent.Status.class);
              if (status.isLastWorkerActive()) {
                final ActorRef expert = Experts.agent(status.lastWorker(), context());
                expert.tell(new Resume(offer), self());
                final Task task = new Task(offer, status);
                task.onTask = status.lastWorker();
                return goTo(State.WORK_TRACKING).using(task).replying(new Ok());
              }
              else {
                final Task task = new Task(offer, status);
                if (status.lastWorker() != null) {
                  timeout = AkkaTools.scheduleTimeout(context(), RETRY_TIMEOUT, self());
                  final ActorRef expert = Experts.agent(status.lastWorker(), context());
                  expert.tell(offer, self());
                  return goTo(State.STARVING).using(task).replying(new Ok());
                }
                else return lookForExpert(task).using(task).replying(new Ok());
              }
            }
        )
    );

    when(State.STARVING,
        matchEvent(Ok.class,
            (ok, task) -> {
              if (timeout != null) {
                timeout.cancel();
                timeout = null;
              }
              final JID expert = Experts.jid(sender());
              if (interview(expert, task)) {
                XMPP.send(new Message(expert, task.jid(), new Invite()), context());
                sender().tell(new Invite(), self());
                return goTo(State.INVITE).using(task.invite(expert));
              }
              else {
                task.refused.add(expert);
                sender().tell(new Cancel(), self());
                return stay();
              }
            }
        ).event(ActorRef.class,
            (expert, task) -> {
              if (!task.refused.contains(Experts.jid(expert))) {
                expert.tell(task.offer, self());
              }
              return stay();
            }
        ).event(Cancel.class,
            (cancel, task) -> stay().using(task.refused(Experts.jid(sender())))
        ).event(Cancel.class, // from room
            (cancel, task) -> task.jid().bareEq(JID.parse(sender().path().name())),
            (cancel, task) -> {
              JID expert;
              while ((expert = task.next()) != null)
                context().actorSelection("/user/labor-exchange/experts-dpt/" + expert.bare().toString()).tell(new Cancel(), self());
              return goTo(State.UNEMPLOYED).using(null);
            }
        ).event(Timeout.class,
            (to, task) -> lookForExpert(task)
        ).event(Offer.class,
            (offer, task) -> stay().replying(new Cancel())
        )
    );

    when(State.INVITE,
        matchEvent(Start.class,
            (start, task) -> task.invited(Experts.jid(sender())),
            (start, task) -> {
              XMPP.send(new Message(Experts.jid(sender()), task.jid(), new Start()), context());
              JID expert;
              while ((expert = task.next()) != null)
                context().actorSelection("/user/labor-exchange/experts-dpt/" + expert.bare().toString()).tell(new Cancel(), self());
              return goTo(State.WORK_TRACKING).using(task.enter(task.invited));
            }
        ).event(Cancel.class, // from invitation
            (cancel, task) -> task.invited(Experts.jid(sender())),
            (cancel, task) -> {
              XMPP.send(new Message(Experts.jid(sender()), task.jid(), new Cancel()), context());
              return lookForExpert(task);
            }
        ).event(Cancel.class, // from check
            (cancel, task) -> !task.invited(Experts.jid(sender())) && !task.jid().bareEq(JID.parse(sender().path().name())),
            (cancel, task) -> stay().using(task.refused(Experts.jid(sender())))
        ).event(Cancel.class, // from room
            (cancel, task) -> task.jid().bareEq(JID.parse(sender().path().name())),
            (cancel, task) -> {
              JID expert;
              while ((expert = task.next()) != null)
                context().actorSelection("/user/labor-exchange/experts-dpt/" + expert.bare().toString()).tell(new Cancel(), self());
              return goTo(State.UNEMPLOYED).using(null);
            }
        ).event(Ok.class, // from check
            (ok, task) -> stay()
        ).event(Offer.class,
            (offer, task) -> stay().replying(new Cancel())
        )
    );

    when(State.WORK_TRACKING,
        matchEvent(Done.class,
            (done, task) -> task.onTask(Experts.jid(sender())),
            (done, task) -> {
              XMPP.send(new Message(Experts.jid(sender()), task.jid(), done), context());
              reference(context()).tell(Done.class, self());
              return goTo(State.UNEMPLOYED).using(null);
            }
        ).event(Cancel.class, // cancel from expert
            (cancel, task) -> task.onTask(JID.parse(sender().path().name())),
            (cancel, task) -> {
              XMPP.send(new Message(Experts.jid(sender()), task.jid(), cancel), context());
              return lookForExpert(task).using(task.enter(null));
            }
        ).event(Cancel.class, // cancel from the room
            (cancel, task) -> task.jid().bareEq(JID.parse(sender().path().name())),
            (cancel, task) -> {
              Experts.agent(task.onTask, context()).tell(cancel, self());
              return goTo(State.UNEMPLOYED).using(null);
            }
        ).event(Offer.class,
            (offer, task) -> stay().replying(new Cancel())
        ).event(Suspend.class,
            (suspend, task) -> {
              XMPP.send(new Message(Experts.jid(sender()), task.jid(), new Suspend()), context());
              return stay();
            }
        ).event(Resume.class,
            (suspend, task) -> {
              XMPP.send(new Message(Experts.jid(sender()), task.jid(), new Resume()), context());
              return stay();
            }
        )
    );

    whenUnhandled(matchEvent(
        ActorRef.class,
        (expert, task) -> stay()
    ));

    onTransition((from, to) -> {
      log.fine(from + " -> " + to + (nextStateData() != null ? " " + nextStateData().offer : ""));
    });

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

  private FSM.State<State, Task> lookForExpert(Task task) {
    log.fine(task.offer.room() + " is looking for experts in state: " + stateName());
    final ActorRef roomAgent = XMPP.register(task.offer.room(), context());
    task.roomStatus = AkkaTools.ask(roomAgent, TBTSRoomAgent.Status.class);
    task.refused.clear();
    experts(context()).tell(task.offer, self());
    timeout = AkkaTools.scheduleTimeout(context(), RETRY_TIMEOUT, self());
    return stateName() != State.STARVING ? goTo(State.STARVING) : stay();
  }

  private boolean interview(JID expert, Task task) {
    if (task.isWorker(expert))
      return true;
    final ExpertManager.Record record = ExpertManager.instance().record(expert.bare());
    final Optional<Pair<JID, ExpertRole.State>> any = record.entries()
        .filter(entry -> task.offer.room().bareEq(entry.first))
        .filter(entry -> entry.getSecond() == ExpertRole.State.INVITE)
        .findAny();
    return !any.isPresent();
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

package com.tbts.server.agents.roles;

import akka.actor.AbstractFSM;
import akka.actor.ActorRef;
import com.spbsu.commons.util.Pair;
import com.tbts.model.ExpertManager;
import com.tbts.model.Offer;
import com.tbts.model.Operations;
import com.tbts.server.agents.LaborExchange;
import com.tbts.server.agents.TBTSRoomAgent;
import com.tbts.server.agents.XMPP;
import com.tbts.util.akka.AkkaTools;
import com.tbts.xmpp.JID;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 18.12.15
 * Time: 22:50
 */
public class BrokerRole extends AbstractFSM<BrokerRole.State, BrokerRole.Task> {
  private static final Logger log = Logger.getLogger(BrokerRole.class.getName());
  public static class Task {
    public final Offer offer;
    private final Queue<JID> candidates = new ArrayDeque<>();
    private JID invited;
    private JID onTask;

    public Task(Offer offer) {
      this.offer = offer;
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
  }

  {
    startWith(State.UNEMPLOYED, null);
    when(State.UNEMPLOYED,
        matchEvent(Offer.class,
            (offer, zero) -> {
              final ActorRef agent = XMPP.register(offer.room(), context());
              final TBTSRoomAgent.Status status = AkkaTools.ask(agent, TBTSRoomAgent.Status.class);
              if (status.worker() != null) {
                final ActorRef expert = LaborExchange.registerExpert(status.worker(), context());
                expert.tell(new Operations.Resume(offer), self());
                final Task task = new Task(offer);
                task.onTask = status.worker();
                return goTo(State.WORK_TRACKING).using(task);
              }
              else {
                final Task task = new Task(offer);
                LaborExchange.experts(context()).tell(offer, self());
                return goTo(State.STARVING).using(task).replying(new Operations.Ok());
              }
            }
        )
    );

    when(State.STARVING,
        matchEvent(ActorRef.class,
            (expert, task) -> {
              if (interview(JID.parse(sender().path().name()), task))
                expert.tell(task.offer, self());
              return stay();
            }
        ).event(Operations.Ok.class,
            (ok, task) -> {
              final JID expert = JID.parse(sender().path().name());
              if (interview(expert, task)) {
                sender().tell(new Operations.Invite(), self());
                return goTo(State.INVITE).using(task.invite(expert));
              }
              else {
                sender().tell(new Operations.Cancel(), self());
                return stay();
              }
            }
        ).event(Offer.class,
            (offer, task) -> stay().replying(new Operations.Cancel())
        )
    );

    when(State.INVITE,
        matchEvent(Operations.Start.class,
            (start, task) -> task.invited(JID.parse(sender().path().name())),
            (start, task) -> {
              JID expert;
              while ((expert = task.next()) != null)
                context().actorSelection("/user/labor-exchange/experts-dpt/" + expert.getAddr()).tell(new Operations.Cancel(), self());
              return goTo(State.WORK_TRACKING).using(task.enter(task.invited));
            }
        ).event(Operations.Cancel.class,
            (cancel, task) -> task.invited(JID.parse(sender().path().name())),
            (cancel, task) -> {
              final JID expert = task.next();
              if (expert == null)
                return goTo(State.STARVING);
              LaborExchange.registerExpert(expert, context()).tell(new Operations.Invite(), self());
              return stay();
            }
        ).event(Operations.Ok.class,
            (ok, task) -> stay().using(task.candidate(JID.parse(sender().path().name())))
        ).event(Offer.class,
            (offer, task) -> stay().replying(new Operations.Cancel())
        )
    );

    when(State.WORK_TRACKING,
        matchEvent(Operations.Done.class,
            (done, task) -> task.onTask(JID.parse(sender().path().name())),
            (done, task) -> goTo(State.UNEMPLOYED).using(null)
        ).event(Operations.Cancel.class,
            (cancel, task) -> task.onTask(JID.parse(sender().path().name())),
            (cancel, task) -> {
              LaborExchange.experts(context()).tell(task.offer, self());
              return goTo(State.STARVING).using(task.enter(null));
            }
        ).event(Offer.class,
            (offer, task) -> stay().replying(new Operations.Cancel())
        )
    );

    whenUnhandled(matchEvent(
        ActorRef.class,
        (expert, task) -> stay()
    ));

    onTransition((from, to) -> {
      log.fine(from + " -> " + to + (nextStateData() != null ? " " + nextStateData().offer : ""));
    });

    initialize();
  }

  private boolean interview(JID expert, Task task) {
    final ExpertManager.Record record = ExpertManager.instance().record(expert.bare());
    final Optional<Pair<JID, ExpertRole.State>> any = record.entries()
        .filter(entry -> task.offer.room().equals(entry.first))
        .filter(entry -> entry.getSecond() == ExpertRole.State.INVITE)
        .findAny();
    return !any.isPresent();
  }

  enum State {
    UNEMPLOYED,
    STARVING,
    INVITE,
    WORK_TRACKING,
  }
}

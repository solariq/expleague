package com.tbts.server.agents.roles;

import akka.actor.AbstractFSM;
import akka.actor.ActorRef;
import com.spbsu.commons.util.Pair;
import com.tbts.modelNew.ExpertManager;
import com.tbts.modelNew.Offer;
import com.tbts.modelNew.Operations;
import com.tbts.server.agents.LaborExchange;
import com.tbts.xmpp.JID;
import com.tbts.xmpp.stanza.Presence;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;

/**
 * User: solar
 * Date: 18.12.15
 * Time: 22:50
 */
public class BrokerRole extends AbstractFSM<BrokerRole.State, BrokerRole.Task> {
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

  public static class On {
    private final Task offer;
    public On(Task offer) {
      this.offer = offer;
    }
  }

  {
    startWith(State.UNEMPLOYED, null);
    when(State.UNEMPLOYED,
        matchEvent(Offer.class,
            (offer, zero) -> {
              LaborExchange.experts(context()).tell(offer, self());
              final Task task = new Task(offer);
              return goTo(State.STARVING).using(task).replying(new On(task));
            }
        ).event(ActorRef.class,
            (expert, zero) -> stay().replying(new Operations.Cancel())
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
        matchEvent(Presence.class,
            (presence, task) -> task.invited(presence.from()),
            (presence, task) -> {
              JID expert;
              while ((expert = task.next()) != null) {
                context().actorSelection("/user/labor-exchange/experts-dpt/" + expert.getAddr()).tell(new Operations.Cancel(), self());
              }
              return goTo(State.WORK_TRACKING).using(task.enter(presence.from()));
            }
        ).event(Operations.Cancel.class,
            (cancel, task) -> task.invited(JID.parse(sender().path().name())),
            (cancel, task) -> {
              final JID expert = task.next();
              if (expert == null)
                return goTo(State.STARVING);
              sender().tell(new Operations.Invite(), self());
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
        ).event(Offer.class,
            (offer, task) -> stay().replying(new Operations.Cancel())
        )
    );


    onTransition((from, to) -> {
      System.out.println(from + " -> " + to + (nextStateData() != null ? " " + nextStateData().offer : ""));
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

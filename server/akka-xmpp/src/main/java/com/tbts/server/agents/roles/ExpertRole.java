package com.tbts.server.agents.roles;

import akka.actor.AbstractFSM;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.util.Timeout;
import com.spbsu.commons.util.Pair;
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

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 17.12.15
 * Time: 14:16
 */
public class ExpertRole extends AbstractFSM<ExpertRole.State, Pair<Offer, ActorRef>> {
  private static final Logger log = Logger.getLogger(ExpertRole.class.getName());
  public static final FiniteDuration CHECK_TIMEOUT = Duration.create(10, TimeUnit.SECONDS);
  public static final FiniteDuration INVITE_TIMEOUT = Duration.create(10, TimeUnit.SECONDS);
  private Cancellable timer;

  {
    startWith(State.OFFLINE, null);

    when(State.OFFLINE,
        matchEvent(
            Presence.class,
            (presence, task) -> presence.available(),
            (presence, zero) -> zero == null ? goTo(State.READY) : goTo(State.BUSY)
        ).event(Operations.Resume.class,
            (resume, zero) -> zero == null,
            (resume, zero) -> stay().using(Pair.create(resume.offer(), sender()))
        ));
    when(State.READY,
        matchEvent(
            Offer.class,
            (offer, zero) -> zero == null,
            (offer, zero) -> {
              XMPP.send(new Message(XMPP.jid(), jid(), offer), context());
              timer = AkkaTools.scheduleTimeout(context(), CHECK_TIMEOUT, self());
              return goTo(State.CHECK).using(Pair.create(offer, sender()));
            }
        ).event(
            Presence.class,
            (presence, task) -> presence.available() ? stay() : goTo(State.OFFLINE)
        ).event(Operations.Resume.class,
            (resume, zero) -> zero == null,
            (resume, zero) -> goTo(State.BUSY).using(Pair.create(resume.offer(), sender()))
        )
    );
    when(State.CHECK,
        matchEvent( // expert accepted check
            Message.class,
            (msg, task) -> msg.get(Operations.Ok.class) != null,
            (msg, task) -> {
              task.getSecond().tell(new Operations.Ok(), self());
              timer.cancel();
              return stay().using(task);
            }
        ).event( // expert canceled check
            (msg, task) -> msg instanceof Timeout || (msg instanceof Message && ((Message) msg).get(Operations.Cancel.class) != null),
            (msg, task) -> {
              if (msg instanceof Timeout)
                XMPP.send(new Message(XMPP.jid(), jid(), task.first, new Operations.Cancel()), context());
              task.getSecond().tell(new Operations.Cancel(), self());
              timer.cancel();
              return goTo(State.READY).using(null);
            }
        ).event( // broker sent invitation
            Operations.Invite.class,
            (invite, task) -> task.getSecond().equals(sender()),
            (invite, task) -> {
              final Message message = new Message(task.first.room(), jid(), task.first);
              invite.form(message, task.first);
              XMPP.send(message, context());
              timer = AkkaTools.scheduleTimeout(context(), INVITE_TIMEOUT, self());
              return goTo(State.INVITE);
            }
        ).event(
            Presence.class,
            (presence, task) -> !presence.available(),
            (presence, task) -> {
              if (task != null)
                task.getSecond().tell(new Operations.Cancel(), self());
              timer.cancel();
              return goTo(State.OFFLINE).using(null);
            }
        ).event(
            Presence.class,
            (presence, task) -> presence.available(),
            (presence, task) -> stay()
        )
    );
    when(State.INVITE,
        matchEvent(
            Stanza.class,
            (stanza, task) -> {
              if (task.getFirst().room().bareEq(stanza.to())) {
                timer.cancel();
                if (stanza instanceof Message && ((Message) stanza).has(Operations.Cancel.class))
                  return goTo(State.READY).using(null);
                else {
                  task.second.tell(new Operations.Start(), self());
                  XMPP.send(new Message(jid(), task.first.room(), new Operations.Start()), context());
                  return goTo(State.BUSY);
                }
              }
              return stay();
            }
        ).event(
            Timeout.class,
            (timeout, task) -> {
              XMPP.send(new Message(XMPP.jid(), jid(), task.first, new Operations.Cancel()), context());
              task.getSecond().tell(new Operations.Cancel(), self());
              return goTo(State.READY).using(null);
            }
        )
    );
    when(State.BUSY,
        matchEvent(
            Presence.class,
            (presence, task) -> {
              if (presence.available() && (presence.to() == null || !presence.to().bareEq(task.first.room()))) {
                task.second.tell(new Operations.Done(), self());
                XMPP.send(new Message(jid(), task.first.room(), new Operations.Done()), context());
                return goTo(State.READY).using(null);
              }
              return stay();
            }
        ).event(Message.class,
            (msg, offer) -> stay()
        )
    );

    whenUnhandled(
        matchEvent(Operations.Resume.class,
            (resume, task) -> {
              XMPP.send(new Message(jid(), resume.offer().room(), new Operations.Done()), context());
              return stay().replying(new Operations.Cancel());
            }
        ).event(Offer.class,
            (offer, task) -> stay().replying(new Operations.Cancel())
        )
    );

//    whenUnhandled(matchAnyEvent((state, data) -> stay().replying(new Operations.Cancel())));

    onTransition((from, to) -> {
      final Offer first = nextStateData() != null ? nextStateData().first : null;
      ExpertManager.instance().record(jid()).entry(first, to);
      if (from != to)
        context().parent().tell(to, self());
    });

    onTransition((from, to) -> {
      final Offer first = nextStateData() != null ? nextStateData().first : null;
      log.fine(from + " -> " + to + (first != null ? " " + first : ""));
    });

    onTransition((from, to) -> {
      if (to == State.READY)
        LaborExchange.reference(context()).tell(self(), self());
    });
    initialize();
  }

  public JID jid() {
    return JID.parse(self().path().name());
  }

  public enum State {
    CHECK,
    INVITE,
    BUSY,
    READY,
    OFFLINE,
  }
}

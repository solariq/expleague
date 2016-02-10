package com.expleague.server.services;

import akka.actor.AbstractFSM;
import akka.actor.ActorRef;
import akka.actor.Props;
import com.expleague.util.akka.UntypedActorAdapter;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.control.roster.Query;
import com.expleague.xmpp.stanza.Iq;
import com.expleague.xmpp.stanza.Iq.IqType;
import scala.runtime.AbstractFunction0;

/**
 * User: solar
 * Date: 15.12.15
 * Time: 13:54
 */
public class Roster extends UntypedActorAdapter {
  public void invoke(Iq<Query> rosterIq) {
    final JID from = rosterIq.from();
    final ActorRef service = getContext()
        .child(from.bare().toString())
        .getOrElse(new AbstractFunction0<ActorRef>() {
          @Override
          public ActorRef apply() {
            switch (rosterIq.type()) {
              case GET:
                return getContext().actorOf(Props.create(GetBuddies.class), from.bare().toString());
              case SET:
                return getContext().actorOf(Props.create(AddBuddy.class), from.bare().toString());
            }
            throw new IllegalArgumentException();
          }
        });
    service.forward(rosterIq, getContext());
  }

  public static class AddBuddy extends AbstractFSM<AddBuddy.States, AddBuddy.Data> {
    public enum States {
      INITIAL,
      CLIENT_ROSTER_SET,
      CLIENT_SUBSCRIPTION_SET
    }

    public static class Data {
      String waitingForId;
      Iq<Query> initial;
    }
    {
      startWith(States.INITIAL, new Data());

      when(States.INITIAL,
          matchEvent(Iq.class, (iq, data) -> {
            //noinspection unchecked
            data.initial = (Iq<Query>)iq;
            final Query query = new Query();
            data.initial.get().items().stream()
                .map(item -> new Query.RosterItem(item.jid(), Query.RosterItem.Subscription.NONE, item.jid().local()))
                .forEach(query::add);
            final Iq<Query> msg = Iq.create(iq.from(), IqType.SET, query);
            data.waitingForId = msg.id();
            sender().tell(msg, self());
            return goTo(States.CLIENT_ROSTER_SET);
          }));

      when(States.CLIENT_ROSTER_SET,
          matchEvent(Iq.class, (iq, data) -> {
            if (!data.waitingForId.equals(iq.id()) || iq.type() != IqType.RESULT)
              return stop(new Failure("Expected answer for " + data.waitingForId + " but received " + iq));
            final Query query = new Query();
            data.initial.get().items().stream()
                .map(item -> new Query.RosterItem(item.jid(), Query.RosterItem.Subscription.NONE, item.jid().local(), "subscribe"))
                .forEach(query::add);
            final Iq<Query> msg = Iq.create(iq.from(), IqType.SET, query);
            data.waitingForId = msg.id();
            sender().tell(msg, self());

            return goTo(States.CLIENT_SUBSCRIPTION_SET);
          }));

      when(States.CLIENT_SUBSCRIPTION_SET,
          matchEvent(Iq.class, (iq, data) -> {
            if (!data.waitingForId.equals(iq.id()) || iq.type() != IqType.RESULT)
              return stop(new Failure("Expected answer for " + data.waitingForId + " but received " + iq));
            sender().tell(Iq.answer(data.initial), self());
            return stop();
          }));
    }
  }

  public static class GetBuddies extends UntypedActorAdapter {
    public void invoke(Iq<Query> iq) {
      sender().tell(Iq.answer(iq, iq.get()), self());
      context().stop(self());
    }
  }
}

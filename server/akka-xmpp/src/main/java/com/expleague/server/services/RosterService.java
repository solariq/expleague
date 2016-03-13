package com.expleague.server.services;

import akka.actor.AbstractFSM;
import akka.actor.ActorRef;
import akka.actor.Props;
import com.expleague.server.Roster;
import com.expleague.server.agents.LaborExchange;
import com.expleague.server.agents.XMPP;
import com.expleague.util.akka.UntypedActorAdapter;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.control.roster.RosterQuery;
import com.expleague.xmpp.stanza.Iq;
import com.expleague.xmpp.stanza.Iq.IqType;
import scala.Option;

import java.util.HashSet;
import java.util.Set;

import static com.expleague.xmpp.control.roster.RosterQuery.RosterItem.Subscription.FROM;

/**
 * User: solar
 * Date: 15.12.15
 * Time: 13:54
 */
public class RosterService extends UntypedActorAdapter {
  public void invoke(Iq<RosterQuery> rosterIq) {
    final JID from = rosterIq.from();
    final Option<ActorRef> service = context()
        .child(from.bare().toString());

    if (service.nonEmpty()) {
      service.get().forward(rosterIq, context());
      return;
    }
    switch (rosterIq.type()) {
      case GET:
        final RosterQuery query = new RosterQuery();
        final Set<JID> known = new HashSet<>(100);
        final Set<JID> online = XMPP.online(context());
        Roster.instance().favorites(rosterIq.from())
            .filter(known::add)
            .filter(online::contains)
            .map(jid -> Roster.instance().profile(jid))
            .map(p -> {
              final RosterQuery.RosterItem item = new RosterQuery.RosterItem(p.jid(), FROM, p.name());
              p.available(true);
              item.append(p);
              item.group("Favorites");
              return item;
            }).forEach(query::add);

        LaborExchange.board().topExperts()
            .filter(jid -> XMPP.online(jid, context()))
            .filter(known::add)
            .map(jid -> Roster.instance().profile(jid))
            .map(p -> {
              final JID jid = p.jid();
              final RosterQuery.RosterItem item = new RosterQuery.RosterItem(jid, FROM, p.name());
              p.available(online.contains(jid));
              item.append(p);
              item.group("Top");
              return item;
            }).forEach(query::add);
        sender().tell(Iq.answer(rosterIq, query), self());
        break;
      case SET:
        context().actorOf(Props.create(AddBuddy.class), from.bare().toString()).forward(rosterIq, context());
        break;
    }
  }

  public static class AddBuddy extends AbstractFSM<AddBuddy.States, AddBuddy.Data> {
    public enum States {
      INITIAL,
      CLIENT_ROSTER_SET,
      CLIENT_SUBSCRIPTION_SET
    }

    public static class Data {
      String waitingForId;
      Iq<RosterQuery> initial;
    }
    {
      startWith(States.INITIAL, new Data());

      when(States.INITIAL,
          matchEvent(Iq.class, (iq, data) -> {
            //noinspection unchecked
            data.initial = (Iq<RosterQuery>)iq;
            final RosterQuery query = new RosterQuery();
            data.initial.get().items().stream()
                .map(item -> new RosterQuery.RosterItem(item.jid(), RosterQuery.RosterItem.Subscription.NONE, item.jid().local()))
                .forEach(query::add);
            final Iq<RosterQuery> msg = Iq.create(iq.from(), IqType.SET, query);
            data.waitingForId = msg.id();
            sender().tell(msg, self());
            return goTo(States.CLIENT_ROSTER_SET);
          }));

      when(States.CLIENT_ROSTER_SET,
          matchEvent(Iq.class, (iq, data) -> {
            if (!data.waitingForId.equals(iq.id()) || iq.type() != IqType.RESULT)
              return stop(new Failure("Expected answer for " + data.waitingForId + " but received " + iq));
            final RosterQuery query = new RosterQuery();
            data.initial.get().items().stream()
                .map(item -> new RosterQuery.RosterItem(item.jid(), RosterQuery.RosterItem.Subscription.NONE, item.jid().local(), "subscribe"))
                .forEach(query::add);
            final Iq<RosterQuery> msg = Iq.create(iq.from(), IqType.SET, query);
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
}

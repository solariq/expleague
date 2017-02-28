package com.expleague.server.services;

import akka.actor.AbstractFSM;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import com.expleague.server.Roster;
import com.expleague.server.XMPPUser;
import com.expleague.server.agents.LaborExchange;
import com.expleague.server.agents.XMPP;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.control.roster.RosterQuery;
import com.expleague.xmpp.stanza.Iq;
import com.expleague.xmpp.stanza.Iq.IqType;
import scala.Option;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.expleague.xmpp.control.roster.RosterQuery.RosterItem.Subscription.FROM;

/**
 * User: solar
 * Date: 15.12.15
 * Time: 13:54
 */
public class RosterService extends ActorAdapter<UntypedActor> {
  @ActorMethod
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
        final List<RosterQuery.RosterItem> items = rosterIq.get().items();
        if (items.isEmpty()) {
          final Set<JID> online = XMPP.online(context());
          final RosterQuery query = new RosterQuery();
          final Set<JID> known = new HashSet<>(100);
          Roster.instance().favorites(rosterIq.from())
              .filter(known::add)
              .map(jid -> Roster.instance().profile(jid.local()))
              .map(p -> {
                final RosterQuery.RosterItem item = new RosterQuery.RosterItem(p.jid(), FROM, p.name());
                p.available(online.contains(p.jid()));
                item.append(p);
                item.group("Favorites");
                return item;
              })
              .forEach(query::add);

          LaborExchange.board().topExperts()
              .filter(known::add)
              .map(jid -> Roster.instance().profile(jid.local()))
              .map(p -> {
                final JID jid = p.jid();
                final RosterQuery.RosterItem item = new RosterQuery.RosterItem(jid, FROM, p.name());
                p.available(online.contains(jid));
                item.append(p);
                item.group("Top");
                return item;
              })
              .forEach(query::add);
          sender().tell(Iq.answer(rosterIq, query), self());
        }
        else if (items.get(0).jid().local().equals("experts")) {
          final RosterQuery query = new RosterQuery();
          Roster.instance().allExperts()
              .map(XMPPUser::jid)
              .map(jid -> Roster.instance().profile(jid.local()))
              .map(p -> {
                final JID jid = p.jid();
                final RosterQuery.RosterItem item = new RosterQuery.RosterItem(jid, FROM, p.name());
                item.append(p);
                return item;
              })
              .forEach(query::add);
          sender().tell(Iq.answer(rosterIq, query), self());
        }
        else {
          final Set<JID> online = XMPP.online(context());
          final RosterQuery query = new RosterQuery();
          items.stream()
              .map(RosterQuery.RosterItem::jid)
              .map(jid -> Roster.instance().profile(jid.local()))
              .map(p -> {
                final RosterQuery.RosterItem item = new RosterQuery.RosterItem(p.jid(), FROM, p.name());
                p.available(online.contains(p.jid()));
                item.append(p);
                return item;
              })
              .forEach(query::add);
          sender().tell(Iq.answer(rosterIq, query), self());
        }
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
            final Iq<RosterQuery> msg = Iq.create(iq.from(), iq.to(), IqType.SET, query);
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
            final Iq<RosterQuery> msg = Iq.create(iq.from(), iq.to(), IqType.SET, query);
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

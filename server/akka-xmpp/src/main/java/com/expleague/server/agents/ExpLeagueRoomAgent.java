package com.expleague.server.agents;

import com.expleague.model.*;
import com.expleague.model.Operations.*;
import com.expleague.model.RoomState;
import com.expleague.server.Roster;
import com.expleague.server.XMPPDevice;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Presence;
import com.expleague.xmpp.stanza.Stanza;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.expleague.model.RoomState.*;
import static com.expleague.server.agents.ExpLeagueOrder.Role.ACTIVE;

/**
 * User: solar
 * Date: 16.12.15
 * Time: 13:18
 */
@SuppressWarnings("UnusedParameters")
public class ExpLeagueRoomAgent extends RoomAgent {
  private static final Logger log = Logger.getLogger(ExpLeagueRoomAgent.class.getName());
  private ExpLeagueOrder[] orders;
  private RoomState state;

  public ExpLeagueRoomAgent(JID jid) {
    super(jid);
  }

  private void state(RoomState newState) {
    final RoomStateChanged stateChanged = new RoomStateChanged(newState);
    persist(stateChanged, changed -> {
      GlobalChatAgent.tell(jid(), stateChanged, context());
      log.fine("Room " + jid().local() + " state change: " + state + " -> " + newState);
      state = newState;
      if (EnumSet.of(DELIVERY, WORK, FEEDBACK).contains(newState))
        commit();
    });
  }

  @Override
  protected void onStart() {
    super.onStart();
    orders = LaborExchange.board().active(jid().local());
    if (orders.length > 0)
      Stream.of(orders).forEach(o -> LaborExchange.tell(context(), o, self()));
  }

  @Override
  public boolean update(JID from, Role role, Affiliation affiliation, boolean enforce) {
    if (role(from) == role && affiliation(from) == affiliation)
      return true;
    if (!super.update(from, role, affiliation, enforce))
      return false;
    GlobalChatAgent.tell(jid(), new RoomRoleUpdate(from, role(from), affiliation(from)), context());
    return true;
  }

  @Override
  public void exit(JID userId) {
    GlobalChatAgent.tell(jid(), new RoomRoleUpdate(userId, Role.NONE, affiliation(userId)), context());
  }

  @Override
  public Role suggestRole(JID from, Affiliation affiliation) {
    if (isTrusted(from))
      return Role.MODERATOR;
    return super.suggestRole(from, affiliation);
  }

  private boolean isTrusted(JID from) {
    final ExpertsProfile profile = Roster.instance().profile(from.local());
    return profile.trusted();
  }

  public void process(Message msg) {
    final JID from = msg.from();
    Affiliation affiliation = affiliation(from);
    final boolean trusted = isTrusted(from);
    if (trusted && affiliation == Affiliation.NONE) {
        update(from, null, Affiliation.ADMIN, true);
        affiliation = Affiliation.ADMIN;
    }
    if (msg.has(Offer.class) && EnumSet.of(Affiliation.OWNER, Affiliation.ADMIN).contains(affiliation)) { // offers handling
      final Offer offer = msg.get(Offer.class);
      final JID owner = owner();
      if (offer.client() == null)
        offer.client(owner);
      if (orders.length > 0) { // order update during the work
        final List<JID> activeExperts = Arrays.stream(orders).flatMap(o -> o.of(ACTIVE)).collect(Collectors.toList());
        Arrays.stream(orders).map(ExpLeagueOrder::broker).filter(Objects::nonNull).forEach(b -> b.tell(new Cancel(), self()));
        if (activeExperts != null)
          offer.filter().prefer(activeExperts.toArray(new JID[activeExperts.size()]));
        orders = LaborExchange.board().register(offer);
        Stream.of(orders).forEach(o -> LaborExchange.tell(context(), o, self()));
        state(WORK);
      }
      else if (affiliation == Affiliation.OWNER) {
        state(OPEN);
      }
      else if (affiliation == Affiliation.ADMIN) {
        final XMPPDevice[] devices = Roster.instance().devices(owner.local());
        if (Stream.of(devices).anyMatch(device -> device.build() > 60)) {
          state(OFFER);
        }
        else {
          state(WORK);
          orders = LaborExchange.board().register(offer);
          Stream.of(orders).forEach(o -> LaborExchange.tell(context(), o, self()));
        }
      }
      GlobalChatAgent.tell(jid(), new Message(jid(), XMPP.jid(GlobalChatAgent.ID), Message.MessageType.GROUP_CHAT, offer, new OfferChange(from.bare())), context());
    }
    else if (msg.has(Progress.class)) {
      final Progress progress = msg.get(Progress.class);
      final Progress.MetaChange metaChange = progress.change();
      if (metaChange != null) {
        switch (metaChange.target()) {
          case PATTERNS:
            break;
          case TAGS:
            if (metaChange.operation() == Progress.MetaChange.Operation.ADD)
              order(progress.order()).tag(metaChange.name());
            else
              order(progress.order()).untag(metaChange.name());
            break;
        }
        if (role(from) == Role.MODERATOR)
          update(from, Role.MODERATOR, Affiliation.ADMIN, true);
      }
    }
    else if (msg.has(Start.class) || msg.has(Resume.class)) {
      update(from, Role.PARTICIPANT, Affiliation.MEMBER, true);
    }
    else if (msg.has(Answer.class)) {
      state(DELIVERY);
      answer = msg;
    }
    else if (msg.has(Feedback.class) && state == FEEDBACK) {
      GlobalChatAgent.tell(jid(), msg.get(Feedback.class), context());
      state(CLOSED);
    }
    else if (state == FEEDBACK) {
      state(OPEN);
    }
  }

  private ExpLeagueOrder order(String id) {
    return orders.length > 0 ? orders[0] : null;
  }

  @Override
  protected void delivered(String id) {
    super.delivered(id);
    final Stanza answer = answer();
    if (state == DELIVERY && answer != null) {
      this.answer = null;
      state(FEEDBACK);
    }
  }

  Stanza answer = null;
  private Stanza answer() {
    if (answer != null)
      return answer;
    final List<Stanza> archive = archive();
    for (int i = archive.size() - 1; i >= 0; i--) {
      final Stanza stanza = archive.get(i);
      if (stanza instanceof Message && ((Message)stanza).has(Answer.class))
        return answer = stanza;
    }
    return null;
  }

  @Override
  public void onReceiveRecover(Object o) throws Exception {
    if (o instanceof RoomStateChanged) {
      state = ((RoomStateChanged)o).state();
    }
    else super.onReceiveRecover(o);
  }
}

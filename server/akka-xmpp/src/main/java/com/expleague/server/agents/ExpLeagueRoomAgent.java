package com.expleague.server.agents;

import akka.persistence.DeleteMessagesSuccess;
import com.expleague.model.*;
import com.expleague.model.Operations.*;
import com.expleague.model.RoomState;
import com.expleague.server.Roster;
import com.expleague.server.XMPPDevice;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.control.DeliveryQuery;
import com.expleague.xmpp.stanza.Message;
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

  private void state(RoomState newState, ProcessMode mode) {
    if (mode == ProcessMode.NORMAL) {
      GlobalChatAgent.tell(jid(), new RoomStateChanged(newState), context());
      log.fine("Room " + jid().local() + " state change: " + state + " -> " + newState);
      if (EnumSet.of(DELIVERY, WORK, FEEDBACK).contains(newState))
        commit();
    }
    state = newState;
  }

  @Override
  protected void onStart() {
    super.onStart();
    orders = LaborExchange.board().active(jid().local());
    if (orders.length > 0)
      Stream.of(orders).forEach(o -> LaborExchange.tell(context(), o, self()));
  }

  @Override
  public boolean update(JID from, Role role, Affiliation affiliation, ProcessMode mode) {
    if ((role == null || role(from) == role) && (affiliation == null || affiliation(from) == affiliation))
      return true;
    if (!super.update(from, role, affiliation, mode))
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

  @Override
  protected boolean relevant(Stanza msg, JID to) {
    if (affiliation(to) == Affiliation.OWNER) {
      if (msg instanceof Message) {
        final Message message = (Message) msg;
        return message.type() == Message.MessageType.GROUP_CHAT ||
            message.has(Start.class) ||
            message.has(Answer.class) ||
            message.has(Progress.class) ||
            super.relevant(msg, to);
      }
    }
    return super.relevant(msg, to);
  }

  public void process(Message msg, ProcessMode mode) {
    super.process(msg, mode);
    final JID from = msg.from();
    Affiliation affiliation = affiliation(from);
    final boolean trusted = isTrusted(from);
    if (trusted && affiliation == Affiliation.NONE) {
      if (mode != ProcessMode.RECOVER)
        update(from, null, Affiliation.ADMIN, mode);
      affiliation = Affiliation.ADMIN;
    }
    if (msg.has(Offer.class) && EnumSet.of(Affiliation.OWNER, Affiliation.ADMIN).contains(affiliation)) { // offers handling
      final Offer offer = msg.get(Offer.class);
      final JID owner = owner();
      if (offer.client() == null)
        offer.client(owner);
      if (state == WORK) { // order update during the work
        if (mode != ProcessMode.RECOVER) {
          final List<JID> activeExperts = Arrays.stream(orders).flatMap(o -> o.of(ACTIVE)).collect(Collectors.toList());
          Arrays.stream(orders).map(ExpLeagueOrder::broker).filter(Objects::nonNull).forEach(b -> b.tell(new Cancel(), self()));
          if (activeExperts != null)
            offer.filter().prefer(activeExperts.toArray(new JID[activeExperts.size()]));
          orders = LaborExchange.board().register(offer);
          if (mode == ProcessMode.NORMAL)
            Stream.of(orders).forEach(o -> LaborExchange.tell(context(), o, self()));
        }
      }
      else if (affiliation == Affiliation.OWNER) {
        state(OPEN, mode);
      }
      else if (affiliation == Affiliation.ADMIN) {
        final XMPPDevice[] devices = Roster.instance().devices(owner.local());
        if (Stream.of(devices).anyMatch(device -> device.build() > 60)) {
          state(OFFER, mode);
        }
        else {
          state(WORK, mode);
          if (mode != ProcessMode.RECOVER)
            orders = LaborExchange.board().register(offer);
          if (mode == ProcessMode.NORMAL)
            Stream.of(orders).forEach(o -> LaborExchange.tell(context(), o, self()));
        }
      }
      if (mode == ProcessMode.NORMAL)
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
            final ExpLeagueOrder order = order(progress.order());
            if (order != null) {
              if (metaChange.operation() == Progress.MetaChange.Operation.ADD)
                order.tag(metaChange.name());
              else
                order.untag(metaChange.name());
            }
            break;
        }
        if (role(from) == Role.MODERATOR && mode == ProcessMode.NORMAL)
          update(from, Role.MODERATOR, Affiliation.ADMIN, mode);
      }
    }
    else if (msg.has(Start.class)) {
      update(from, Role.PARTICIPANT, Affiliation.MEMBER, mode);
      if (mode == ProcessMode.NORMAL) {
        invoke(new Message(jid(), roomAlias(owner()), Roster.instance().profile(from.local())));
      }
    }
    else if (msg.has(Answer.class)) {
      state(DELIVERY, mode);
      answer = msg;
    }
    else if (msg.has(Cancel.class) && affiliation == Affiliation.OWNER) {
      state(CLOSED, mode);
    }
    else if (affiliation == Affiliation.OWNER) {
      if (msg.has(Feedback.class) && state == FEEDBACK) {
        if (mode == ProcessMode.NORMAL)
          GlobalChatAgent.tell(jid(), msg.get(Feedback.class), context());
        state(CLOSED, mode);
      }
      else if (state == FEEDBACK && !msg.has(Command.class)) {
        state(OPEN, mode);
      }
      else if (mode == ProcessMode.NORMAL) {
        GlobalChatAgent.tell(jid(), new RoomMessageReceived(from), context());
      }
    }
  }

  @ActorMethod
  public void recover(DeleteMessagesSuccess success) {
    super.recover(success);
    LaborExchange.board().removeAllOrders(jid().local());
  }


  private ExpLeagueOrder order(String id) {
    return orders.length > 0 ? orders[0] : null;
  }

  @Override
  protected void delivered(String id, ProcessMode mode) {
    super.delivered(id, mode);
    final Stanza answer = answer();
    if (state == DELIVERY && answer != null) {
      this.answer = null;
      state(FEEDBACK, ProcessMode.NORMAL);
    }
    if (mode == ProcessMode.NORMAL)
      persist(new DeliveryQuery(id, null), delivered -> {});
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
}

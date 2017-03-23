package com.expleague.server.agents;

import akka.persistence.DeleteMessagesSuccess;
import com.expleague.model.*;
import com.expleague.model.Operations.*;
import com.expleague.model.RoomState;
import com.expleague.server.Roster;
import com.expleague.server.XMPPDevice;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.control.DeliveryReceit;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Stanza;

import java.util.*;
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
  private List<ExpLeagueOrder> orders = new ArrayList<>();
  private RoomState state;

  public ExpLeagueRoomAgent(JID jid) {
    super(jid, true);
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
    if (state == WORK) {
      final List<ExpLeagueOrder> active = Arrays.asList(LaborExchange.board().active(jid().local()));
      if (active.isEmpty())
        orders.addAll(Arrays.asList(LaborExchange.board().register(offer())));
      else
        orders.addAll(active);
      orders.forEach(o -> LaborExchange.tell(context(), o, self()));
    }
  }

  @Override
  public boolean update(JID from, Role role, Affiliation affiliation, ProcessMode mode) {
    if (from.isRoom())
      return true;
    if ((role == null || role(from) == role) && (affiliation == null || affiliation(from) == affiliation))
      return true;
    if (!super.update(from, role, affiliation, mode))
      return false;
    if (mode != ProcessMode.RECOVER)
      GlobalChatAgent.tell(jid(), new RoomRoleUpdate(from.bare(), role(from), affiliation(from)), context());
    return true;
  }

  @Override
  public Role suggestRole(JID from, Affiliation affiliation) {
    final XMPPDevice device = XMPPDevice.fromJid(from);
    if (authority(from) == ExpertsProfile.Authority.ADMIN)
      return Role.MODERATOR;
    else if (device != null && device.expert())
        return Role.PARTICIPANT;
    return super.suggestRole(from, affiliation);
  }

  private ExpertsProfile.Authority authority(JID from) {
    final ExpertsProfile profile = Roster.instance().profile(from.local());
    return profile != null ? profile.authority() : ExpertsProfile.Authority.NONE;
  }

  @Override
  protected boolean relevant(Stanza msg, JID to) {
    if (affiliation(to) == Affiliation.OWNER) { // client
      if (msg instanceof Message) {
        final Message message = (Message) msg;
        return (message.type() == Message.MessageType.GROUP_CHAT && !message.body().isEmpty()) ||
            message.has(Start.class) ||
            message.has(ExpertsProfile.class) ||
            message.has(Answer.class) && message.has(Verified.class) ||
            message.has(Progress.class) && message.get(Progress.class).meta() != null;
      }
    }
    else if (EnumSet.of(Role.MODERATOR, Role.PARTICIPANT).contains(role(to))) { // expert or admin
      if (msg instanceof Message) {
        final Message message = (Message) msg;
        return message.type() == Message.MessageType.GROUP_CHAT ||
            message.has(Progress.class) ||
            message.has(Offer.class) ||
            message.has(Answer.class) ||
            super.relevant(msg, to);
      }
    }
    return false;
  }

  @Override
  protected boolean filter(Message msg) {
    if (msg.has(Start.class)) {
      update(msg.from(), Role.PARTICIPANT, Affiliation.MEMBER, ProcessMode.NORMAL);
    }
    return super.filter(msg);
  }

  public void process(Message msg, ProcessMode mode) {
    if (owner() == null)
      update(msg.from(), null, Affiliation.OWNER, mode);
    super.process(msg, mode);
    final JID from = msg.from();
    Affiliation affiliation = affiliation(from);
    final ExpertsProfile.Authority authority = authority(from);
    if (authority == ExpertsProfile.Authority.ADMIN && affiliation == Affiliation.NONE) {
      if (mode != ProcessMode.RECOVER)
        update(from, null, Affiliation.ADMIN, mode);
      affiliation = Affiliation.ADMIN;
    }
    if (msg.has(Offer.class)) { // offers handling
      final Offer offer = msg.get(Offer.class);
      final JID owner = owner();
      if (offer.client() == null)
        offer.client(owner);
      if (state == WORK) { // order update during the work
        if (mode != ProcessMode.RECOVER && affiliation == Affiliation.ADMIN) {
          final List<JID> activeExperts = orders.stream().flatMap(o -> o.of(ACTIVE)).collect(Collectors.toList());
          cancelOrders();
          if (activeExperts != null)
            offer.filter().prefer(activeExperts.toArray(new JID[activeExperts.size()]));
          orders.addAll(Arrays.asList(LaborExchange.board().register(offer)));
          if (mode == ProcessMode.NORMAL)
            orders.forEach(o -> LaborExchange.tell(context(), o, self()));
        }
      }
      else if (affiliation == Affiliation.OWNER) {
        if (mode != ProcessMode.RECOVER)
          GlobalChatAgent.tell(jid(), new RoomMessageReceived(from, false), context());
        state(OPEN, mode);
      }
      else if (affiliation == Affiliation.ADMIN) {
        final XMPPDevice[] devices = Roster.instance().devices(owner.local());
        if (Stream.of(devices).anyMatch(device -> device.build() > 60)) {
          state(OFFER, mode);
        }
        else {
          state(WORK, mode);
          if (mode != ProcessMode.RECOVER) {
            orders.addAll(Arrays.asList(LaborExchange.board().register(offer)));
          }
          if (mode == ProcessMode.NORMAL)
            orders.forEach(o -> LaborExchange.tell(context(), o, self()));
        }
        if (mode != ProcessMode.RECOVER)
          GlobalChatAgent.tell(jid(), new RoomMessageReceived(from, true), context());
      }
      if (mode == ProcessMode.NORMAL) {
        GlobalChatAgent.tell(jid(), new Message(jid(), XMPP.jid(GlobalChatAgent.ID), Message.MessageType.GROUP_CHAT, offer, new OfferChange(from.bare())), context());
        GlobalChatAgent.tell(jid(), new RoomMessageReceived(from, true), context());
      }
    }
    else if (msg.has(Progress.class) && mode != ProcessMode.RECOVER) {
      final Progress progress = msg.get(Progress.class);
      final Progress.MetaChange metaChange = progress.meta();
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
      else if (progress.state() != null && mode == ProcessMode.NORMAL) {
        GlobalChatAgent.tell(jid(), progress, context());
      }
    }
    else if (msg.has(Start.class)) {
      msg.has(Start.class, s -> s.order() != null);

      if (mode == ProcessMode.NORMAL) {
        final ExpertsProfile profile = Roster.instance().profile(from.local());
        invoke(new Message(jid(), roomAlias(owner()), profile));
        GlobalChatAgent.tell(jid(), new Message(jid(), roomAlias(owner()), msg.get(Start.class), profile.shorten()), context());
        final Offer offer = offer();
        assert offer != null;
        offer.filter().prefer(from);
        self().tell(new Message(jid(), jid(), offer), self());
      }
    }
    else if (msg.has(Answer.class)) {
      if (authority.priority() <= ExpertsProfile.Authority.EXPERT.priority()) {
        state(DELIVERY, mode);
        if (mode == ProcessMode.NORMAL) {
          invoke(new Message(from, roomAlias(owner()), msg.get(Answer.class), new Verified(from)));
        }
        if (mode != ProcessMode.RECOVER) {
          cancelOrders();
        }
      }
      else state(VERIFY, mode);
      answer = msg;
      if (mode == ProcessMode.NORMAL)
        GlobalChatAgent.tell(jid(), new RoomMessageReceived(from, true), context());
    }
    else if (msg.has(Verified.class)) {
      if (state == VERIFY && authority.priority() <= ExpertsProfile.Authority.EXPERT.priority()) {
        state(DELIVERY, mode);
        if (mode == ProcessMode.NORMAL)
          invoke(new Message(from, roomAlias(owner()), answer.get(Answer.class), new Verified(from)));
      }
    }
    else if (msg.has(Cancel.class)) {
      if (affiliation == Affiliation.OWNER) {
        if (mode == ProcessMode.NORMAL) {
          orders.stream().map(ExpLeagueOrder::broker).filter(Objects::nonNull).forEach(b -> b.tell(new Cancel(), self()));
          orders.clear();
        }

        state(CLOSED, mode);
      }
      else if (mode == ProcessMode.NORMAL){
        final Offer offer = offer();
        assert offer != null;
        offer.filter().reject(from);
        self().tell(new Message(from, jid(), offer), self());
      }
    }
    else if (msg.has(Feedback.class) && (state == FEEDBACK || state == DELIVERY)) {
      if (mode == ProcessMode.NORMAL) {
        final Feedback feedback = msg.get(Feedback.class);
        if (!orders.isEmpty())
          orders.get(0).feedback(feedback.stars(), feedback.payment());
        GlobalChatAgent.tell(jid(), feedback, context());
      }
      state(CLOSED, mode);
    }
    else if (msg.has(Done.class)) {
      orders.removeIf(next -> next.state() == OrderState.DONE);
    }
    else {
      if (EnumSet.of(CLOSED, FEEDBACK, DELIVERY).contains(state) && !msg.has(Command.class) && affiliation == Affiliation.OWNER) {
        state(OPEN, mode);
      }
      else if (EnumSet.of(OPEN, CHAT).contains(state)) {
        if (affiliation == Affiliation.ADMIN)
          state(CHAT, mode);
        if (mode == ProcessMode.NORMAL) {
          GlobalChatAgent.tell(jid(), new RoomMessageReceived(from, affiliation != Affiliation.OWNER), context());
        }
      }
    }
  }

  private void cancelOrders() {
    orders.stream().filter(o -> o.state() != OrderState.DONE).map(ExpLeagueOrder::broker).filter(Objects::nonNull).forEach(b -> b.tell(new Cancel(), self()));
    orders.clear();
  }

  @Override
  protected <S extends Stanza> S participantCopy(S stanza, JID to) {
    if (stanza instanceof Message) {
      final Message message = (Message) stanza;
      if (affiliation(to) == Affiliation.OWNER && message.has(Progress.class)) {
        return stanza.<S>copy(to.local())
            .to(to)
            .from(jid());
      }
    }
    return super.participantCopy(stanza, to);
  }

  @ActorMethod
  public void recover(DeleteMessagesSuccess success) {
    super.recover(success);
    LaborExchange.board().removeAllOrders(jid().local());
  }


  private ExpLeagueOrder order(String id) {
    return orders.size() > 0 ? orders.get(0) : null;
  }

  private Offer offer() {
    final List<Stanza> archive = archive();
    for (int i = archive.size() - 1; i >= 0; i--) {
      final Stanza stanza =  archive.get(i);
      if (stanza instanceof Message) {
        final Message message = (Message) stanza;
        if (message.has(Offer.class))
          return message.get(Offer.class);
      }
    }
    return null;
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
      persist(new DeliveryReceit(id, null), delivered -> {});
  }

  Message answer = null;
  private Stanza answer() {
    if (answer != null)
      return answer;
    final List<Stanza> archive = archive();
    for (int i = archive.size() - 1; i >= 0; i--) {
      final Stanza stanza = archive.get(i);
      if (stanza instanceof Message && ((Message)stanza).has(Answer.class))
        return answer = (Message)stanza;
    }
    return null;
  }
}

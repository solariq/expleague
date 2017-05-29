package com.expleague.server.agents;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.ReceiveTimeout;
import com.expleague.model.*;
import com.expleague.model.Operations.*;
import com.expleague.model.RoomState;
import com.expleague.server.Roster;
import com.expleague.server.XMPPDevice;
import com.expleague.server.answers.RepositoryService;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.control.receipts.Received;
import com.expleague.xmpp.muc.MucHistory;
import com.expleague.xmpp.muc.MucXData;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Presence;
import com.expleague.xmpp.stanza.Stanza;
import scala.concurrent.duration.Duration;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.expleague.model.RoomState.*;
import static com.expleague.server.agents.ExpLeagueOrder.Role.*;

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
  private Offer currentOffer = null;
  private HashSet<JID> knownToClient = new HashSet<>();
  private Map<String, Message> answers = new HashMap<>();

  private boolean oldFormat = false;
  private long currentTime = 0;

  public ExpLeagueRoomAgent(JID jid) {
    super(jid, true);
    orders = LaborExchange.board().history(jid().local()).collect(Collectors.toList());
  }

  private void state(RoomState newState) {
    tellGlobal(new RoomStateChanged(newState));
    commit();
    if (mode() == ProcessMode.NORMAL)
      log.fine("Room " + jid().local() + " state change: " + state + " -> " + newState);
    state = newState;
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (state == WORK) {
      inflightOrders()
          .forEach(order -> LaborExchange.tell(context(), order, self()));
    }
    context().setReceiveTimeout(Duration.apply(1, TimeUnit.HOURS));
  }

  @ActorMethod
  public void inactivityShutDown(ReceiveTimeout timeout) {
    self().tell(PoisonPill.getInstance(), self());
  }

  @Override
  protected boolean checkAffiliation(JID from, Affiliation affiliation) {
    if (affiliation == null)
      return true;
    final XMPPDevice device = XMPPDevice.fromJid(from);
    if (device == null)
      return false;
    if (from.isRoom() && affiliation.priority() >= Affiliation.MEMBER.priority()) return true;
    //noinspection SimplifiableIfStatement
    if (!device.expert() && affiliation.priority() >= Affiliation.OWNER.priority()) return true;
    return super.checkAffiliation(from, affiliation);
  }

  @Override
  public boolean update(JID from, Role role, Affiliation affiliation, ProcessMode mode) throws MembershipChangeRefusedException {
    if (from.isRoom() || !super.update(from, role, affiliation, mode))
      return false;
    tellGlobal(new RoomRoleUpdate(from.bare(), role(from), affiliation(from)));
    if (affiliation == Affiliation.OWNER)
      tellGlobal(new Presence(from, role != Role.NONE && role != null));
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
    if (from.isRoom() || from.local().isEmpty())
      return ExpertsProfile.Authority.NONE;
    final ExpertsProfile profile = Roster.instance().profile(from.local());
    return profile != null ? profile.authority() : ExpertsProfile.Authority.NONE;
  }

  @Override
  protected boolean relevant(Stanza msg, JID to) {
    if (affiliation(to) == Affiliation.OWNER) { // client
      if (msg instanceof Message) {
        final Message message = (Message) msg;
        return (message.type() == Message.MessageType.GROUP_CHAT && !message.body().trim().isEmpty()) ||
            message.has(Progress.class) && message.get(Progress.class).meta().findAny().isPresent();
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

  protected boolean filter(Presence pres) {
    currentTime = System.currentTimeMillis();
    return super.filter(pres);
  }

  @Override
  public boolean update(Presence presence) {
    currentTime = System.currentTimeMillis();
    return super.update(presence);
  }

  @Override
  protected boolean filter(Message msg) {
    if (mode() == ProcessMode.REPLAY)
      currentTime = msg.hasTs() ? msg.ts() : currentTime + 1;
    else
      currentTime = msg.ts();

    if (owner() == null)
      affiliation(msg.from(), Affiliation.OWNER);
    if (msg.has(Start.class))
      affiliation(msg.from(), Affiliation.MEMBER);
    else if (msg.has(Cancel.class) && affiliation(msg.from()) != Affiliation.OWNER)
      affiliation(msg.from(), Affiliation.NONE);
    else if (msg.has(Offer.class) && !msg.hasTs() && currentTime < 1000L * 1000L * 1000L) //9 September 2001
      currentTime = (long) msg.get(Offer.class).started() * 1000;
    else
      return super.filter(msg);
    return true;
  }

  public void process(Message msg) {
    super.process(msg);

    final JID from = msg.from();
    final Affiliation affiliation = affiliation(from);
    final boolean fromOwner = affiliation == Affiliation.OWNER;
    final ExpertsProfile.Authority authority = authority(from);

    if (msg.has(Progress.class)) {
      final Progress progress = msg.get(Progress.class);
      progress.meta().forEach(metaChange -> inflightOrders().filter(order -> order.id().equals(progress.order()) || progress.order() == null).forEach(order -> {
        switch (metaChange.target()) {
          case PATTERNS:
            break;
          case TAGS:
            if (metaChange.operation() == Progress.MetaChange.Operation.ADD)
              order.tag(metaChange.name());
            else
              order.untag(metaChange.name());
        }
      }));
      if (progress.state() != null) {
        tellGlobal(progress);
      }
    }
    else if (msg.has(Start.class)) {
      update(from, Role.PARTICIPANT, null);
      if (currentOffer == null) {
        log.warning("Start while offer is empty!");
        return;
      }
      if (!inflightOrders().findAny().isPresent()) { // backward compatibility
        oldFormat = true;
        state(WORK);
        startOrders(currentOffer);
      }

      final Start start = msg.get(Start.class);
      if (start.order() != null && !start.order().startsWith("room")) /* old format*/
        if (orders.size() > 0)
          start.order(orders.get(orders.size() - 1).id());
        else
          start.order(null);

      orders(OrderState.NONE, OrderState.OPEN).filter(o -> o.broker() == null && (o.id().equals(start.order()) || start.order() == null)).forEach(o -> {
        o.state(OrderState.IN_PROGRESS, currentTime);
        o.role(from, ACTIVE, currentTime);
      });
      enter(from, new MucXData(new MucHistory()));
      final ExpertsProfile profile = Roster.instance().profile(from.local());
      if (!knownToClient.contains(from.bare())) {
        knownToClient.add(from.bare());
        message(new Message(jid(), roomAlias(owner()), start, profile));
      }
      final Offer newOffer = currentOffer.copy();
      newOffer.filter().prefer(from);
      if (!newOffer.equals(currentOffer)) {
        currentOffer = newOffer;
        tellGlobal(currentOffer, new OfferChange(from.bare()));
      }
      tellGlobal(start, profile.shorten());
    }
    else if (msg.has(Answer.class)) {
      final Answer answer = msg.get(Answer.class);
      if (authority.priority() <= ExpertsProfile.Authority.EXPERT.priority() || oldFormat) {
        if (authority == ExpertsProfile.Authority.ADMIN || oldFormat)
          state(FEEDBACK);
        else
          state(DELIVERY);

        final Message answerCopy = msg.copy(owner().local());
        answerCopy.to(roomAlias(owner()));
        answerCopy.append(new Verified(answer.order(), from));
        message(answerCopy);
      }
      else state(VERIFY);
      message(new Message(jid(), RepositoryService.jid(), currentOffer, answer));
      answer(msg);
      tellGlobal(new RoomMessageReceived(from, true));
      inflightOrders().forEach(order->{
        if (order.state() != OrderState.DONE && (order.id().equals(answer.order()) || answer.order() == null)) {
          order.state(OrderState.DONE, currentTime);
        }
      });
    }
    else if (msg.has(Verified.class)) {
      if (state == VERIFY && authority.priority() <= ExpertsProfile.Authority.EXPERT.priority()) {
        if (oldFormat)
          state(FEEDBACK);
        else
          state(DELIVERY);

        final Verified verified = msg.get(Verified.class);
        final Message answer = this.answer(verified.order());
        if (answer != null) {
          final Message answerCopy = answer.copy(owner().local());
          answerCopy.to(roomAlias(owner()));
          answerCopy.append(verified);
          message(answerCopy);
          message(new Message(jid(), RepositoryService.jid(), currentOffer, verified));
        }
      }
    }
    else if (msg.has(Cancel.class)) {
        if (fromOwner) {
          cancelOrders(currentTime);
          state(CLOSED);
        }
        else if (currentOffer != null) {
          final Cancel cancel = msg.get(Cancel.class);
          if (knownToClient.contains(from))
            message(new Message(from, roomAlias(owner()), cancel));
          final Offer oldOffer = currentOffer;
          currentOffer = currentOffer.copy();
          currentOffer.filter().reject(from);
          if (!currentOffer.equals(oldOffer)) {
            inflightOrders().filter(order -> order.offer().equals(oldOffer)).forEach(order -> {
              order.offer(currentOffer);
              if (order.broker() == null && (cancel.order() == null || cancel.order().equals(order.id())))
                order.role(from, order.state == OrderState.IN_PROGRESS ? SLACKER : DENIER, currentTime);
            });
            tellGlobal(currentOffer, new OfferChange(from.bare()));
          }
        }
    }
    else if (msg.has(Feedback.class)) {
      final Feedback feedback = msg.get(Feedback.class);
      orders(OrderState.DONE).forEach(order -> order.feedback(feedback.stars(), feedback.payment()));
      tellGlobal(feedback);
      message(new Message(jid(), RepositoryService.jid(), currentOffer, feedback));
      state(CLOSED);
    }
    else if (msg.has(Resume.class)) {
      final Resume resume = msg.get(Resume.class);
      update(from, Role.PARTICIPANT, null);
      enter(from, new MucXData(new MucHistory()));
      orders(OrderState.SUSPENDED).filter(o -> o.id().equals(resume.order()) || resume.order() == null).forEach(o -> o.state(OrderState.IN_PROGRESS, currentTime));
    }
    else if (msg.has(Done.class)) {
      final Done done = msg.get(Done.class);
      update(from, Role.NONE, null);
      inflightOrders().forEach(order->{
        if (order.state() != OrderState.DONE && (order.id().equals(done.order()) || done.order() == null)) {
          order.state(OrderState.DONE, currentTime);
        }
      });
    }
    else if (msg.has(Suspend.class)) {
      final Suspend suspend = msg.get(Suspend.class);
      update(from, Role.NONE, null);
      orders(OrderState.IN_PROGRESS).forEach(order->{
        if (order.state() != OrderState.SUSPENDED && (order.id().equals(suspend.order()) || suspend.order() == null)) {
          order.state(OrderState.SUSPENDED, currentTime);
        }
      });
    }
    else if (msg.has(Offer.class)) { // offers handling
      currentOffer = msg.get(Offer.class);
      JID owner = owner();
      if (currentOffer.client() == null)
        currentOffer.client(owner);
      else if (!owner.bareEq(currentOffer.client())) {
        affiliation(owner, Affiliation.ADMIN);
        affiliation(currentOffer.client(), Affiliation.OWNER);
      }

      if (fromOwner) {
        state(OPEN);
      }
      else if (authority == ExpertsProfile.Authority.ADMIN) {
        affiliation(from, Affiliation.ADMIN);
        if (state != WORK) {
          final XMPPDevice[] devices = Roster.instance().devices(owner.local());
          if (Stream.of(devices).anyMatch(device -> device.build() > 70)) {
            state(OFFER);
          }
          else {
            state(WORK);
            startOrders(currentOffer).forEach(order -> {
              final Offer orderOffer = order.offer();
              for (final Tag tag : orderOffer.tags()) {
                message(new Message(from, jid(), new Progress(order.id(), new Progress.MetaChange(tag.name(), Progress.MetaChange.Operation.ADD, Progress.MetaChange.Target.TAGS))));
              }
              for (final Pattern pattern : orderOffer.patterns()) {
                message(new Message(from, jid(), new Progress(order.id(), new Progress.MetaChange(pattern.name(), Progress.MetaChange.Operation.ADD, Progress.MetaChange.Target.PATTERNS))));
              }
            });
          }
          tellGlobal(new RoomMessageReceived(from, true));
        }
        else { // order update during the work
          final List<JID> activeExperts = inflightOrders().flatMap(o -> o.of(ACTIVE)).collect(Collectors.toList());
          cancelOrders(currentTime);
          if (activeExperts != null) {
            currentOffer = currentOffer.copy();
            currentOffer.filter().prefer(activeExperts.toArray(new JID[activeExperts.size()]));
          }
          startOrders(currentOffer);
        }
      }
      tellGlobal(currentOffer, new OfferChange(from.bare()));
      tellGlobal(new RoomMessageReceived(from, true));
    }
    else {
      if (EnumSet.of(CLOSED, FEEDBACK, DELIVERY).contains(state) && !msg.has(Command.class) && fromOwner) {
        state(OPEN);
      }
      else if (EnumSet.of(OPEN, CHAT).contains(state)) {
        if (!fromOwner) {
          affiliation(from, Affiliation.ADMIN);
          state(CHAT);
        }
        tellGlobal(new RoomMessageReceived(from, !fromOwner));
      }
    }
  }

  private void answer(Message answerMsg) {
    final Answer answer = answerMsg.get(Answer.class);
    if (answer.order() != null)
      answers.put(answer.order(), answerMsg);
    answers.put("last", answerMsg);
  }

  private Message answer(String order) {
    return order != null ? answers.get(order) : answers.get("last");
  }

  private void message(Message message) {
    if (message.to().bareEq(jid())) {
      if (mode() == ProcessMode.NORMAL)
        onMessage(message);
    }
    else if (mode() != ProcessMode.RECOVER) {
      XMPP.send(message, context());
    }
  }

  private Stream<ExpLeagueOrder> inflightOrders() {
    return orders(OrderState.NONE, OrderState.IN_PROGRESS, OrderState.OPEN, OrderState.SUSPENDED);
  }

  private Stream<ExpLeagueOrder> orders(OrderState state0, OrderState... states) {
    if (mode() != ProcessMode.RECOVER)
      return orders.stream().filter(order -> EnumSet.of(state0, states).contains(order.state()));
    else
      return Stream.empty();
  }

  private void tellGlobal(Item... progress) {
    if (mode() != ProcessMode.RECOVER) {
      final Message message = new Message(jid(), XMPP.jid(GlobalChatAgent.ID), Message.MessageType.GROUP_CHAT, progress);
      message.append(new Message.Timestamp(currentTime));
      GlobalChatAgent.tell(message, context());
    }
  }

  private void tellGlobal(Stanza stanza) {
    if (mode() == ProcessMode.NORMAL)
      GlobalChatAgent.tell(stanza, context());
  }

  private Stream<ExpLeagueOrder> startOrders(Offer offer) {
    knownToClient.clear();
    if (offer.room() == null) // backward compatibility
      offer.room(jid());
    if (mode() == ProcessMode.RECOVER)
      return Stream.empty();

    final List<ExpLeagueOrder> registered = Arrays.asList(LaborExchange.board().register(offer, orders.size()));
    orders.addAll(registered);
    if (mode() == ProcessMode.NORMAL)
      registered.forEach(o -> LaborExchange.tell(context(), o, self()));
    return registered.stream();
  }

  private void cancelOrders(long ts) {
    inflightOrders().filter(o -> o.state() != OrderState.DONE).forEach(order -> {
      order.state(OrderState.DONE, ts);
      message(new Message(jid(), jid(), new Operations.Progress(order.id(), OrderState.DONE)));
      final ActorRef broker = order.broker();
      if (broker != null)
        broker.tell(new Cancel(), self());
      else if (mode() == ProcessMode.NORMAL)
        log.warning("Empty broker found for order " + order.id());
    });
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

  protected void replay() {
    if (mode() == ProcessMode.REPLAY) {
      log.fine("Replay has been already started");
      return;
    }

    currentTime = 0;
    cancelOrders(System.currentTimeMillis());
    LaborExchange.board().removeAllOrders(jid().local());
    GlobalChatAgent.tell(new Message(jid(), XMPP.jid(GlobalChatAgent.ID), Message.MessageType.GROUP_CHAT, new Clear()), context());

    orders.clear();
    super.replay();
  }

  @Override
  @ActorMethod
  public void onDelivered(Delivered delivered) {
    super.onDelivered(delivered);
    if (state == DELIVERY) {
      final boolean answerDelivered = answers.values().stream().anyMatch(a -> delivered.id().startsWith(a.id()) && delivered.user().bareEq(owner()));
      if (answerDelivered) {
        state(FEEDBACK);
        if (mode() == ProcessMode.NORMAL) {
          final Message message = new Message(delivered.user().resource(delivered.resource()), jid(), new Received(delivered.id()));
          persist(delivered, d -> archive(message));
        }
      }
    }
  }
}

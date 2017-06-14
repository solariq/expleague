package com.expleague.server.agents;

import akka.actor.*;
import akka.util.Timeout;
import com.expleague.model.*;
import com.expleague.util.akka.*;
import com.spbsu.commons.util.Pair;
import com.expleague.server.ExpLeagueServer;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Presence;
import org.jetbrains.annotations.Nullable;
import scala.Option;
import scala.collection.JavaConversions;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * User: solar
 * Date: 17.12.15
 * Time: 15:18
 */
public class LaborExchange extends ActorAdapter<UntypedActor> {
  private static final Logger log = Logger.getLogger(LaborExchange.class.getName());

  public static final String EXPERTS_ACTOR_NAME = "experts";
  private Map<String, ActorRef> activeOrders = new HashMap<>();

  @Override
  public void preStart() throws Exception {
    super.preStart();
    AkkaTools.scheduleTimeout(context(), ExpLeagueServer.config().timeout("labor-exchange.state-timeout"), self());
    status = new ServiceStatus();
    context().actorOf(props(Experts.class), EXPERTS_ACTOR_NAME);
  }

  @ActorMethod
  public void invoke(ExpLeagueOrder order) {
    final String roomName = order.room().local();
    log.fine("Labor exchange received order " + roomName);
    if (activeOrders.containsKey(order.id())) {
      log.fine("Broker " + activeOrders.get(order.id()) + " is already working on it.");
    }
    else if (order.state() != OrderState.DONE) {
      log.fine("Creating new broker for task: " + activeOrders.get(order.id()) + ".");
      final ActorRef broker = context().actorOf(Props.create(BrokerRole.class, self()));
      activeOrders.put(order.id(), broker);
      final Object answer = AkkaTools.ask(broker, order);
      if (!(answer instanceof Operations.Ok))
        log.warning("Unable to create alive broker! Received: " + answer);
    }
  }

  @ActorMethod
  public void invoke(ActorRef expertAgent) {
    JavaConversions.asJavaCollection(context().children()).stream()
      .filter(LaborExchange::isBrokerActorRef)
      .forEach(ref -> ref.forward(expertAgent, context()));
  }

  @ActorMethod
  public void onTerminated(Terminated terminated) {
    log.fine("Broker " + terminated.actor() + " has died.");
    activeOrders.replaceAll((orderId, broker) -> broker == terminated.actor() ? null : broker);
  }

  @ActorMethod
  public void invoke(Operations.StatusChange notification) {
    if (!"experts".equals(sender().path().parent().name())) { // broker
      final OrderState orderState = OrderState.valueOf(notification.taskState());
      final BrokerRole.State from = BrokerRole.State.valueOf(notification.from());
      final BrokerRole.State to = BrokerRole.State.valueOf(notification.to());
      if (EnumSet.of(OrderState.IN_PROGRESS, OrderState.SUSPENDED).contains(orderState)) {
        if (from == BrokerRole.State.STARVING)
          status.brokerFed();
        else if (to == BrokerRole.State.STARVING)
          status.brokerStarving();
      }
    }
    else { // expert
      final ExpertRole.State from = ExpertRole.State.valueOf(notification.from());
      final ExpertRole.State to = ExpertRole.State.valueOf(notification.to());
      if (to == ExpertRole.State.READY) {
        if (from == ExpertRole.State.OFFLINE)
          status.expertOnline();
        status.expertAvailable();
      }
      if (from == ExpertRole.State.READY) {
        status.expertBusy();
      }
      if (to == ExpertRole.State.OFFLINE) {
        status.expertOffline();
      }
    }
  }

  private static boolean isBrokerActorRef(final ActorRef ref) {
    return !EXPERTS_ACTOR_NAME.equals(ref.path().name());
  }

  public static <T> void tell(ActorContext context, T msg, ActorRef from) {
    reference(context).tell(msg, from);
  }

  public static ActorSelection reference(ActorContext context) {
    return context.actorSelection("/user/labor-exchange");
  }

  public static ActorSelection experts(ActorContext context) {
    return context.actorSelection("/user/labor-exchange/experts");
  }

  public static Board board() {
    return ExpLeagueServer.board();
  }

  private ServiceStatus knownStatus;
  private ServiceStatus status = new ServiceStatus();

  @ActorMethod
  public void sendState(Timeout to) {
    try {
      if (knownStatus != null && knownStatus.equals(status))
        return;
      knownStatus = status;
      XMPP.send(new Presence(XMPP.jid(), true, status), context());
      status = new ServiceStatus(knownStatus);
    }
    finally {
      AkkaTools.scheduleTimeout(context(), ExpLeagueServer.config().timeout("labor-exchange.state-timeout"), self());
    }
  }

  /**
   * User: solar
   * Date: 19.12.15
   * Time: 17:32
   */
  public static class Experts extends ActorAdapter<UntypedActor> {
    @ActorMethod
    public void invoke(JID jid) {
      sender().tell(findOrAllocate(jid), self());
    }

    @ActorMethod
    public void invoke(ActorRef actor) {
      log.fine("Experts department received request from broker");
      JavaConversions.asJavaCollection(context().children()).forEach(
          expert -> {
            log.finest("Forwarding offer to " + Experts.jid(expert));
            expert.forward(actor, context());
          }
      );
    }

    @ActorMethod
    public void whisper(Pair<Object, JID> whisper) {
      final ActorRef allocate = findOrAllocate(whisper.second);
      if (whisper.first != null) {
        allocate.forward(whisper.first, context());
      }
    }

    public static JID jid(ActorRef ref) {
      return XMPP.jid(ref);
    }

    private ActorRef findOrAllocate(JID jid) {
      final String id = jid.bare().toString();
      final Option<ActorRef> child = context().child(id);
      if (child.isDefined()) {
        return child.get();
      }
      return context().actorOf(Props.create(ExpertRole.class), id);
    }

    public static void tellTo(JID jid, Object o, ActorRef from, ActorContext context) {
      experts(context).tell(Pair.create(o, jid), from);
    }
  }

  public interface Board {
    ExpLeagueOrder[] active(String roomId);
    ExpLeagueOrder[] register(Offer offer, int startNo);
    void removeAllOrders(String roomId);

    Stream<ExpLeagueOrder> history(String roomId);
    Stream<ExpLeagueOrder> related(JID jid);
    Stream<ExpLeagueOrder> open();
    Stream<ExpLeagueOrder> orders(OrderFilter filter);

    void replay(String roomId, ActorContext context);

    Stream<JID> topExperts();

    Stream<Tag> tags();
    @Nullable
    AnswerOfTheWeek answerOfTheWeek();
  }

  public static class AnswerOfTheWeek {
    private final String roomId;
    private final String topic;

    public AnswerOfTheWeek(String roomId, String topic) {
      this.roomId = roomId;
      this.topic = topic;
    }

    @Override
    public boolean equals(Object o) {
      return this == o || !(o == null || getClass() != o.getClass()) && roomId.equals(((AnswerOfTheWeek) o).roomId);
    }

    @Override
    public int hashCode() {
      return roomId.hashCode();
    }

    public String roomId() {
      return roomId;
    }

    public String topic() {
      return topic;
    }
  }

  public static class OrderFilter {
    // todo: should be a full-blown rich feature filter
    private final boolean withoutFeedback;
    private final EnumSet<OrderState> statuses;

    public OrderFilter(final boolean withoutFeedback, final EnumSet<OrderState> statuses) {
      this.withoutFeedback = withoutFeedback;
      this.statuses = statuses;
    }

    public boolean withoutFeedback() {
      return withoutFeedback;
    }

    public EnumSet<OrderState> getStatuses() {
      return statuses;
    }
  }
}

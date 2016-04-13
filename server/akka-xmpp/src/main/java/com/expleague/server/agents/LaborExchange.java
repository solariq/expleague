package com.expleague.server.agents;

import akka.actor.*;
import akka.util.Timeout;
import com.expleague.model.*;
import com.spbsu.commons.util.Pair;
import com.expleague.server.ExpLeagueServer;
import com.expleague.util.akka.AkkaTools;
import com.expleague.util.akka.UntypedActorAdapter;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Presence;
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
public class LaborExchange extends UntypedActorAdapter {
  private static final Logger log = Logger.getLogger(LaborExchange.class.getName());

  public static final String EXPERTS_ACTOR_NAME = "experts";

  private final Map<String, ActorRef> openPositions = new HashMap<>();
  public LaborExchange() {
    XMPP.send(new Presence(XMPP.jid(), false, new ServiceStatus(0)), context());
  }

  @Override
  public void preStart() throws Exception {
    super.preStart();
    context().actorOf(Props.create(Experts.class), EXPERTS_ACTOR_NAME);
    board().open().forEach(o -> self().tell(o, self()));
  }

  public void invoke(ExpLeagueOrder order) {
    final String roomName = order.room().local();
    log.fine("Labor exchange received order " + roomName + " looking for broker");
    final Collection<ActorRef> children = JavaConversions.asJavaCollection(context().children());
    ActorRef responsible = null;
    for (final ActorRef ref : children) {
      if (isBrokerActorRef(ref)) {
        if (AkkaTools.ask(ref, order) instanceof Operations.Ok) {
          responsible = ref;
          break;
        }
      }
    }
    if (responsible == null) {
      responsible = context().actorOf(Props.create(BrokerRole.class, self()));
      final Object answer = AkkaTools.ask(responsible, order);
      if (!(answer instanceof Operations.Ok))
        throw new RuntimeException("Unable to create alive broker! Received: " + answer);
    }
  }

  public void invoke(ActorRef expertAgent) {
    JavaConversions.asJavaCollection(context().children()).stream()
      .filter(LaborExchange::isBrokerActorRef)
      .forEach(ref -> ref.forward(expertAgent, context()));
  }

  private static boolean isBrokerActorRef(final ActorRef ref) {
    return !EXPERTS_ACTOR_NAME.equals(ref.path().name());
  }

  public static ActorRef reference(ActorContext context) {
    return AkkaTools.getOrCreate("/user/labor-exchange", context.system(),
        (name, factory) -> factory.actorOf(Props.create(LaborExchange.class), "labor-exchange")
    );
  }

  public static <T> void tell(ActorContext context, T msg, ActorRef from) {
    reference(context).tell(msg, from);
  }

  public static ActorSelection experts(ActorContext context) {
    return context.actorSelection("/user/labor-exchange/experts");
  }

  public static Board board() {
    return ExpLeagueServer.board();
  }

  /**
   * User: solar
   * Date: 19.12.15
   * Time: 17:32
   */
  public static class Experts extends UntypedActorAdapter {
    public void invoke(JID jid) {
      sender().tell(AkkaTools.getOrCreate(jid.bare().toString(), context(), () -> Props.create(ExpertRole.class)), self());
    }

    public void invoke(Offer offer) {
      log.fine("Experts department received offer " + offer.room().local());
      JavaConversions.asJavaCollection(context().children()).stream().forEach(
          expert -> {
            log.finest("Forwarding offer to " + Experts.jid(expert));
            expert.forward(offer, context());
          }
      );
    }

    public int readyCount = 0;
    private Map<String, ExpertRole.State> states = new HashMap<>();
    private Cancellable stateTimeout = null;
    public void invoke(ExpertRole.State next) {
      final String key = sender().path().name();
      final ExpertRole.State current = states.get(key);
      readyCount += increment(next) - increment(current);
      states.put(key, next);
      sendState();
    }

    public void invoke(Pair<Object, JID> whisper) {
      final ActorRef ref;
      final String name = whisper.second.bare().toString();
      final Option<ActorRef> child = context().child(name);
      if (child.isEmpty()) {
        ref = context().actorOf(Props.create(ExpertRole.class), name);
      } else {
        ref = child.get();
      }
      if (whisper.first != null) {
        ref.forward(whisper.first, context());
      }
//      else {
        // todo: do we need to spawn child anyway?
//      }
    }

    @SuppressWarnings("UnusedParameters")
    public void invoke(Timeout to) {
      stateTimeout = null;
      sendState();
    }

    int sentCount = 0;
    public void sendState() {
      if (stateTimeout != null)
        return;
      if (sentCount != readyCount) {
        // todo: don't we need to check actual experts?
        sentCount = readyCount;
        XMPP.send(new Presence(XMPP.jid(), readyCount != 0, new ServiceStatus(readyCount)), context());
        stateTimeout = AkkaTools.scheduleTimeout(context(), ExpLeagueServer.config().timeout("labor-exchange.state-timeout"), self());
      }
    }

    private int increment(ExpertRole.State next) {
      if (next == null)
        return 0;
      switch (next) {
        case READY:
          return 1;
        case OFFLINE:
        case CHECK:
        case INVITE:
        case BUSY:
          return 0;
      }
      return 0;
    }

    public static JID jid(ActorRef ref) {
      return XMPP.jid(ref);
    }

    public static void tellTo(JID jid, Object o, ActorRef from, ActorContext context) {
      experts(context).tell(Pair.create(o, jid), from);
    }
  }

  public interface Board {
    ExpLeagueOrder active(String roomId);
    ExpLeagueOrder register(Offer offer);

    Stream<ExpLeagueOrder> history(String roomId);
    Stream<ExpLeagueOrder> related(JID jid);
    Stream<ExpLeagueOrder> open();
    Stream<ExpLeagueOrder> orders(OrderFilter filter);

    Stream<JID> topExperts();

    Stream<Tag> tags();
  }

  public static class OrderFilter {
    // todo: should be a full-blown rich feature filter
    private final boolean withoutFeedback;
    private final EnumSet<ExpLeagueOrder.Status> statuses;

    public OrderFilter(final boolean withoutFeedback, final EnumSet<ExpLeagueOrder.Status> statuses) {
      this.withoutFeedback = withoutFeedback;
      this.statuses = statuses;
    }

    public boolean withoutFeedback() {
      return withoutFeedback;
    }

    public EnumSet<ExpLeagueOrder.Status> getStatuses() {
      return statuses;
    }
  }
}

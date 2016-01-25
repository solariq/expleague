package com.tbts.server.agents;

import akka.actor.*;
import akka.persistence.SaveSnapshotFailure;
import akka.persistence.SaveSnapshotSuccess;
import akka.persistence.SnapshotOffer;
import akka.persistence.UntypedPersistentActor;
import akka.util.Timeout;
import com.spbsu.commons.system.RuntimeUtils;
import com.tbts.model.Offer;
import com.tbts.model.Operations;
import com.tbts.model.ServiceStatus;
import com.tbts.server.TBTSServer;
import com.tbts.server.agents.roles.BrokerRole;
import com.tbts.server.agents.roles.ExpertRole;
import com.tbts.util.akka.AkkaTools;
import com.tbts.util.akka.UntypedActorAdapter;
import com.tbts.xmpp.JID;
import com.tbts.xmpp.stanza.Presence;
import scala.collection.JavaConversions;
import scala.concurrent.duration.Duration;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * User: solar
 * Date: 17.12.15
 * Time: 15:18
 */
public class LaborExchange extends UntypedPersistentActor {
  private final Map<String, ActorRef> openPositions = new HashMap<>();
  public LaborExchange() {
    XMPP.send(new Presence(XMPP.jid(), false, new ServiceStatus(0)), context());
  }

  @Override
  public void preStart() throws Exception {
    super.preStart();
    AkkaTools.getOrCreate("experts", context(), () -> Props.create(Experts.class));
  }

  public void invoke(Offer offer) {
    final Collection<ActorRef> children = JavaConversions.asJavaCollection(context().children());
    for (final ActorRef ref : children) {
      if (!"experts".equals(ref.path().name())) {
        if (AkkaTools.ask(ref, offer) instanceof Operations.Ok) {
          openPositions.put(offer.room().local(), ref);
          saveSnapshot(new ArrayList<>(openPositions.keySet()));
          return;
        }
      }
    }
    final ActorRef ref = context().actorOf(Props.create(BrokerRole.class));
    final Object answer = AkkaTools.ask(ref, offer);
    if (!(answer instanceof Operations.Ok))
      throw new RuntimeException("Unable to create alive broker! Received: " + answer);
    openPositions.put(offer.room().local(), ref);
    saveSnapshot(new ArrayList<>(openPositions.keySet()));
  }

  public void invoke(ActorRef expertAgent) {
    JavaConversions.asJavaCollection(context().children()).forEach(
        ref -> {
          if(!"experts".equals(ref.path().name()))
            ref.forward(expertAgent, context());
        }
    );
  }

  public void invoke(Operations.Done done) {
    final JID jid = Experts.jid(sender());
    openPositions.remove(jid.local());
    saveSnapshot(new ArrayList<>(openPositions.keySet()));
  }

  public void invoke(Operations.Cancel cancel) {
    final JID jid = Experts.jid(sender());
    openPositions.remove(jid.local()).forward(cancel, context());
    saveSnapshot(new ArrayList<>(openPositions.keySet()));
  }

  @Override
  public void onReceiveRecover(Object o) throws Exception {
    if (o instanceof SnapshotOffer) {
      final SnapshotOffer offer = (SnapshotOffer) o;
      //noinspection unchecked
      ((List<String>) offer.snapshot()).stream().forEach(local -> {
        final JID room = new JID(local, "muc." + TBTSServer.config().domain(), null);
        final ActorRef roomAgent = XMPP.register(room, context());
        roomAgent.tell(new Operations.Resume(), self());
      });
    }
  }

  @Override
  public void onReceiveCommand(Object o) throws Exception {
    dispatcher.invoke(this, o);
  }

  @Override
  public String persistenceId() {
    return "labor-exchange";
  }

  private RuntimeUtils.InvokeDispatcher dispatcher = new RuntimeUtils.InvokeDispatcher(getClass(), this::unhandled);
  public static ActorRef reference(ActorContext context) {
    return AkkaTools.getOrCreate("/user/labor-exchange", context.system(),
        (name, factory) -> factory.actorOf(Props.create(LaborExchange.class), "labor-exchange")
    );
  }

  public static ActorSelection experts(ActorContext context) {
    return context.actorSelection("/user/labor-exchange/experts");
  }

  public void invoke(SaveSnapshotSuccess sss) {}
  public void invoke(SaveSnapshotFailure ssf) {}

  /**
   * User: solar
   * Date: 19.12.15
   * Time: 17:32
   */
  public static class Experts extends UntypedActorAdapter {
    public void invoke(JID jid) {
      sender().tell(AkkaTools.getOrCreate(jid.bare().toString(), context(), () -> Props.create(ExpertRole.class)), self());
    }

    public void invoke(Object o) {
      if (o instanceof JID || o instanceof ExpertRole.State || o instanceof Timeout)
        return;
      JavaConversions.asJavaCollection(context().children()).stream().forEach(
          expert -> expert.forward(o, context())
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

    public void invoke(Timeout to) {
      stateTimeout = null;
      sendState();
    }

    int sentCount = 0;
    public void sendState() {
      if (stateTimeout != null)
        return;
      if (sentCount != readyCount) {
        sentCount = readyCount;
        XMPP.send(new Presence(XMPP.jid(), readyCount != 0, new ServiceStatus(readyCount)), context());
        stateTimeout = AkkaTools.scheduleTimeout(context(), Duration.apply(10, TimeUnit.SECONDS), self());
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
      return JID.parse(ref.path().name());
    }

    public static ActorRef agent(JID onTask, ActorContext context) {
      return AkkaTools.ask(experts(context), onTask);
    }
  }
}

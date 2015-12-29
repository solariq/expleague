package com.tbts.server.agents;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.persistence.SaveSnapshotFailure;
import akka.persistence.SaveSnapshotSuccess;
import akka.persistence.SnapshotOffer;
import akka.persistence.UntypedPersistentActor;
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

import java.util.*;

/**
 * User: solar
 * Date: 17.12.15
 * Time: 15:18
 */
public class LaborExchange extends UntypedPersistentActor {
  private final Map<String, ActorRef> openPositions = new HashMap<>();

  public void invoke(Offer offer) {
    experts();
    final Collection<ActorRef> children = JavaConversions.asJavaCollection(context().children());
    for (final ActorRef ref : children) {
      if (!"experts".equals(ref.path().name())) {
        if (AkkaTools.ask(ref, offer) instanceof Operations.Ok) {
          openPositions.put(offer.room().local(), ref);
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

  public void invoke(JID expert) {
    experts().forward(expert, context());
  }

  private ActorRef experts() {
    return AkkaTools.getOrCreate("experts", context(), () -> Props.create(Dpt.class));
  }

  public void invoke(Operations.Ok ok) {
    final JID jid = Dpt.jid(sender());
    openPositions.remove(jid.local());
    saveSnapshot(new ArrayList<>(openPositions.keySet()));
  }

  public void invoke(Operations.Cancel cancel) {
    final JID jid = Dpt.jid(sender());
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
      saveSnapshot(new ArrayList<>(openPositions.keySet()));
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

  public static ActorRef registerExpert(JID jid, ActorContext context) {
    return AkkaTools.ask(reference(context), jid);
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
  public static class Dpt extends UntypedActorAdapter {
    public void invoke(JID jid) {
      sender().tell(AkkaTools.getOrCreate(jid.bare().toString(), context(), () -> Props.create(ExpertRole.class)), self());
    }

    public void invoke(Object o) {
      if (o instanceof JID || o instanceof ExpertRole.State)
        return;
      JavaConversions.asJavaCollection(context().children()).stream().forEach(
          expert -> expert.forward(o, context())
      );
    }

    public int readyCount = 0;
    public void invoke(ExpertRole.State state) {
      switch (state) {
        case READY:
          readyCount++;
          break;
        case OFFLINE:
          readyCount--;
          break;
        case CHECK:
        case INVITE:
        case BUSY:
      }
      XMPP.send(new Presence(XMPP.jid(), readyCount != 0, new ServiceStatus(readyCount)), context());
    }

    public static JID jid(ActorRef ref) {
      return JID.parse(ref.path().name());
    }
  }
}

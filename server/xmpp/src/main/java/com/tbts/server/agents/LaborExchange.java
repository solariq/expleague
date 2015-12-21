package com.tbts.server.agents;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.pattern.AskableActorRef;
import akka.persistence.UntypedPersistentActor;
import akka.util.Timeout;
import com.spbsu.commons.system.RuntimeUtils;
import com.tbts.modelNew.Offer;
import com.tbts.server.agents.roles.BrokerRole;
import com.tbts.server.agents.roles.ExpertRole;
import com.tbts.util.akka.AkkaTools;
import com.tbts.util.akka.UntypedActorAdapter;
import com.tbts.xmpp.JID;
import scala.collection.JavaConversions;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 17.12.15
 * Time: 15:18
 */
public class LaborExchange extends UntypedPersistentActor {
  private static final Logger log = Logger.getLogger(LaborExchange.class.getName());
  public void invoke(Offer offer) {
    experts();
    final Collection<ActorRef> children = JavaConversions.asJavaCollection(context().children());
    for (final ActorRef ref : children) {
      if (!"experts".equals(ref.path().name())) {
        final AskableActorRef ask = new AskableActorRef(ref);
        final Future<Object> future = ask.ask(offer, Timeout.apply(AkkaTools.AKKA_OPERATION_TIMEOUT));
        try {
          final Object result = Await.result(future, Duration.Inf());
          if (result instanceof BrokerRole.On)
            return;
        }
        catch (Exception e) {
          log.log(Level.WARNING, "Exception during offer", e);
        }
      }
    }
    context().actorOf(Props.create(BrokerRole.class)).tell(offer, self());
  }

  @SuppressWarnings("UnusedParameters")
  public void invoke(BrokerRole.On on){
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

  @Override
  public void onReceiveRecover(Object o) throws Exception {
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

  public static ActorRef register(JID jid, ActorContext context) {
    final Future<Object> future = new AskableActorRef(reference(context)).ask(jid, Timeout.apply(AkkaTools.AKKA_OPERATION_TIMEOUT));
    try {
      return (ActorRef)Await.result(future, Duration.Inf());
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static ActorSelection experts(ActorContext context) {
    return context.actorSelection("/user/labor-exchange/experts");
  }

  /**
   * User: solar
   * Date: 19.12.15
   * Time: 17:32
   */
  public static class Dpt extends UntypedActorAdapter {
    public void invoke(JID jid) {
      sender().tell(
          AkkaTools.getOrCreate(jid.bare().toString(), context(), () -> Props.create(ExpertRole.class)),
          self());
    }

    public void invoke(Object o) {
      if (o instanceof JID)
        return;
      JavaConversions.asJavaCollection(context().children()).stream().forEach(
          expert -> expert.forward(o, context())
      );
    }
  }
}

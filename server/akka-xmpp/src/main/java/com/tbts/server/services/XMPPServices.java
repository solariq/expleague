package com.tbts.server.services;

import akka.actor.*;
import com.tbts.util.akka.UntypedActorAdapter;
import com.tbts.xmpp.Item;
import com.tbts.xmpp.control.XMPPQuery;
import com.tbts.xmpp.stanza.Iq;
import scala.runtime.AbstractFunction0;

import java.util.HashMap;
import java.util.Map;

/**
 * User: solar
 * Date: 15.12.15
 * Time: 13:20
 */
public class XMPPServices extends UntypedActorAdapter {
  public void invoke(Iq<?> iq) {
    final String ns = iq.serviceNS();
    if (knownServices.containsKey(ns)) {
      final String shortName = shortNames.get(ns);
      final ActorRef service = getContext()
          .child(shortName)
          .getOrElse(new AbstractFunction0<ActorRef>() {
            @Override
            public ActorRef apply() {
              return getContext().actorOf(Props.create(knownServices.get(ns)), shortName);
            }
          });
      service.forward(iq, getContext());
    }
    else if (iq.get() instanceof XMPPQuery) {
      final Item answer = ((XMPPQuery) iq.get()).reply(iq.type());
      if (answer != null)
        getSender().tell(Iq.answer(iq, answer), getSelf());
    }
    else unhandled(iq);
  }

  private static Map<String, Class<? extends Actor>> knownServices = new HashMap<>();
  private static Map<String, String> shortNames = new HashMap<>();
  public static void register(String ns, Class<? extends Actor> actorClass, String shortName) {
    knownServices.put(ns, actorClass);
    shortNames.put(ns, shortName);
  }

  public static ActorSelection reference(ActorSystem system) {
    return system.actorSelection("/user/services");
  }
}

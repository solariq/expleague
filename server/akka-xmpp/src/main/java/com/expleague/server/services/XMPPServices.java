package com.expleague.server.services;

import akka.actor.*;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.control.XMPPQuery;
import com.expleague.xmpp.stanza.Iq;
import scala.runtime.AbstractFunction0;

import java.util.HashMap;
import java.util.Map;

/**
 * User: solar
 * Date: 15.12.15
 * Time: 13:20
 */
public class XMPPServices extends ActorAdapter<AbstractActor> {
  @ActorMethod
  public void invoke(Iq<?> iq) {
    final String ns = iq.serviceNS();
    if (knownServices.containsKey(ns)) {
      final String shortName = shortNames.get(ns);
      final ActorRef service = context()
          .child(shortName)
          .getOrElse(new AbstractFunction0<ActorRef>() {
            @Override
            public ActorRef apply() {
              return context().actorOf(props(knownServices.get(ns)), shortName);
            }
          });
      service.forward(iq, context());
    }
    else if (iq.get() instanceof XMPPQuery) {
      final Item answer = ((XMPPQuery) iq.get()).reply(iq.type());
      if (answer != null)
        sender().tell(Iq.answer(iq, answer), self());
    }
    else unhandled(iq);
  }

  private static Map<String, Class<? extends ActorAdapter>> knownServices = new HashMap<>();
  private static Map<String, String> shortNames = new HashMap<>();
  public static void register(String ns, Class<? extends ActorAdapter> actorClass, String shortName) {
    knownServices.put(ns, actorClass);
    shortNames.put(ns, shortName);
  }

  public static ActorSelection reference(ActorSystem system) {
    return system.actorSelection("/user/services");
  }
}

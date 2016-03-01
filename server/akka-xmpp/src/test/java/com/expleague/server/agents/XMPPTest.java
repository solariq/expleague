package com.expleague.server.agents;

import akka.actor.*;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.JID;
import org.junit.Test;
import scala.concurrent.duration.Duration;

import static org.junit.Assert.*;

/**
 * @author vpdelta
 */
public class XMPPTest extends ActorSystemTestCase {
  @Test
  public void testXmppReceivesJid() throws Exception {
    new TestKit()  {{
      final JID jid = JID.parse("login");
      xmpp.tell(jid, getRef());

      final ActorRef actorRef = expectActorRef();
      assertEquals(jid, XMPP.jid(actorRef));

      xmpp.tell(jid, getRef());
      new Within(Duration.Zero(), duration("1 second")) {
        @Override
        protected void run() {
          expectMsgEquals(actorRef);
        }
      };

      final ActorRef actorFinder = system.actorOf(Props.create(ActorFinder.class));
      actorFinder.tell("/user/xmpp/" + jid.bare().getAddr(), getRef());
      new Within(Duration.Zero(), duration("1 second")) {
        @Override
        protected void run() {
          expectMsgEquals(actorRef);
        }
      };
    }};
  }

  @Test
  public void testXmppRegistration() throws Exception {
    new TestKit()  {{
      final JID jid = JID.parse("login");
      system.actorOf(Props.create(Registrator.class), "registrator-tester").tell(jid, getRef());

      new Within(Duration.Zero(), duration("1 second")) {
        @Override
        protected void run() {
          assertEquals(jid, XMPP.jid(expectActorRef()));
        }
      };
    }};
  }

  @Test
  public void testXmppRegistrationOfMock() throws Exception {
    new TestKit()  {{
      final JID jid = JID.parse("login");
      final ActorRef actorRef = registerMock(jid, new ActorAdapter() {
        @ActorMethod
        public void reply(final String xxx) {
          sender().tell("Reply to " + xxx, self());
        }
      });
      actorRef.tell("Hello", getRef());
      expectMsgEquals("Reply to Hello");
    }};
  }

  @Test
  public void testSubscribe() throws Exception {
    final XMPP.Subscriptions subscriptions = new XMPP.Subscriptions();
    final JID subscriber = JID.parse("user1");
    final JID room1 = JID.parse("room1");
    final JID room2 = JID.parse("room2");
    assertFalse(subscriptions.isSubscribed(subscriber, room1));
    assertFalse(subscriptions.isSubscribed(subscriber, room2));
    subscriptions.subscribe(new XMPP.Subscribe(subscriber, room1));
    assertTrue(subscriptions.isSubscribed(subscriber, room1));
    assertFalse(subscriptions.isSubscribed(subscriber, room2));
  }
}
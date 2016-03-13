package com.expleague.server.agents;

import akka.actor.ActorRef;
import com.expleague.model.Delivered;
import com.expleague.model.Operations;
import com.expleague.server.Roster;
import com.expleague.server.XMPPDevice;
import com.expleague.server.XMPPUser;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.control.register.Query;
import com.expleague.xmpp.stanza.Iq;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Presence;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author vpdelta
 */
public class UserAgentTest extends ActorSystemTestCase {
  @Test
  public void testInitializationAndPresenceExchange() throws Exception {
    new TestKit()  {{
      final JID jid1 = JID.parse("login1@localhost");
      final JID jid2 = JID.parse("login2@localhost");

      final ActorRef userAgentRef1 = register(jid1);
      final ActorRef userAgentRef2 = register(jid2);

      userAgentRef1.tell(new UserAgent.ConnStatus(true, "resource", registerFakeDevice(jid1)), getRef());
      expectMsgClass(ActorRef.class);

      userAgentRef2.tell(new UserAgent.ConnStatus(true, "resource", registerFakeDevice(jid2)), getRef());
      expectMsgClass(ActorRef.class);

      userAgentRef2.tell(new Presence(jid1, true), getRef());
      final Presence presenceOf1 = expectMessage(Presence.class);
      assertEquals(jid1, presenceOf1.from());
      assertTrue(presenceOf1.available());

      userAgentRef1.tell(new Presence(jid2, true), getRef());
      final Presence presenceOf2 = expectMessage(Presence.class);
      assertEquals(jid2, presenceOf2.from());
      assertTrue(presenceOf2.available());
    }};
  }

  @Test
  public void testMessageDelivery() throws Exception {
    new TestKit()  {{
      final JID jid1 = JID.parse("login1@localhost");
      final JID jid2 = JID.parse("login2@localhost");
      final XMPPDevice device2 = registerFakeDevice(jid1);

      final ActorRef userAgentRef2 = register(jid2);

      // send message to jid1
      userAgentRef2.tell(new Message(jid2, jid1, "Hello"), getRef());
      expectNoMsg();

      // connect jid1 and receive message
      final ActorRef userAgentRef1 = register(jid1);
      userAgentRef1.tell(new UserAgent.ConnStatus(true, "resource", device2), getRef());
      expectMsgClass(ActorRef.class);
      final Message message = expectMessage(Message.class);
      assertEquals("Hello", message.body());

      // send delivery ack to jid1
      userAgentRef1.tell(new Delivered(message.id(), "resource"), getRef());
      expectNoMsg();

      // disconnect
      userAgentRef1.tell(new UserAgent.ConnStatus(false, "resource", device2), getRef());
      expectNoMsg();

      // reconnect (no messages will come)
      // todo: shouldn't there be a state sync logic between client and server?
      userAgentRef1.tell(new UserAgent.ConnStatus(true, "resource", device2), getRef());
      expectMsgClass(ActorRef.class);
    }};
  }

  @Test
  public void testIqDelivery() throws Exception {
    new TestKit()  {{
      final JID jid1 = JID.parse("login1@localhost");
      final JID jid2 = JID.parse("login2@localhost");

      final ActorRef userAgentRef1 = register(jid1);

      // register test actor as connector
      userAgentRef1.tell(new UserAgent.ConnStatus(true, "resource", registerFakeDevice(jid1)), getRef());
      expectMsgClass(ActorRef.class);
      expectMsgClass(Presence.class);

      // send message to jid1
      final Iq<Operations.Ok> iq = Iq.create(jid1, Iq.IqType.GET, new Operations.Ok());
      iq.from(jid2);
      userAgentRef1.tell(iq, getRef());
      expectMsgEquals(iq);
    }};
  }

  @Test
  public void testUserAgentWithComposition() throws Exception {
    new TestKit()  {{
      final JID jid1 = JID.parse("login1");
      final JID jid2 = JID.parse("login2");

      class ActorOverrideTester extends ActorAdapter {
        @ActorMethod
        public void reply(final String xxx) {
          sender().tell("Reply to " + xxx, self());
        }
      }

      final ActorRef actorRef = registerOverride(jid1, new ActorOverrideTester());

      // register test actor as connector
      actorRef.tell(new UserAgent.ConnStatus(true, "resource", registerFakeDevice(jid1)), getRef());
      expectMsgClass(ActorRef.class);
      expectMsgClass(Presence.class);

      // send message to jid1
      final Iq<Operations.Ok> iq = Iq.create(jid1, Iq.IqType.GET, new Operations.Ok());
      iq.from(jid2);
      actorRef.tell(iq, getRef());
      expectMsgEquals(iq);

      actorRef.tell("Hello", getRef());
      expectMsgEquals("Reply to Hello");
    }};
  }

  @Test
  public void testUserAgentWithOverride() throws Exception {
    new TestKit()  {{
      final JID jid1 = JID.parse("login1");
      final JID jid2 = JID.parse("login2");

      class ActorOverrideTester extends ActorAdapter {
        @ActorMethod
        public void reply(final UserAgent.ConnStatus status) {
          sender().tell("Get status from " + status.resource, self());
          unhandled(status);
        }
      }

      final ActorRef actorRef = registerOverride(jid1, new ActorOverrideTester());

      // register test actor as connector
      actorRef.tell(new UserAgent.ConnStatus(true, "resource", registerFakeDevice(jid1)), getRef());
      final Object[] messages = receiveN(2);
      if (messages[0] instanceof ActorRef) {
        assertEquals("Get status from resource", messages[1]);
      }
      else {
        assertEquals("Get status from resource", messages[0]);
        assertTrue(messages[1] instanceof ActorRef);
      }
      expectMsgClass(Presence.class);

      // send message to jid1
      final Iq<Operations.Ok> iq = Iq.create(jid1, Iq.IqType.GET, new Operations.Ok());
      iq.from(jid2);
      actorRef.tell(iq, getRef());
      expectMsgEquals(iq);
    }};
  }

  private XMPPDevice registerFakeDevice(JID jid1) throws Exception {
    final Query query = new Query();
    query.username(jid1.local());
    query.name("Fake User: " + jid1.local());
    query.passwd("");
    Roster.instance().register(query);
    return Roster.instance().device(jid1.local());
  }
}
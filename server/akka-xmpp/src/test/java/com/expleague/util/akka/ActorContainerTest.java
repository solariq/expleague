package com.expleague.util.akka;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import com.expleague.server.agents.ActorSystemTestCase;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author vpdelta
 */
public class ActorContainerTest extends ActorSystemTestCase {
  public static class TestActor extends ActorAdapter {
    private final String id;

    public TestActor(final String id) {
      this.id = id;
    }

    @ActorMethod
    public void test(final String msg) {
      sender().tell(id + " reply to " + msg, self());
    }
  }

  @Test
  public void testActorContainer() throws Exception {
    new JavaTestKit(system) {{
      final ActorRef tester = system.actorOf(ActorContainer.props(TestActor.class, "Tester"));
      tester.tell("hello", getRef());
      expectMsgEquals("Tester reply to hello");
    }};
  }
}
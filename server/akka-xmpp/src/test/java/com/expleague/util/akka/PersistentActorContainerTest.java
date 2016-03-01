package com.expleague.util.akka;

import akka.actor.ActorRef;
import akka.testkit.JavaTestKit;
import com.expleague.server.agents.ActorSystemTestCase;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author vpdelta
 */
public class PersistentActorContainerTest extends ActorSystemTestCase {
  public static class TestActor extends PersistentActorAdapter {
    private final String id;

    public TestActor(final String id) {
      this.id = id;
    }

    @ActorMethod
    public void test(final String msg) {
      sender().tell(id + " reply to " + msg, self());
    }

    @ActorRecover
    public void recover(final Object msg) {
    }

    @Override
    public String persistenceId() {
      return "some-test-id";
    }
  }

  @Test
  public void testPersistentActorContainer() throws Exception {
    new JavaTestKit(system) {{
      final ActorRef tester = system.actorOf(PersistentActorContainer.props(TestActor.class, "Tester"));
      tester.tell("hello", getRef());
      expectMsgEquals("Tester reply to hello");
    }};
  }
}
package com.expleague.server.agents.roles;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import akka.testkit.TestFSMRef;
import com.expleague.server.agents.ActorSystemTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author vpdelta
 */
public class BrokerRoleTest extends ActorSystemTestCase {
  @Test
  public void testBrokerFSM() throws Exception {
    new JavaTestKit(system)  {{
      final ActorRef brokerRole = system.actorOf(Props.create(BrokerRole.class));
    }};
  }
}
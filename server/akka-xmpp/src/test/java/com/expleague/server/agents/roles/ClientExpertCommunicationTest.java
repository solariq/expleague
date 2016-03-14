package com.expleague.server.agents.roles;

import org.junit.Test;

/**
 * @author vpdelta
 */
public class ClientExpertCommunicationTest extends CommunicationAcceptanceTestCase {
  @Test
  /*need to add Invite method. Not only Offer(check)*/
  public void testClientReceivesAnswerAlternatelyOnline() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert");
      client.goOnline();
      final Room room = client.query("Task");
      expert.goOnline();
      expert.acceptOffer(room, "Task");
      client.receiveStart(expert);
      expert.sendAnswer(room, "Answer");
      client.receiveAnswer(expert, "Answer");
    }};
  }

  @Test
  /*need to add Invite method. Not only Offer(check)*/
  public void testClientReceivesAnswerTogetherOnline() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert");
      expert.goOnline();
      client.goOnline();
      final Room room = client.query("Task");
      expert.acceptOffer(room, "Task");
      client.receiveStart(expert);
      expert.sendAnswer(room, "Answer");
      client.receiveAnswer(expert, "Answer");
    }};
  }

  @Test
  /*need to add Invite method. Not only Offer(check)*/
  public void testClientSendLocationOption() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert");
      expert;
      client.goOnline();
      final Room room = client.query("Task");
      expert.acceptOffer(room, "Task");
      client.receiveStart(expert);
      expert.sendAnswer(room, "Answer");
      client.receiveAnswer(expert, "Answer");
    }};
  }



  @Test
  public void testClientReceivesAnswerAfterReject() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert1");
      final Expert expert2 = registerExpert("expert2");

      client.goOnline();
      final Room room = client.query("Task");

      expert.goOnline();
      expert.rejectOffer(room, "Task");

      expert2.goOnline();
      expert2.acceptOffer(room, "Task");

      client.receiveStart(expert2);
      expert2.sendAnswer(room, "Answer");

      client.receiveAnswer(expert2, "Answer");
    }};
  }

  @Test
  public void testClientReceivesAnswerWhenBecameOnline() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert1");

      client.goOnline();
      final Room room = client.query("Task");
      client.goOffline();

      expert.goOnline();
      expert.acceptOffer(room, "Task");
      expert.sendAnswer(room, "Answer");

      client.goOnline();
      client.receiveAnswer(expert, "Answer");
    }};
  }


}

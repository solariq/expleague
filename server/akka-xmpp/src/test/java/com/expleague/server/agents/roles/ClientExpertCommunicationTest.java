package com.expleague.server.agents.roles;

import org.junit.Test;

/**
 * @author vpdelta
 */
public class ClientExpertCommunicationTest extends CommunicationAcceptanceTestCase {
  @Test
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
  public void testClientReceivesAnswerTogetherOnline() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert");
      expert.goOnline();
      client.goOnline();
      final Room room = client.query("Task");
      expert.receiveOffer(room, "Task");
      expert.acceptOffer(room, "Task");
      client.receiveStart(expert);
      expert.sendAnswer(room, "Answer");
      client.receiveAnswer(expert, "Answer");
    }};
  }

  @Test
   /*How to check which room is active/a target for the "Start"/"Answer"*/
  public void testClientReceivesAnswersInManyRoomsOneExpert() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert");
      expert.goOnline();
      client.goOnline();
      final Room room = client.query("Task1");
      final Room room2 = client.query("Task2");

      expert.receiveOffer(room, "Task1");
      expert.acceptOffer(room, "Task1");
      client.receiveStart(expert);
      expert.sendAnswer(room, "Answer1");
      client.receiveAnswer(expert, "Answer1");

      expert.receiveOffer(room2, "Task2");
      expert.acceptOffer(room2, "Task2");
      client.receiveStart(expert);
      expert.sendAnswer(room2, "Answer2");
      client.receiveAnswer(expert, "Answer2");
    }};
  }

  @Test
   /*How to check which room is active/a target for the "Start"/"Answer"*/
  public void testClientReceivesAnswersInManyRoomsOneExpertOfflineStatus() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert");
      expert.goOnline();
      client.goOnline();
      final Room room = client.query("Task1");
      final Room room2 = client.query("Task2");
      client.goOffline();

      expert.receiveOffer(room, "Task1");
      expert.acceptOffer(room, "Task1");
      expert.sendAnswer(room, "Answer1");

      expert.receiveOffer(room, "Task2");
      expert.acceptOffer(room2, "Task2");
      expert.sendAnswer(room2, "Answer2");

      client.goOnline();
      client.receiveStart(expert);
      client.receiveAnswer(expert, "Answer1");
      client.receiveStart(expert);
      client.receiveAnswer(expert, "Answer2");
    }};
  }

  @Test
  /*How to check which room is active/a target for the "Start"/"Answer"*/
  public void testClientReceivesAnswersInManyRoomsOneExpertOfflineStatus2() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert");
      expert.goOnline();
      client.goOnline();
      final Room room = client.query("Task1");
      final Room room2 = client.query("Task2");
      client.goOffline();

      expert.receiveOffer(room, "Task1");
      expert.acceptOffer(room, "Task1");
      expert.sendAnswer(room, "Answer1");

      client.goOnline();
      client.receiveStart(expert);
      client.receiveAnswer(expert, "Answer1");
      client.goOffline();

      expert.receiveOffer(room2, "Task2");
      expert.acceptOffer(room2, "Task2");
      expert.sendAnswer(room2, "Answer2");

      client.goOnline();
      client.receiveStart(expert);
      client.receiveAnswer(expert, "Answer2");
    }};
  }

  @Test
  public void testClientReceivesAnswersInManyRoomsOneExpertOfflineStatus3() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert");
      expert.goOnline();
      client.goOnline();
      final Room room = client.query("Task1");
      final Room room2 = client.query("Task2");
      client.goOffline();

      expert.receiveOffer(room, "Task1");
      expert.acceptOffer(room, "Task1");
      expert.sendAnswer(room, "Answer1");

      client.goOnline();
      client.receiveStart(expert);
      client.receiveAnswer(expert, "Answer1");
      expert.receiveOffer(room2, "Task2");
      expert.acceptOffer(room2, "Task2");
      client.receiveStart(expert);

      client.goOffline();
      expert.sendAnswer(room2, "Answer2");

      client.goOnline();
      client.receiveAnswer(expert, "Answer2");
    }};
  }

  @Test
  /*How to check which room is active/a target for the "Start"/"Answer"*/
  public void testClientReceivesAnswersInManyRoomsTwoExperts() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert1");
      final Expert expert2 = registerExpert("expert2");
      expert.goOnline();
      expert2.goOnline();
      client.goOnline();
      final Room room = client.query("Task1");
      final Room room2 = client.query("Task2");

      expert.receiveOffer(room, "Task1");
      expert.acceptOffer(room, "Task1");
      client.receiveStart(expert);

      expert2.receiveOffer(room2, "Task2");
      expert2.acceptOffer(room2, "Task2");
      client.receiveStart(expert2);

      expert.sendAnswer(room, "Answer1");
      client.receiveAnswer(expert, "Answer1");

      expert2.sendAnswer(room2, "Answer2");
      client.receiveAnswer(expert2, "Answer2");
    }};
  }

  @Test
  public void testClientReceivesAnswersInManyRoomsTwoExpertsOfflineStatus() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert1");
      final Expert expert2 = registerExpert("expert2");
      expert.goOnline();
      expert2.goOnline();
      client.goOnline();
      final Room room = client.query("Task1");
      final Room room2 = client.query("Task2");
      client.goOffline();

      expert.receiveOffer(room, "Task1");
      expert.acceptOffer(room, "Task1");

      expert2.receiveOffer(room2, "Task2");
      expert2.acceptOffer(room2, "Task2");

      expert.sendAnswer(room, "Answer1");
      expert2.sendAnswer(room2, "Answer2");

      client.goOnline();
      client.receiveStart(expert);
      client.receiveAnswer(expert, "Answer1");
      client.receiveStart(expert2);
      client.receiveAnswer(expert2, "Answer2");
    }};
  }

  @Test
  public void testClientReceivesAnswersInManyRoomsTwoExpertsOfflineStatus2() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert1");
      final Expert expert2 = registerExpert("expert2");
      expert.goOnline();
      expert2.goOnline();
      client.goOnline();
      final Room room = client.query("Task1");
      final Room room2 = client.query("Task2");
      client.goOffline();

      expert.receiveOffer(room, "Task1");
      expert.acceptOffer(room, "Task1");
      expert.sendAnswer(room, "Answer1");

      client.goOnline();
      client.receiveStart(expert);
      client.receiveAnswer(expert, "Answer1");
      client.goOffline();

      expert2.receiveOffer(room2, "Task2");
      expert2.acceptOffer(room2, "Task2");
      expert2.sendAnswer(room2, "Answer2");

      client.goOnline();
      client.receiveStart(expert2);
      client.receiveAnswer(expert2, "Answer2");
    }};
  }

  @Test
  public void testClientReceivesAnswersInManyRoomsTwoExpertsOfflineStatus3() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert1");
      final Expert expert2 = registerExpert("expert2");
      expert.goOnline();
      expert2.goOnline();
      client.goOnline();
      final Room room = client.query("Task1");
      final Room room2 = client.query("Task2");
      client.goOffline();

      expert.receiveOffer(room, "Task1");
      expert.acceptOffer(room, "Task1");
      expert.sendAnswer(room, "Answer1");

      client.goOnline();
      client.receiveStart(expert);
      client.receiveAnswer(expert, "Answer1");
      expert2.receiveOffer(room2, "Task2");
      expert2.acceptOffer(room2, "Task2");
      client.receiveStart(expert2);
      client.goOffline();

      expert2.sendAnswer(room2, "Answer2");
      client.goOnline();
      client.receiveAnswer(expert2, "Answer2");
    }};
  }

  @Test
  public void testClientReceivesAnswerAfterInviteReject() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert1");
      final Expert expert2 = registerExpert("expert2");

      client.goOnline();
      final Room room = client.query("Task");

      expert.goOnline();
      expert.receiveOffer(room, "Task");
      expert.rejectOffer(room, "Task");

      expert2.goOnline();
      expert2.receiveOffer(room, "Task");
      expert2.acceptOffer(room, "Task");

      client.receiveStart(expert2);
      expert2.sendAnswer(room, "Answer");
      client.receiveAnswer(expert2, "Answer");
    }};
  }

  @Test
  public void testTwoExpertsReceiveInvitesTogether() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert1");
      final Expert expert2 = registerExpert("expert2");

      client.goOnline();
      final Room room = client.query("Task");

      expert.goOnline();
      expert2.goOnline();

      expert.receiveOffer(room, "Task");
      expert2.receiveOffer(room, "Task");

      expert2.acceptOffer(room, "Task");

      client.receiveStart(expert2);
      expert2.sendAnswer(room, "Answer");
      client.receiveAnswer(expert2, "Answer");
    }};
  }

  @Test
  public void testThreeExpertsReceiveInvitesTogether() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert1");
      final Expert expert2 = registerExpert("expert2");
      final Expert expert3 = registerExpert("expert3");

      client.goOnline();
      final Room room = client.query("Task");

      expert.goOnline();
      expert2.goOnline();
      expert3.goOnline();

      expert.receiveOffer(room, "Task");
      expert2.receiveOffer(room, "Task");
      expert3.receiveOffer(room, "Task");

      expert2.acceptOffer(room, "Task");

      client.receiveStart(expert2);
      expert2.sendAnswer(room, "Answer");
      client.receiveAnswer(expert2, "Answer");
    }};
  }

  @Test
  public void testThreeExpertsReceiveInvitesTogether2() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert1");
      final Expert expert2 = registerExpert("expert2");
      final Expert expert3 = registerExpert("expert3");
      expert.goOnline();
      expert2.goOnline();
      expert3.goOnline();
      client.goOnline();
      final Room room = client.query("Task");

      expert.receiveOffer(room, "Task");
      expert2.receiveOffer(room, "Task");
      expert3.receiveOffer(room, "Task");

      expert2.acceptOffer(room, "Task");

      client.receiveStart(expert2);
      expert2.sendAnswer(room, "Answer");
      client.receiveAnswer(expert2, "Answer");
    }};
  }

  @Test
  public void testThreeExpertsReceiveInvitesTogetherOnlineDiffTime() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert1");
      final Expert expert2 = registerExpert("expert2");
      final Expert expert3 = registerExpert("expert3");
      expert.goOnline();
      client.goOnline();
      final Room room = client.query("Task");
      expert2.goOnline();
      expert3.goOnline();

      expert.receiveOffer(room, "Task");
      expert2.receiveOffer(room, "Task");
      expert3.receiveOffer(room, "Task");

      expert2.acceptOffer(room, "Task");

      client.receiveStart(expert2);
      expert2.sendAnswer(room, "Answer");
      client.receiveAnswer(expert2, "Answer");
    }};
  }

  @Test
  public void testThreeExpertsReceiveInvitesTogetherOtherExpertsNot() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert1");
      final Expert expert2 = registerExpert("expert2");
      final Expert expert3 = registerExpert("expert3");
      final Expert expert4 = registerExpert("expert4");
      final Expert expert5 = registerExpert("expert5");
      final Expert expert6 = registerExpert("expert6");
      final Expert expert7 = registerExpert("expert7");

      client.goOnline();
      final Room room = client.query("Task");
      expert.goOnline();
      expert2.goOnline();
      expert3.goOnline();

      expert.receiveOffer(room, "Task");
      expert2.receiveOffer(room, "Task");
      expert3.receiveOffer(room, "Task");

      expert4.goOnline();
      expert5.goOnline();
      expert6.goOnline();
      final Room room2 = client.query("Task2");
      expert7.goOnline();
      expert7.receiveOffer(room, "Task2");

      expert7.acceptOffer(room2, "Task2");
      client.receiveStart(expert7);
      expert2.acceptOffer(room, "Task");
      client.receiveStart(expert2);

      expert2.sendAnswer(room, "Answer");
      client.receiveAnswer(expert2, "Answer");
      expert7.sendAnswer(room2, "Answer2");
      client.receiveAnswer(expert7, "Answer2");

    }};
  }

  @Test
  public void testInvitedExpertsRecieveNextInviteAfterBecomeFree() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert1");
      final Expert expert2 = registerExpert("expert2");
      final Expert expert3 = registerExpert("expert3");
      final Expert expert4 = registerExpert("expert4");
      final Expert expert5 = registerExpert("expert5");
      final Expert expert6 = registerExpert("expert6");
      final Expert expert7 = registerExpert("expert7");

      client.goOnline();
      final Room room = client.query("Task");
      expert.goOnline();
      expert2.goOnline();
      expert3.goOnline();

      expert.receiveOffer(room, "Task");
      expert2.receiveOffer(room, "Task");
      expert3.receiveOffer(room, "Task");

      expert4.goOnline();
      expert5.goOnline();
      expert6.goOnline();

      final Room room2 = client.query("Task2");
      expert7.goOnline();
      expert7.receiveOffer(room, "Task2");
      expert7.acceptOffer(room2, "Task2");
      client.receiveStart(expert7);

      expert4.goOffline();
      expert5.goOffline();
      expert6.goOffline();

      final Room room3 = client.query("Task3");

      expert2.acceptOffer(room, "Task");
      client.receiveStart(expert2);

      expert.receiveOffer(room3, "Task3");
      expert3.receiveOffer(room3, "Task3");

      expert3.acceptOffer(room3, "Task3");
      client.receiveStart(expert3);

      expert2.sendAnswer(room, "Answer");
      client.receiveAnswer(expert2, "Answer");
      expert7.sendAnswer(room2, "Answer2");
      client.receiveAnswer(expert7, "Answer2");
      expert3.sendAnswer(room3, "Answer3");
      client.receiveAnswer(expert3, "Answer3");

    }};
  }

  @Test
  public void testBookedExpertsRecieveNextInviteAfterBecomeFree() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert1");
      final Expert expert2 = registerExpert("expert2");
      final Expert expert3 = registerExpert("expert3");
      final Expert expert4 = registerExpert("expert4");
      final Expert expert5 = registerExpert("expert5");
      final Expert expert6 = registerExpert("expert6");
      final Expert expert7 = registerExpert("expert7");

      client.goOnline();
      final Room room = client.query("Task");
      expert.goOnline();
      expert2.goOnline();
      expert3.goOnline();

      expert.receiveOffer(room, "Task");
      expert2.receiveOffer(room, "Task");
      expert3.receiveOffer(room, "Task");

      expert4.goOnline();
      expert5.goOnline();
      expert6.goOnline();

      final Room room2 = client.query("Task2");
      expert7.goOnline();
      expert7.receiveOffer(room, "Task2");
      expert7.acceptOffer(room2, "Task2");
      client.receiveStart(expert7);

      expert.goOffline();
      expert3.goOffline();
      expert4.goOffline();

      expert5.receiveOffer(room, "Task");
      expert6.receiveOffer(room, "Task");

      expert2.acceptOffer(room, "Task");
      client.receiveStart(expert2);

      final Room room3 = client.query("Task3");
      expert5.receiveOffer(room3, "Task3");
      expert6.receiveOffer(room3, "Task3");

      expert5.acceptOffer(room3, "Task3");
      client.receiveStart(expert5);

      expert2.sendAnswer(room, "Answer");
      client.receiveAnswer(expert2, "Answer");
      expert7.sendAnswer(room2, "Answer2");
      client.receiveAnswer(expert7, "Answer2");
      expert5.sendAnswer(room3, "Answer3");
      client.receiveAnswer(expert5, "Answer3");

    }};
  }

  @Test
  public void testExpertRecieveNextOfferAfterReturningOnlineTwoRooms() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert1");
      final Expert expert2 = registerExpert("expert2");
      final Expert expert3 = registerExpert("expert2");
      expert.goOnline();
      expert2.goOnline();
      expert3.goOnline();
      client.goOnline();
      final Room room = client.query("Task");

      expert.receiveOffer(room, "Task");
      expert2.receiveOffer(room, "Task");
      expert3.receiveOffer(room, "Task");

      expert3.goOffline();

      final Room room2 = client.query("Task2");
      expert2.acceptOffer(room, "Task");
      client.receiveStart(expert2);

      expert3.goOnline();
      expert.receiveOffer(room2, "Task2");
      expert3.receiveOffer(room2, "Task2");
      expert.acceptOffer(room2, "Task2");
      client.receiveStart(expert);

      expert2.sendAnswer(room, "Answer1");
      expert.sendAnswer(room, "Answer2");
      client.receiveAnswer(expert2, "Answer1");
      client.receiveAnswer(expert, "Answer2");

    }};
  }

  @Test
  public void testClientReceivesAnswerAfterOneExpertRejectsInvite() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert1");
      final Expert expert2 = registerExpert("expert2");

      client.goOnline();
      final Room room = client.query("Task");

      expert.goOnline();
      expert2.goOnline();

      expert.receiveOffer(room, "Task");
      expert2.receiveOffer(room, "Task");
      expert.rejectOffer(room, "Task");
      expert2.acceptOffer(room, "Task");

      client.receiveStart(expert2);
      expert2.sendAnswer(room, "Answer");
      client.receiveAnswer(expert2, "Answer");
    }};
  }

  @Test
  public void testClientReceivesAnswerAfterTwoExpertsRejectInvite() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert1");
      final Expert expert2 = registerExpert("expert2");
      final Expert expert3 = registerExpert("expert3");

      client.goOnline();
      final Room room = client.query("Task");

      expert.goOnline();
      expert2.goOnline();
      expert3.goOnline();

      expert.receiveOffer(room, "Task");
      expert2.receiveOffer(room, "Task");
      expert3.receiveOffer(room, "Task");
      expert.rejectOffer(room, "Task");
      expert3.rejectOffer(room, "Task");
      expert2.acceptOffer(room, "Task");

      client.receiveStart(expert2);
      expert2.sendAnswer(room, "Answer");
      client.receiveAnswer(expert2, "Answer");
    }};
  }

  @Test
  public void testClientReceivesAnswerAfterTwoExpertsRejectInvite2() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert1");
      final Expert expert2 = registerExpert("expert2");
      final Expert expert3 = registerExpert("expert3");

      client.goOnline();
      final Room room = client.query("Task");

      expert.goOnline();
      expert2.goOnline();

      expert.receiveOffer(room, "Task");
      expert2.receiveOffer(room, "Task");
      expert.rejectOffer(room, "Task");
      expert2.rejectOffer(room, "Task");

      expert3.goOnline();
      expert3.receiveOffer(room, "Task");
      expert3.acceptOffer(room, "Task");

      client.receiveStart(expert3);
      expert3.sendAnswer(room, "Answer");
      client.receiveAnswer(expert3, "Answer");
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

  @Test
  public void testExpertResumeDuringInvite() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert1");

      client.goOnline();
      final Room room = client.query("Task");
      expert.goOnline();
      expert.receiveOffer(room, "Task");

      expert.goOffline();
      expert.goOnline();
      expert.acceptOffer(room, "Task");
      expert.sendAnswer(room, "Answer");

      client.goOnline();
      client.receiveAnswer(expert, "Answer");
    }};
  }

  @Test
  public void testExpertResumeDuringTask() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert1");

      client.goOnline();
      final Room room = client.query("Task");
      expert.goOnline();
      expert.receiveOffer(room, "Task");
      expert.acceptOffer(room, "Task");
      client.receiveStart(expert);

      expert.goOffline();
      expert.goOnline();
      expert.sendAnswer(room, "Answer");

      client.goOnline();
      client.receiveAnswer(expert, "Answer");
    }};
  }

  /*@Test
  public void testClientRecieveAnswerAfterExpertChange() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert1");
      final Expert expert2 = registerExpert("expert2");

      expert.goOnline();
      expert2.goOnline();
      client.goOnline();
      final Room room = client.query("Task");

      expert.receiveOffer(room, "Task");
      expert2.receiveOffer(room, "Task");
      expert.acceptOffer(room, "Task");
      client.receiveStart(expert);

      expert.rejectTask(room, "Task");
      expert2.receiveOffer(room, "Task");
      expert2.acceptOffer(room, "Task");
      client.receiveStart(expert2);

      expert2.sendAnswer(room, "Answer");
      client.receiveAnswer(expert2, "Answer");
    }};
  }

  @Test
  public void testClientRecieveAnswerAfterExpertChangeTwice() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert1");
      final Expert expert2 = registerExpert("expert2");
      final Expert expert3 = registerExpert("expert3");

      expert.goOnline();
      expert2.goOnline();
      client.goOnline();
      final Room room = client.query("Task");

      expert.receiveOffer(room, "Task");
      expert2.receiveOffer(room, "Task");
      expert.acceptOffer(room, "Task");
      client.receiveStart(expert);

      expert.rejectTask(room, "Task");
      expert2.receiveOffer(room, "Task");
      expert2.rejectOffer(room, "Task");
      expert3.goOnline();
      expert3.receiveOffer(room, "Task");
      expert3.acceptOffer(room, "Task");

      client.receiveStart(expert3);

      expert3.sendAnswer(room, "Answer");
      client.receiveAnswer(expert3, "Answer");
    }};
  }*/


}
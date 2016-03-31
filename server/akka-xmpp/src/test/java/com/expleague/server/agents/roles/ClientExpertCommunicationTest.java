package com.expleague.server.agents.roles;

import com.expleague.model.Offer;
import com.spbsu.commons.util.Pair;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

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
      client.query("Task");
      expert.goOnline();
      final Offer offer = expert.receiveOffer("Task");
      expert.acceptOffer(offer);
      client.receiveStart(expert);
      expert.sendAnswer(offer, "Answer");
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
      client.query("Task");
      final Offer offer = expert.receiveOffer("Task");
      expert.acceptOffer(offer);
      client.receiveStart(expert);
      expert.sendAnswer(offer, "Answer");
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
      client.query("Task1");
      client.query("Task2");

      final Offer offer1 = expert.receiveOffer();
      expert.acceptOffer(offer1);
      client.receiveStart(expert);
      expert.sendAnswer(offer1, "Answer1");
      client.receiveAnswer(expert, "Answer1");

      final Offer offer2 = expert.receiveOffer(offer1);
      expert.acceptOffer(offer2);
      client.receiveStart(expert);
      expert.sendAnswer(offer2, "Answer2");
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
      client.query("Task1");
      client.query("Task2");
      client.goOffline();

      final Offer offer = expert.receiveOffer();
      expert.acceptOffer(offer);
      expert.sendAnswer(offer, "Answer1");

      final Offer offer2 = expert.receiveOffer(offer);
      expert.acceptOffer(offer2);
      expert.sendAnswer(offer2, "Answer2");

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
      client.query("Task1");
      client.query("Task2");
      client.goOffline();

      final Offer offer = expert.receiveOffer();
      expert.acceptOffer(offer);
      expert.sendAnswer(offer, "Answer1");

      client.goOnline();
      client.receiveStart(expert);
      client.receiveAnswer(expert, "Answer1");
      client.goOffline();

      final Offer offer2 = expert.receiveOffer(offer);
      expert.acceptOffer(offer2);
      expert.sendAnswer(offer2, "Answer2");

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
      client.query("Task1");
      client.query("Task2");
      client.goOffline();

      final Offer offer = expert.receiveOffer();
      expert.acceptOffer(offer);
      expert.sendAnswer(offer, "Answer1");

      client.goOnline();
      client.receiveStart(expert);
      client.receiveAnswer(expert, "Answer1");
      final Offer offer2 = expert.receiveOffer(offer);
      expert.acceptOffer(offer2);
      client.receiveStart(expert);

      client.goOffline();
      expert.sendAnswer(offer2, "Answer2");

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
      client.query("Task1");
      client.query("Task2");

      final Offer offer = expert.receiveOffer();
      expert.acceptOffer(offer);
      client.receiveStart(expert);

      final Offer offer2 = expert2.receiveOffer(offer);
      expert2.acceptOffer(offer2);
      client.receiveStart(expert2);

      expert.sendAnswer(offer, "Answer1");
      client.receiveAnswer(expert, "Answer1");

      expert2.sendAnswer(offer2, "Answer2");
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
      client.query("Task1");
      client.query("Task2");
      client.goOffline();

      final Offer offer = expert.receiveOffer();
      expert.acceptOffer(offer);

      final Offer offer2 = expert2.receiveOffer(offer);
      expert2.acceptOffer(offer2);

      expert.sendAnswer(offer.room(), "Answer1");
      expert2.sendAnswer(offer2.room(), "Answer2");

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
      client.query("Task1");
      client.query("Task2");
      client.goOffline();

      expert.passCheck();
      expert2.passCheck();

      final Offer offer1 = expert.receiveOffer();
      expert.acceptOffer(offer1);
      expert.sendAnswer(offer1.room(), "Answer1");

      client.goOnline();
      client.receiveStart(expert);
      client.receiveAnswer(expert, "Answer1");
      client.goOffline();

      final Offer offer2 = expert2.receiveOffer(offer1);
      expert2.acceptOffer(offer2);
      expert2.sendAnswer(offer2.room(), "Answer2");

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
      client.query("Task1");
      client.query("Task2");
      client.goOffline();

      final Offer offer1 = expert.receiveOffer();
      expert.acceptOffer(offer1);
      expert.sendAnswer(offer1.room(), "Answer1");

      client.goOnline();
      client.receiveStart(expert);
      client.receiveAnswer(expert, "Answer1");
      final Offer offer2 = expert2.receiveOffer(offer1);
      expert2.acceptOffer(offer2);
      client.receiveStart(expert2);
      client.goOffline();

      expert2.sendAnswer(offer2.room(), "Answer2");
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
      client.query("Task");

      expert.goOnline();
      final Offer offer = expert.receiveOffer();
      expert.rejectOffer(offer);

      expert2.goOnline();
      final Offer offer2 = expert2.receiveOffer();
      expert2.acceptOffer(offer2);

      client.receiveStart(expert2);
      expert2.sendAnswer(offer2, "Answer");
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
      client.query("Task");

      expert.goOnline();
      expert2.goOnline();

      expert.receiveOffer("Task");
      final Offer offer2 = expert2.receiveOffer("Task");

      expert2.acceptOffer(offer2);

      client.receiveStart(expert2);
      expert2.sendAnswer(offer2, "Answer");
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
      client.query("Task");

      expert.goOnline();
      expert2.goOnline();
      expert3.goOnline();

      expert.receiveOffer("Task");
      final Offer offer2 = expert2.receiveOffer("Task");
      expert3.receiveOffer("Task");

      expert2.acceptOffer(offer2);

      client.receiveStart(expert2);
      expert2.sendAnswer(offer2, "Answer");
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
      client.query("Task");

      expert.receiveOffer("Task");
      final Offer offer2 = expert2.receiveOffer("Task");
      expert3.receiveOffer("Task");

      expert2.acceptOffer(offer2);

      client.receiveStart(expert2);
      expert2.sendAnswer(offer2, "Answer");
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
      client.query("Task");
      expert2.goOnline();
      expert3.goOnline();

      expert.receiveOffer("Task");
      final Offer offer = expert2.receiveOffer("Task");
      expert3.receiveOffer("Task");

      expert2.acceptOffer(offer);

      client.receiveStart(expert2);
      expert2.sendAnswer(offer, "Answer");
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
      client.query("Task");
      expert.goOnline();
      expert2.goOnline();
      expert3.goOnline();

      expert.receiveOffer("Task");
      final Offer offer2 = expert2.receiveOffer("Task");
      expert3.receiveOffer("Task");

      expert4.goOnline();
      expert4.passCheck();
      expert5.goOnline();
      expert5.passCheck();
      expert6.goOnline();
      expert6.passCheck();
      client.query("Task2");
      expert7.goOnline();
      final Offer offer = expert7.receiveOffer("Task2");

      expert7.acceptOffer(offer);
      client.receiveStart(expert7);
      expert2.acceptOffer(offer2);
      client.receiveStart(expert2);

      expert2.sendAnswer(offer2, "Answer");
      client.receiveAnswer(expert2, "Answer");
      expert7.sendAnswer(offer, "Answer2");
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
      client.query("Task");
      expert.goOnline();
      expert2.goOnline();
      expert3.goOnline();

      expert.receiveOffer("Task");
      final Offer offer2 = expert2.receiveOffer("Task");
      expert3.receiveOffer("Task");

      expert4.goOnline();
      expert4.passCheck();

      expert5.goOnline();
      expert5.passCheck();

      expert6.goOnline();
      expert6.passCheck();

      client.query("Task2");
      expert7.goOnline();
      final Offer offer7 = expert7.receiveOffer("Task2");
      expert7.acceptOffer(offer7);
      client.receiveStart(expert7);

      expert4.goOffline();
      expert5.goOffline();
      expert6.goOffline();

      client.query("Task3");

      expert2.acceptOffer(offer2);
      client.receiveStart(expert2);

      expert.receiveOffer("Task3");
      final Offer offer3 = expert3.receiveOffer("Task3");

      expert3.acceptOffer(offer3);
      client.receiveStart(expert3);

      expert2.sendAnswer(offer2, "Answer");
      client.receiveAnswer(expert2, "Answer");
      expert7.sendAnswer(offer7, "Answer2");
      client.receiveAnswer(expert7, "Answer2");
      expert3.sendAnswer(offer3, "Answer3");
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
      client.query("Task");
      expert.goOnline();
      expert2.goOnline();
      expert3.goOnline();

      expert.receiveOffer("Task");
      final Offer offer = expert2.receiveOffer("Task");
      expert3.receiveOffer("Task");

      expert4.goOnline();
      expert4.passCheck();

      expert5.goOnline();
      expert5.passCheck();

      expert6.goOnline();
      expert6.passCheck();

      client.query("Task2");
      expert7.goOnline();
      final Offer task2 = expert7.receiveOffer("Task2");
      expert7.acceptOffer(task2);
      client.receiveStart(expert7);

      expert.goOffline();
      expert3.goOffline();
      expert4.goOffline();

      expert5.receiveOffer("Task");
      expert6.receiveOffer("Task");

      expert2.acceptOffer(offer);
      client.receiveStart(expert2);

      client.query("Task3");
      final Offer task3 = expert5.receiveOffer("Task3");
      expert6.receiveOffer("Task3");

      expert5.acceptOffer(task3);
      client.receiveStart(expert5);

      expert2.sendAnswer(offer, "Answer");
      client.receiveAnswer(expert2, "Answer");
      expert7.sendAnswer(task2, "Answer2");
      client.receiveAnswer(expert7, "Answer2");
      expert5.sendAnswer(task3, "Answer3");
      client.receiveAnswer(expert5, "Answer3");

    }};
  }

  @Test
  public void testExpertRecieveNextOfferAfterReturningOnlineTwoRooms() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert1");
      final Expert expert2 = registerExpert("expert2");
      final Expert expert3 = registerExpert("expert3");
      expert.goOnline();
      expert2.goOnline();
      expert3.goOnline();
      client.goOnline();

      client.query("Task");

      expert.passCheck();
      expert2.passCheck();
      expert3.passCheck();

      expert.receiveOffer("Task");
      final Offer task = expert2.receiveOffer("Task");
      expert3.receiveOffer("Task");

      expert3.goOffline();

      client.query("Task2");
      expert2.acceptOffer(task);
      client.receiveStart(expert2);

      expert3.goOnline();
      final Offer task2 = expert.receiveOffer("Task2");
      expert3.receiveOffer("Task2");
      expert.acceptOffer(task2);
      client.receiveStart(expert);

      expert2.sendAnswer(task, "Answer1");
      expert.sendAnswer(task2, "Answer2");
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
      client.query("Task");

      expert.goOnline();
      expert2.goOnline();

      final Offer offer = expert.receiveOffer("Task");
      final Offer offer2 = expert2.receiveOffer("Task");
      expert.rejectOffer(offer);
      expert2.acceptOffer(offer2);

      client.receiveStart(expert2);
      expert2.sendAnswer(offer2, "Answer");
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
      client.query("Task");

      expert.goOnline();
      expert.passCheck();
      expert2.goOnline();
      expert2.passCheck();
      expert3.goOnline();
      expert3.passCheck();

      final Offer offer = expert.receiveOffer("Task");
      final Offer offer2 = expert2.receiveOffer("Task");
      final Offer offer3 = expert3.receiveOffer("Task");
      expert.rejectOffer(offer);
      expert3.rejectOffer(offer3);
      expert2.acceptOffer(offer2);

      client.receiveStart(expert2);
      expert2.sendAnswer(offer2, "Answer");
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
      client.query("Task");

      expert.goOnline();
      expert2.goOnline();

      final Offer offer = expert.receiveOffer("Task");
      final Offer offer2 = expert2.receiveOffer("Task");
      expert.rejectOffer(offer);
      expert2.rejectOffer(offer2);

      expert3.goOnline();
      final Offer offer3 = expert3.receiveOffer("Task");
      expert3.acceptOffer(offer3);

      client.receiveStart(expert3);
      expert3.sendAnswer(offer3, "Answer");
      client.receiveAnswer(expert3, "Answer");
    }};
  }

  @Test
  public void testClientReceivesAnswerWhenBecameOnline() throws Exception {
    new ScenarioTestKit() {{
      final Client client = registerClient("client");
      final Expert expert = registerExpert("expert1");

      client.goOnline();
      client.query("Task");
      client.goOffline();

      expert.goOnline();
      final Offer task = expert.receiveOffer("Task");
      expert.acceptOffer(task);
      expert.sendAnswer(task, "Answer");

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
      client.query("Task");
      expert.goOnline();
      expert.passCheck();
      final Offer task = expert.receiveOffer("Task");
      expert.acceptOffer(task);

      expert.goOffline();
      expert.goOnline();
      expert.resumeOffer(task);
      expert.sendAnswer(task, "Answer");

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
      client.query("Task");
      expert.goOnline();
      final Offer task = expert.receiveOffer("Task");
      expert.acceptOffer(task);
      client.receiveStart(expert);

      expert.goOffline();
      expert.goOnline();
      expert.sendAnswer(task, "Answer");

      client.goOnline();
      client.receiveAnswer(expert, "Answer");
    }};
  }

  @Test
  @Ignore /*this is stress test*/
  public void testManyClientsAskManyQueries() throws Exception {
    new ScenarioTestKit() {{
      final int numberOfClients = 100;
      final Client[] clients = new Client[numberOfClients];
      for (int i = 0; i < clients.length; i++) {
        clients[i] = registerClient("client" + i);
      }

      final int numberOfExperts = 3;
      final Expert[] experts = new Expert[numberOfExperts];
      for (int i = 0; i < experts.length; i++) {
        experts[i] = registerExpert("expert" + i);
      }

      for (Expert expert : experts) {
        expert.goOnline();
      }

      final Set<String> tasks = new HashSet<>();

      final int numberOfTasksPerClient = 2;
      for (Client client : clients) {
        client.goOnline();
        for (int i = 0; i < numberOfTasksPerClient; i++) {
          final String task = "Task-" + client.getJid().bare().local() + "-" + i;
          client.query(task);
          tasks.add(task);
        }
        client.goOffline();
      }


      final Set<Offer> receivedOffers = new HashSet<>();
      final ExpertsGroup expertsGroup = new ExpertsGroup(experts);
      while (receivedOffers.size() != tasks.size()) {
        final Pair<Expert, Offer> expertOfferPair = expertsGroup.receiveOffer(offer -> !receivedOffers.contains(offer));
        System.out.println("Offer received");
        final Expert expert = expertOfferPair.getFirst();
        final Offer offer = expertOfferPair.getSecond();
        receivedOffers.add(offer);

        expert.acceptOffer(offer);
        expert.sendAnswer(offer, offer.topic());
      }
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
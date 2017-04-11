package integration_tests.tests;

import com.expleague.bots.AdminBot;
import com.expleague.bots.ClientBot;
import com.expleague.bots.ExpertBot;
import com.expleague.bots.utils.ExpectedMessage;
import com.expleague.bots.utils.ExpectedMessageBuilder;
import com.expleague.model.*;
import com.expleague.xmpp.JID;
import com.spbsu.commons.util.sync.StateLatch;
import integration_tests.BaseRoomTest;
import org.junit.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;

import java.util.Collections;

/**
 * User: Artem
 * Date: 06.04.2017
 * Time: 18:34
 */
public class ExpertCancelsTest extends BaseRoomTest {

  @Test
  public void testOneExpertCancelsInWorkStateAnotherAnswers() throws JaxmppException {
    //Arrange
    final AdminBot adminBot = botsManager.nextAdmin();
    final ExpertBot firstExpertBot = botsManager.nextExpert();
    final ExpertBot secondExpertBot = botsManager.nextExpert();
    final ClientBot clientBot = botsManager.nextClient();
    final BareJID roomJID = obtainRoomWorkState(testName(), clientBot, adminBot, firstExpertBot, secondExpertBot);

    final ExpectedMessage invite = new ExpectedMessageBuilder().from(roomJID).has(Offer.class).has(Operations.Invite.class).build();
    final ExpectedMessage cancel = new ExpectedMessageBuilder().from(botRoomJID(roomJID, firstExpertBot)).has(Operations.Cancel.class).build();
    final ExpectedMessage startAndExpert = new ExpectedMessageBuilder().from(roomJID).has(Operations.Start.class).has(ExpertsProfile.class).build();

    final Answer answer = new Answer(generateRandomString());
    final ExpectedMessage answerModel = new ExpectedMessageBuilder()
        .from(botRoomJID(roomJID, secondExpertBot))
        .has(Answer.class, a -> answer.value().equals(a.value()))
        .build();

    //Act
    firstExpertBot.sendGroupchat(roomJID, new Operations.Ok());
    secondExpertBot.sendGroupchat(roomJID, new Operations.Ok());
    //Assert
    assertAllExpectedMessagesAreReceived(firstExpertBot.tryReceiveMessages(new StateLatch(), invite));
    assertAllExpectedMessagesAreReceived(secondExpertBot.tryReceiveMessages(new StateLatch(), invite.copy()));

    //Act
    firstExpertBot.sendGroupchat(roomJID, new Operations.Cancel());
    //Assert
    assertAllExpectedMessagesAreReceived(adminBot.tryReceiveMessages(new StateLatch(), cancel));

    //Act
    secondExpertBot.sendGroupchat(roomJID, new Operations.Start());
    //Assert
    assertAllExpectedMessagesAreReceived(clientBot.tryReceiveMessages(new StateLatch(), startAndExpert));

    //Act
    secondExpertBot.sendGroupchat(roomJID, answer);
    //Assert
    assertAllExpectedMessagesAreReceived(clientBot.tryReceiveMessages(new StateLatch(), answerModel));

    //Act/Assert
    roomCloseStateByClientFeedback(roomJID, clientBot, adminBot);
  }

  /*@Test
  public void testOneExpertCancelsInDeliverStateAnotherAnswers() throws JaxmppException {
    //Arrange
    final AdminBot adminBot = botsManager.nextAdmin();
    final ExpertBot firstExpertBot = botsManager.nextExpert();
    final ExpertBot secondExpertBot = botsManager.nextExpert();
    final ClientBot clientBot = botsManager.nextClient();
    final BareJID roomJID = obtainRoomWorkState(testName(), clientBot, adminBot, firstExpertBot, secondExpertBot);

    final ExpectedMessage invite = new ExpectedMessageBuilder().from(roomJID).has(Offer.class).has(Operations.Invite.class).build();
    final ExpectedMessage cancel = new ExpectedMessageBuilder().from(botRoomJID(roomJID, firstExpertBot)).has(Operations.Cancel.class).build();
    final ExpectedMessage startAndExpert = new ExpectedMessageBuilder().from(roomJID).has(Operations.Start.class).has(ExpertsProfile.class).build();

    final Answer answer = new Answer(generateRandomString());
    final ExpectedMessage answerModel = new ExpectedMessageBuilder()
        .from(botRoomJID(roomJID, secondExpertBot))
        .has(Answer.class, a -> answer.value().equals(a.value()))
        .build();

    //Act
    firstExpertBot.sendGroupchat(roomJID, new Operations.Ok());
    //Assert
    assertAllExpectedMessagesAreReceived(firstExpertBot.tryReceiveMessages(new StateLatch(), invite));

    //Act
    firstExpertBot.sendGroupchat(roomJID, new Operations.Start());
    //Assert
    assertAllExpectedMessagesAreReceived(clientBot.tryReceiveMessages(new StateLatch(), startAndExpert));

    //Act
    firstExpertBot.sendGroupchat(roomJID, new Operations.Cancel());
    //Assert
    assertAllExpectedMessagesAreReceived(clientBot.tryReceiveMessages(new StateLatch(), cancel));

    //Act/Assert
    roomCloseStateByClientFeedback(roomJID, clientBot, adminBot);
  }*/

  @Test
  public void testChosenExpertCancels() throws JaxmppException {
    //Arrange
    final AdminBot adminBot = botsManager.nextAdmin();
    final ExpertBot firstExpertBot = botsManager.nextExpert();
    final ExpertBot secondExpertBot = botsManager.nextExpert();
    final ClientBot clientBot = botsManager.nextClient();
    final BareJID roomJID = obtainRoomOpenState(testName(), clientBot, adminBot);

    final Filter expertFilter = new Filter(Collections.singletonList(JID.parse(firstExpertBot.jid().toString())), null, null);
    final Offer offer = new Offer(JID.parse(roomJID.toString()), expertFilter);

    final ExpectedMessage offerCheck = new ExpectedMessageBuilder().from(domainJID()).has(Offer.class).has(Operations.Check.class).build();
    final ExpectedMessage invite = new ExpectedMessageBuilder().from(roomJID).has(Offer.class).has(Operations.Invite.class).build();
    final ExpectedMessage cancelByRoom = new ExpectedMessageBuilder().from(roomJID).has(Operations.Cancel.class).build();
    final ExpectedMessage cancelByFirstExpert = new ExpectedMessageBuilder().from(botRoomJID(roomJID, firstExpertBot)).has(Operations.Cancel.class).build();

    //Act
    adminBot.send(roomJID, offer);
    //Assert
    assertAllExpectedMessagesAreReceived(firstExpertBot.tryReceiveMessages(new StateLatch(), offerCheck));
    // TODO: should be no checks from room, need to figure this out
//    assertAllExpectedMessagesAreReceived(secondExpertBot.tryReceiveMessages(new StateLatch(), offerCheck.copy()));

    //Act
    firstExpertBot.sendGroupchat(roomJID, new Operations.Ok());
//    secondExpertBot.sendGroupchat(roomJID, new Operations.Ok());
    //Assert
    assertAllExpectedMessagesAreReceived(firstExpertBot.tryReceiveMessages(new StateLatch(), invite));
//    assertAllExpectedMessagesAreReceived(secondExpertBot.tryReceiveMessages(new StateLatch(), cancelByRoom));

    // TODO: the following is wrong, need to rework this test
    //Act
    firstExpertBot.sendGroupchat(roomJID, new Operations.Cancel());
    //Assert
    assertAllExpectedMessagesAreReceived(adminBot.tryReceiveMessages(new StateLatch(), cancelByFirstExpert));
//    assertAllExpectedMessagesAreReceived(secondExpertBot.tryReceiveMessages(new StateLatch(), offerCheck.copy()));

    //Act
//    secondExpertBot.sendGroupchat(roomJID, new Operations.Ok());
//    assertAllExpectedMessagesAreReceived(secondExpertBot.tryReceiveMessages(new StateLatch(), cancelByRoom.copy()));
  }
}

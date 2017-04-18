package integration_tests.tests;

import com.expleague.bots.AdminBot;
import com.expleague.bots.ClientBot;
import com.expleague.bots.ExpertBot;
import com.expleague.bots.utils.ReceivingMessage;
import com.expleague.bots.utils.ReceivingMessageBuilder;
import com.expleague.model.*;
import com.expleague.xmpp.JID;
import com.spbsu.commons.util.sync.StateLatch;
import integration_tests.BaseRoomTest;
import org.junit.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.expleague.bots.utils.FunctionalUtils.throwableConsumer;
import static com.expleague.bots.utils.FunctionalUtils.throwableSupplier;

/**
 * User: Artem
 * Date: 18.04.2017
 * Time: 19:41
 */
public class ExpertsAssignmentTest extends BaseRoomTest {

  @Test
  public void testTaskToAnyExpert() throws JaxmppException {
    //Arrange
    final int expertsNum = 3;
    final ExpertBot[] expertBots = Stream.generate(throwableSupplier(() -> botsManager.nextExpert())).limit(expertsNum).toArray(ExpertBot[]::new);
    final ExpertBot defaultExpert = expertBots[0];
    final AdminBot adminBot = botsManager.nextAdmin();
    final ClientBot clientBot = botsManager.nextClient();
    final BareJID roomJID = obtainRoomWorkState(testName(), clientBot, adminBot, expertBots);

    //Act/Assert
    checkDefaultExpertAnswersAndOthersDoNot(roomJID, defaultExpert, expertBots, clientBot);
  }

  @Test
  public void testTaskToSpecifiedExpert() throws JaxmppException {
    checkTaskToSeveralSpecifiedExperts(1, 3);
  }

  @Test
  public void testTaskToSeveralSpecifiedExperts() throws JaxmppException {
    checkTaskToSeveralSpecifiedExperts(3, 3);
  }

  private void checkTaskToSeveralSpecifiedExperts(int acceptedExpertNum, int extraExpertsNum) throws JaxmppException {
    //Arrange
    final ExpertBot[] acceptedExperts = Stream.generate(throwableSupplier(() -> botsManager.nextExpert())).limit(acceptedExpertNum).toArray(ExpertBot[]::new);
    final ExpertBot defaultExpert = acceptedExperts[0];
    final ExpertBot[] extraExperts = Stream.generate(throwableSupplier(() -> botsManager.nextExpert())).limit(extraExpertsNum).toArray(ExpertBot[]::new);
    final AdminBot adminBot = botsManager.nextAdmin();
    final ClientBot clientBot = botsManager.nextClient();
    final BareJID roomJID = obtainRoomOpenState(testName(), clientBot, adminBot);

    final Filter expertFilter = new Filter(Arrays.stream(acceptedExperts).map(expertBot -> JID.parse(expertBot.jid().toString())).collect(Collectors.toList()), null, null);
    final Offer offer = new Offer(JID.parse(roomJID.toString()), expertFilter);
    final ReceivingMessageBuilder offerCheck = new ReceivingMessageBuilder().from(domainJID()).has(Offer.class).has(Operations.Check.class);
    final ReceivingMessageBuilder sync = new ReceivingMessageBuilder().has(Operations.Sync.class);
    final ReceivingMessageBuilder cancel = new ReceivingMessageBuilder().from(roomJID).has(Operations.Cancel.class);

    //Act
    adminBot.send(roomJID, offer);
    //Assert
    Arrays.stream(acceptedExperts).forEach(throwableConsumer(expertBot -> assertThereAreNoFailedMessages(expertBot.tryReceiveMessages(new StateLatch(), offerCheck.build()))));

    { //Check other bots are not received invite
      Arrays.stream(extraExperts).filter(expertBot -> !expertBot.equals(defaultExpert)).forEach(throwableConsumer(expertBot -> {
        //Act
        adminBot.send(expertBot.jid(), new Operations.Sync());
        //Assert
        assertThereAreNoFailedMessages(expertBot.tryReceiveMessages(new StateLatch(), sync.build()));

        if (expertBot.offerCheckReceivedAndReset()) {
          //Act
          expertBot.sendGroupchat(roomJID, new Operations.Ok());
          //Assert
          assertThereAreNoFailedMessages(expertBot.tryReceiveMessages(new StateLatch(), cancel.build()));
        }
      }));
    }

    //Act/Assert
    checkDefaultExpertAnswersAndOthersDoNot(roomJID, defaultExpert, acceptedExperts, clientBot);
  }

  private void checkDefaultExpertAnswersAndOthersDoNot(BareJID roomJID, ExpertBot defaultExpert, ExpertBot[] expertBots, ClientBot clientBot) throws JaxmppException {
    //Arrange
    final Answer answer = new Answer(generateRandomString());
    final ReceivingMessage expectedAnswer = new ReceivingMessageBuilder().from(botRoomJID(roomJID, defaultExpert)).has(Answer.class, a -> answer.value().equals(a.value())).build();
    final ReceivingMessageBuilder invite = new ReceivingMessageBuilder().from(roomJID).has(Offer.class).has(Operations.Invite.class);
    final ReceivingMessage startAndExpert = new ReceivingMessageBuilder().from(roomJID).has(Operations.Start.class).has(ExpertsProfile.class).build();
    final ReceivingMessageBuilder cancelOffer = new ReceivingMessageBuilder().from(roomJID).has(Offer.class).has(Operations.Cancel.class);

    //Act
    Arrays.stream(expertBots).forEach(throwableConsumer(expertBot -> expertBot.sendGroupchat(roomJID, new Operations.Ok())));
    //Assert
    Arrays.stream(expertBots).forEach(throwableConsumer(expertBot -> assertThereAreNoFailedMessages(expertBot.tryReceiveMessages(new StateLatch(), invite.build()))));

    //Act
    defaultExpert.sendGroupchat(roomJID, new Operations.Start());
    //Assert
    assertThereAreNoFailedMessages(clientBot.tryReceiveMessages(new StateLatch(), startAndExpert));
    Arrays.stream(expertBots).filter(expertBot -> !expertBot.equals(defaultExpert)).forEach(throwableConsumer(expertBot -> assertThereAreNoFailedMessages(expertBot.tryReceiveMessages(new StateLatch(), cancelOffer.build()))));

    //Act
    defaultExpert.sendGroupchat(roomJID, answer);
    //Assert
    assertThereAreNoFailedMessages(clientBot.tryReceiveMessages(new StateLatch(), expectedAnswer));
  }
}

package integration_tests.tests;

import com.expleague.bots.AdminBot;
import com.expleague.bots.Bot;
import com.expleague.bots.ClientBot;
import com.expleague.bots.ExpertBot;
import com.expleague.bots.utils.ReceivingMessage;
import com.expleague.bots.utils.ReceivingMessageBuilder;
import com.expleague.model.*;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
import com.spbsu.commons.util.sync.StateLatch;
import integration_tests.BaseRoomTest;
import org.junit.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;

import java.util.Arrays;
import java.util.Collections;
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
    checkDefaultExpertAnswersAndOthersDoNot(roomJID, defaultExpert, Stream.of(expertBots).filter(expertBot -> !defaultExpert.equals(expertBot)).toArray(ExpertBot[]::new), clientBot);
  }

  @Test
  public void testTaskToSpecifiedExpert() throws JaxmppException, InterruptedException {
    checkTaskToSeveralSpecifiedExperts(1, 3);
  }

  @Test
  public void testTaskToSeveralSpecifiedExperts() throws JaxmppException, InterruptedException {
    checkTaskToSeveralSpecifiedExperts(3, 3);
  }

  @Test
  public void testTaskToPreferredExpert() throws JaxmppException, InterruptedException {
    //Arrange
    final int expertsNum = 4;
    final ExpertBot[] expertBots = Stream.generate(throwableSupplier(() -> botsManager.nextExpert())).limit(expertsNum).toArray(ExpertBot[]::new);
    final ExpertBot defaultExpert = expertBots[0];
    //final ExpertBot secondDefaultExpert = expertBots[1];
    final AdminBot adminBot = botsManager.nextAdmin();
    final ClientBot clientBot = botsManager.nextClient();

    final BareJID roomJID = obtainRoomOpenState(testName(), clientBot, adminBot);
    final Filter expertFilter = new Filter(null, null, Collections.singletonList(JID.parse(defaultExpert.jid().toString())));
    final Offer offer = new Offer(JID.parse(roomJID.toString()), expertFilter);
    final ReceivingMessage invite = new ReceivingMessageBuilder().from(roomJID).has(Offer.class).has(Operations.Invite.class).build();

    //Act
    adminBot.send(roomJID, offer);
    //Assert
    checkOtherExpertsAreNotReceivedInvite(roomJID, new ExpertBot[]{defaultExpert}, Stream.of(expertBots).filter(expertBot -> !defaultExpert.equals(expertBot)).toArray(ExpertBot[]::new));

    //Act
    defaultExpert.sendGroupchat(roomJID, new Operations.Ok());
    //Assert
    assertThereAreNoFailedMessages(defaultExpert.tryReceiveMessages(new StateLatch(), invite));

    //Currently, this functionality is changed
    /*//Act
    defaultExpert.offline();
    //Assert
    checkOtherExpertsAreNotReceivedInvite(roomJID, Stream.of(expertBots).filter(expertBot -> !defaultExpert.equals(expertBot)).toArray(ExpertBot[]::new), new ExpertBot[] {defaultExpert});

    //Act/Assert
    checkDefaultExpertAnswersAndOthersDoNot(roomJID, secondDefaultExpert, Stream.of(expertBots).filter(expertBot -> !defaultExpert.equals(expertBot) && !secondDefaultExpert.equals(expertBot)).toArray(ExpertBot[]::new), clientBot);*/
  }

  @Test
  public void testTaskToBannedExpert() throws JaxmppException, InterruptedException {
    //Arrange
    final int expertsNum = 4;
    final ExpertBot[] expertBots = Stream.generate(throwableSupplier(() -> botsManager.nextExpert())).limit(expertsNum / 2).toArray(ExpertBot[]::new);
    final ExpertBot defaultExpert = expertBots[0];
    final ExpertBot[] bannedExperts = Stream.generate(throwableSupplier(() -> botsManager.nextExpert())).limit(expertsNum / 2).toArray(ExpertBot[]::new);
    final AdminBot adminBot = botsManager.nextAdmin();
    final ClientBot clientBot = botsManager.nextClient();

    final BareJID roomJID = obtainRoomOpenState(testName(), clientBot, adminBot);
    final Filter expertFilter = new Filter(null, Stream.of(bannedExperts).map(expertBot -> JID.parse(expertBot.jid().toString())).collect(Collectors.toList()), null);
    final Offer offer = new Offer(JID.parse(roomJID.toString()), expertFilter);

    //Act
    adminBot.send(roomJID, offer);
    //Assert
    checkOtherExpertsAreNotReceivedInvite(roomJID, expertBots, bannedExperts);

    //Act/Assert
    checkDefaultExpertAnswersAndOthersDoNot(roomJID, defaultExpert, Stream.of(expertBots).filter(expertBot -> !defaultExpert.equals(expertBot)).toArray(ExpertBot[]::new), clientBot);
  }

  @Test
  public void testStatusChangedFromBanned() throws JaxmppException, InterruptedException {
    //Arrange
    final int expertsNum = 4;
    final ExpertBot[] expertBots = Stream.generate(throwableSupplier(() -> botsManager.nextExpert())).limit(expertsNum / 2).toArray(ExpertBot[]::new);
    final ExpertBot defaultExpert = expertBots[0];
    final ExpertBot[] bannedExperts = Stream.generate(throwableSupplier(() -> botsManager.nextExpert())).limit(expertsNum / 2).toArray(ExpertBot[]::new);
    final ExpertBot secondDefaultExpert = bannedExperts[0];
    final AdminBot adminBot = botsManager.nextAdmin();
    final ClientBot clientBot = botsManager.nextClient();

    final BareJID roomJID = obtainRoomOpenState(testName(), clientBot, adminBot);
    final Filter expertFilter = new Filter(null, Stream.of(bannedExperts).map(expertBot -> JID.parse(expertBot.jid().toString())).collect(Collectors.toList()), null);
    final Offer offer = new Offer(JID.parse(roomJID.toString()), expertFilter);

    final Filter newExpertFilter = new Filter(Stream.of(bannedExperts).map(expertBot -> JID.parse(expertBot.jid().toString())).collect(Collectors.toList()), null, null);
    final Offer newOffer = new Offer(JID.parse(roomJID.toString()), newExpertFilter);

    final Message.Body messageForReopen = new Message.Body(generateRandomString());
    final ReceivingMessageBuilder expectedMessage = new ReceivingMessageBuilder().from(botRoomJID(roomJID, clientBot)).has(Message.Body.class, b -> messageForReopen.value().equals(b.value()));

    //Act
    adminBot.send(roomJID, offer);
    //Assert
    checkOtherExpertsAreNotReceivedInvite(roomJID, expertBots, bannedExperts);

    //Act/Assert
    checkDefaultExpertAnswersAndOthersDoNot(roomJID, defaultExpert, Stream.of(expertBots).filter(expertBot -> !defaultExpert.equals(expertBot)).toArray(ExpertBot[]::new), clientBot);

    //Act
    clientBot.sendGroupchat(roomJID, messageForReopen);
    //Assert
    assertThereAreNoFailedMessages(adminBot.tryReceiveMessages(new StateLatch(), expectedMessage.build()));
    assertThereAreNoFailedMessages(defaultExpert.tryReceiveMessages(new StateLatch(), expectedMessage.build()));

    //Act
    Stream.of(expertBots).forEach(Bot::offerCheckReceivedAndReset);
    adminBot.send(roomJID, newOffer);
    //Assert
    checkOtherExpertsAreNotReceivedInvite(roomJID, bannedExperts, expertBots);

    //Act/Assert
    checkDefaultExpertAnswersAndOthersDoNot(roomJID, secondDefaultExpert, Stream.of(bannedExperts).filter(expertBot -> !secondDefaultExpert.equals(expertBot)).toArray(ExpertBot[]::new), clientBot);
  }

  private void checkTaskToSeveralSpecifiedExperts(int acceptedExpertNum, int extraExpertsNum) throws JaxmppException, InterruptedException {
    //Arrange
    final ExpertBot[] acceptedExperts = Stream.generate(throwableSupplier(() -> botsManager.nextExpert())).limit(acceptedExpertNum).toArray(ExpertBot[]::new);
    final ExpertBot defaultExpert = acceptedExperts[0];
    final ExpertBot[] extraExperts = Stream.generate(throwableSupplier(() -> botsManager.nextExpert())).limit(extraExpertsNum).toArray(ExpertBot[]::new);
    final AdminBot adminBot = botsManager.nextAdmin();
    final ClientBot clientBot = botsManager.nextClient();

    final BareJID roomJID = obtainRoomOpenState(testName(), clientBot, adminBot);
    final Filter expertFilter = new Filter(Arrays.stream(acceptedExperts).map(expertBot -> JID.parse(expertBot.jid().toString())).collect(Collectors.toList()), null, null);
    final Offer offer = new Offer(JID.parse(roomJID.toString()), expertFilter);

    //Act
    adminBot.send(roomJID, offer);
    //Act
    checkOtherExpertsAreNotReceivedInvite(roomJID, acceptedExperts, extraExperts);

    //Act/Assert
    checkDefaultExpertAnswersAndOthersDoNot(roomJID, defaultExpert, Stream.of(acceptedExperts).filter(expertBot -> !defaultExpert.equals(expertBot)).toArray(ExpertBot[]::new), clientBot);
  }

  private void checkDefaultExpertAnswersAndOthersDoNot(BareJID roomJID, ExpertBot defaultExpert, ExpertBot[] expertBots, ClientBot clientBot) throws JaxmppException {
    //Arrange
    final Answer answer = new Answer(generateRandomString());
    final ReceivingMessage expectedAnswer = new ReceivingMessageBuilder().from(botRoomJID(roomJID, defaultExpert)).has(Answer.class, a -> answer.value().equals(a.value())).build();
    final ReceivingMessageBuilder invite = new ReceivingMessageBuilder().from(roomJID).has(Offer.class).has(Operations.Invite.class);
    final ReceivingMessage startAndExpert = new ReceivingMessageBuilder().from(roomJID).has(Operations.Start.class).has(ExpertsProfile.class).build();
    final ReceivingMessageBuilder cancelOffer = new ReceivingMessageBuilder().from(roomJID).has(Offer.class).has(Operations.Cancel.class);

    //Act
    defaultExpert.sendGroupchat(roomJID, new Operations.Ok());
    Arrays.stream(expertBots).forEach(throwableConsumer(expertBot -> expertBot.sendGroupchat(roomJID, new Operations.Ok())));
    //Assert
    Arrays.stream(expertBots).forEach(throwableConsumer(expertBot -> assertThereAreNoFailedMessages(expertBot.tryReceiveMessages(new StateLatch(), invite.build()))));

    //Act
    defaultExpert.sendGroupchat(roomJID, new Operations.Start());
    //Assert
    assertThereAreNoFailedMessages(clientBot.tryReceiveMessages(new StateLatch(), startAndExpert));
    Arrays.stream(expertBots).forEach(throwableConsumer(expertBot -> assertThereAreNoFailedMessages(expertBot.tryReceiveMessages(new StateLatch(), cancelOffer.build()))));

    //Act
    defaultExpert.sendGroupchat(roomJID, answer);
    //Assert
    assertThereAreNoFailedMessages(clientBot.tryReceiveMessages(new StateLatch(), expectedAnswer));
  }

  private void checkOtherExpertsAreNotReceivedInvite(BareJID roomJID, ExpertBot[] acceptedExperts, ExpertBot[] extraExperts) throws JaxmppException, InterruptedException {
    //Arrange
    final ReceivingMessageBuilder offerCheck = new ReceivingMessageBuilder().from(domainJID()).has(Offer.class).has(Operations.Check.class);
    final ReceivingMessageBuilder cancel = new ReceivingMessageBuilder().from(roomJID).has(Operations.Cancel.class);

    //Assert
    Arrays.stream(acceptedExperts).forEach(throwableConsumer(expertBot -> assertThereAreNoFailedMessages(expertBot.tryReceiveMessages(new StateLatch(), offerCheck.build()))));
    Thread.sleep(SYNC_WAIT_TIMEOUT_IN_MILLIS); //wait because sync does not work in this case
    { //Check other bots are not received invite
      Arrays.stream(extraExperts).forEach(throwableConsumer(expertBot -> {
        if (expertBot.offerCheckReceivedAndReset()) {
          //Act
          expertBot.sendGroupchat(roomJID, new Operations.Ok());
          //Assert
          assertThereAreNoFailedMessages(expertBot.tryReceiveMessages(new StateLatch(), cancel.build()));
        }
      }));
    }
  }
}

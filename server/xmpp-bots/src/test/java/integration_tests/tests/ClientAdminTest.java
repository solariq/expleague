package integration_tests.tests;

import com.expleague.bots.AdminBot;
import com.expleague.bots.ClientBot;
import com.expleague.bots.ExpertBot;
import com.expleague.bots.utils.ExpectedMessage;
import com.expleague.bots.utils.ExpectedMessageBuilder;
import com.expleague.model.Answer;
import com.expleague.model.Operations;
import com.expleague.xmpp.stanza.Message;
import com.spbsu.commons.util.Pair;
import com.spbsu.commons.util.sync.StateLatch;
import integration_tests.BaseSingleBotsTest;
import org.junit.Test;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.expleague.bots.utils.FunctionalUtils.throwableConsumer;
import static com.expleague.bots.utils.FunctionalUtils.throwableSupplier;

/**
 * User: Artem
 * Date: 15.02.2017
 * Time: 11:51
 */
public class ClientAdminTest extends BaseSingleBotsTest {

  @Test
  public void testAdminHandlesMultipleRooms() throws JaxmppException {
    //Arrange
    botsManager.addBots(1, 1, 0);
    botsManager.startAll();
    final ClientBot clientBot = botsManager.defaultClientBot();
    final AdminBot adminBot = botsManager.defaultAdminBot();

    final int roomCount = 3;
    final List<BareJID> rooms = Stream.generate(throwableSupplier(() -> obtainRoomOpenState(clientBot, adminBot))).limit(roomCount).collect(Collectors.toList());
    final Operations.Progress.MetaChange.Target[] targets = Operations.Progress.MetaChange.Target.values();

    final List<Pair<BareJID, Operations.Progress>> progresses = rooms.stream().flatMap(roomJID -> Arrays.stream(targets).map(target -> new Pair<>(roomJID,
        new Operations.Progress(
            generateRandomString(),
            new Operations.Progress.MetaChange(generateRandomString(),
                Operations.Progress.MetaChange.Operation.ADD,
                target)))))
        .collect(Collectors.toList());
    final ExpectedMessage[] expectedProgresses = progresses.stream().map(roomProgress -> new ExpectedMessageBuilder()
        .from(roomProgress.first)
        .has(Operations.Progress.class,
            p -> roomProgress.second.meta().name().equals(p.meta().name()) &&
                roomProgress.second.meta().operation() == p.meta().operation() &&
                roomProgress.second.meta().target() == p.meta().target() &&
                roomProgress.second.order().equals(p.order()))
        .build()).toArray(ExpectedMessage[]::new);

    final List<Pair<BareJID, Message.Body>> messages = rooms.stream().map(roomJID -> new Pair<>(roomJID, new Message.Body(generateRandomString()))).collect(Collectors.toList());
    final ExpectedMessage[] expectedMessages = messages.stream().map(roomBody -> new ExpectedMessageBuilder()
        .from(botRoomJID(roomBody.first, adminBot))
        .has(Message.Body.class, body -> roomBody.second.value().equals(body.value()))
        .build()).toArray(ExpectedMessage[]::new);

    final List<Pair<BareJID, Answer>> answers = rooms.stream().map(roomJID -> new Pair<>(roomJID, new Answer(generateRandomString()))).collect(Collectors.toList());
    final ExpectedMessage[] expectedAnswers = answers.stream().map(roomAnswer -> new ExpectedMessageBuilder()
        .from(botRoomJID(roomAnswer.first, adminBot))
        .has(Answer.class, answer -> roomAnswer.second.value().equals(answer.value()))
        .build()).toArray(ExpectedMessage[]::new);

    //Act
    progresses.forEach(throwableConsumer(roomProgress -> adminBot.sendGroupchat(roomProgress.first, roomProgress.second)));
    final ExpectedMessage[] notReceivedProgress = clientBot.tryReceiveMessages(new StateLatch(), expectedProgresses);
    //Assert
    assertAllExpectedMessagesAreReceived(notReceivedProgress);

    //Act
    messages.forEach(throwableConsumer(roomMessage -> adminBot.sendGroupchat(roomMessage.first, roomMessage.second)));
    final ExpectedMessage[] notReceivedMessages = clientBot.tryReceiveMessages(new StateLatch(), expectedMessages);
    //Assert
    assertAllExpectedMessagesAreReceived(notReceivedMessages);

    //Act
    answers.forEach(throwableConsumer(roomAnswer -> adminBot.sendGroupchat(roomAnswer.first, roomAnswer.second)));
    final ExpectedMessage[] notReceivedAnswers = clientBot.tryReceiveMessages(new StateLatch(), expectedAnswers);
    //Assert
    assertAllExpectedMessagesAreReceived(notReceivedAnswers);

    //Act/Assert
    rooms.forEach(throwableConsumer(roomJID -> roomCloseStateByClientFeedback(roomJID, clientBot, adminBot)));
  }

  @Test
  public void testAdminClosesRoom() throws JaxmppException {
    //Arrange
    botsManager.addBots(1, 1, 0);
    botsManager.startAll();
    final ClientBot clientBot = botsManager.defaultClientBot();
    final AdminBot adminBot = botsManager.defaultAdminBot();

    final BareJID roomJID = obtainRoomOpenState(clientBot, adminBot);
    final Answer answer = new Answer(generateRandomString());
    final ExpectedMessage expectedAnswer = new ExpectedMessageBuilder()
        .from(botRoomJID(roomJID, adminBot))
        .has(Answer.class, a -> answer.value().equals(a.value())).build();

    //Act
    adminBot.sendGroupchat(roomJID, answer);
    final ExpectedMessage[] notReceivedMessages = clientBot.tryReceiveMessages(new StateLatch(), expectedAnswer);
    roomCloseStateByClientFeedback(roomJID, clientBot, adminBot);

    //Assert
    assertAllExpectedMessagesAreReceived(notReceivedMessages);
  }

  @Test
  public void testClientReceivesMessageInOpenRoomState() throws JaxmppException {
    //Arrange
    botsManager.addBots(1, 1, 0);
    botsManager.startAll();
    final ClientBot clientBot = botsManager.defaultClientBot();
    final AdminBot adminBot = botsManager.defaultAdminBot();

    //Act/Assert
    testClientReceivesMessage(throwableSupplier(() -> obtainRoomOpenState(clientBot, adminBot)), true, adminBot, clientBot);
  }

  @Test
  public void testClientReceivesMessageInWorkRoomState() throws JaxmppException {
    //Arrange
    botsManager.addBots(1, 1, 1);
    botsManager.startAll();
    final ClientBot clientBot = botsManager.defaultClientBot();
    final AdminBot adminBot = botsManager.defaultAdminBot();
    final ExpertBot expertBot = botsManager.defaultExpertBot();

    //Act/Assert
    testClientReceivesMessage(throwableSupplier(() -> obtainRoomWorkState(clientBot, adminBot, expertBot)), true, adminBot, clientBot);
  }

  @Test
  public void testClientReceivesMessageInCloseRoomState() throws JaxmppException {
    //Arrange
    botsManager.addBots(1, 1, 1);
    botsManager.startAll();
    final ClientBot clientBot = botsManager.defaultClientBot();
    final AdminBot adminBot = botsManager.defaultAdminBot();
    final ExpertBot expertBot = botsManager.defaultExpertBot();

    //Act/Assert
    testClientReceivesMessage(throwableSupplier(() -> {
      final BareJID roomJID = obtainRoomWorkState(clientBot, adminBot, expertBot);
      roomCloseStateByClientCancel(roomJID, clientBot, adminBot);
      return roomJID;
    }), false, adminBot, clientBot);
  }

  private void testClientReceivesMessage(Supplier<BareJID> obtainState, boolean closeRoom, AdminBot adminBot, ClientBot clientBot) throws JaxmppException {
    //Arrange
    final BareJID roomJID = obtainState.get();
    final Message.Body body = new Message.Body(generateRandomString());
    final ExpectedMessage expectedMessageFromAdmin = new ExpectedMessageBuilder()
        .from(botRoomJID(roomJID, adminBot))
        .has(Message.Body.class, b -> body.value().equals(b.value())).build();

    //Act
    adminBot.sendGroupchat(roomJID, body);
    final ExpectedMessage[] notReceivedMessages = clientBot.tryReceiveMessages(new StateLatch(), expectedMessageFromAdmin);
    if (closeRoom) {
      roomCloseStateByClientCancel(roomJID, clientBot, adminBot);
    }

    //Assert
    assertAllExpectedMessagesAreReceived(notReceivedMessages);
  }
}
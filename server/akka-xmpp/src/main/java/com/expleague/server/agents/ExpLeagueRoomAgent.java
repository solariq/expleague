package com.expleague.server.agents;

import akka.persistence.RecoveryCompleted;
import com.expleague.model.Answer;
import com.expleague.model.Image;
import com.expleague.model.Offer;
import com.expleague.model.Operations.*;
import com.expleague.server.Roster;
import com.expleague.server.dao.Archive;
import com.expleague.util.akka.ActorMethod;
import com.expleague.util.akka.PersistentActorAdapter;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Iq;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Message.MessageType;
import com.expleague.xmpp.stanza.Presence;
import com.expleague.xmpp.stanza.Stanza;
import com.spbsu.commons.func.Functions;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Stack;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static com.expleague.server.agents.ExpLeagueOrder.Role.*;

/**
 * User: solar
 * Date: 16.12.15
 * Time: 13:18
 */
@SuppressWarnings("UnusedParameters")
public class ExpLeagueRoomAgent extends PersistentActorAdapter {
  private static final Logger log = Logger.getLogger(ExpLeagueRoomAgent.class.getName());
  private final JID jid;
  private List<Stanza> archive = new ArrayList<>();
  private ExpLeagueOrder order;

  public ExpLeagueRoomAgent(JID jid) {
    this.jid = jid.bare();
  }

  @Override
  protected void init() {
    order = LaborExchange.board().active(jid.local());
  }

  public JID owner() {
    return archive.get(0).from().bare();
  }

  @ActorMethod
  public void invoke(Message msg) {
    persist(msg, this::invokeInner);
  }

  public void invokeInner(Message msg) {
    final JID from = msg.from();
    archive.add(msg);
    if (msg.type() == MessageType.GROUP_CHAT &&
      !(owner().bareEq(from) || order != null && order.role(from) == ACTIVE)
    ) {
      final Message message = new Message(
        jid,
        from,
        MessageType.GROUP_CHAT,
        "Сообщение от " + from + " не доставленно. Вы не являетесь участником задания!"
      );
      message.append(msg);
      XMPP.send(message, context());
      return;
    }

    // saving everything to archive

    if (order != null) {
      switch (order.role(from)) {
        case OWNER:
          if (msg.has(Feedback.class)) {
            final Feedback feedback = msg.get(Feedback.class);
            order.feedback(feedback.stars(), feedback.payment());
          }
          if (msg.has(Cancel.class)) {
            order.broker().tell(new Cancel(), self());
            order = null;
          }
          break;
        case ACTIVE:
          if (msg.has(Resume.class)) {
            archive.stream()
                    .flatMap(Functions.instancesOf(Message.class))
                    .filter(message -> message.to().equals(jid))
                    .filter(message -> message.type() == MessageType.GROUP_CHAT || message.has(Progress.class))
                    .forEach(message -> XMPP.send(copyFromRoomAlias(message, from), context()));
            XMPP.send(new Presence(roomAlias(msg.from()), owner(), true), context());
          }
          else if (msg.has(Answer.class)) {
            order = null;
          }
          else if (msg.has(Progress.class)) {
            final Progress progress = msg.get(Progress.class);
            final Progress.MetaChange metaChange = progress.change();
            if (metaChange != null) {
              switch (metaChange.target()) {
                case PATTERNS:
                  break;
                case TAGS:
                  if (metaChange.operation() == Progress.MetaChange.Operation.ADD)
                    order.tag(metaChange.name());
                  else
                    order.untag(metaChange.name());
                  break;
              }
            }
            sendToOwner(new Message(jid, owner(), progress));
          }
          else if (msg.has(Suspend.class)) {
            XMPP.send(new Presence(roomAlias(msg.from()), owner(), false), context());
          }
          else if (msg.has(Start.class)) {
            archive.stream()
                    .flatMap(Functions.instancesOf(Message.class))
                    .filter(message -> message.type() == MessageType.GROUP_CHAT || message.has(Progress.class))
                    .forEach(message -> XMPP.send(copyFromRoomAlias(message, from), context()));
            sendToOwner(new Message(jid, owner(), msg.get(Start.class), Roster.instance().profile(from.local())));
          }
          else if (msg.body().startsWith("{\"type\":\"pageVisited\"")) {
            sendToOwner(new Message(jid, owner(), msg.body()));
          }
          break;
        case SLACKER:
          if (msg.has(Cancel.class)) {
            sendToOwner(new Message(roomAlias(msg.from()), owner(), msg.get(Command.class)));
          }
          break;
        default:
          log.warning("Unexpected command " + msg.get(Command.class) + " received from " + from + " playing: " + order.role(from));
      }
    }
    else if (msg.has(Feedback.class)) {
      final Feedback feedback = msg.get(Feedback.class);
      lastOrder().feedback(feedback.stars(), feedback.payment());
    }
    else if (msg.from().bareEq(owner()) && !msg.has(Done.class)){
      final Offer offer = offer(msg);
      if (offer != null) {
        order = LaborExchange.board().register(offer);
        invoke(new Message(XMPP.jid(), offer.client(), new Create(), order.offer()));
        LaborExchange.tell(context(), order, self());
      }
    }

    if (msg.type() == MessageType.GROUP_CHAT) {
      broadcast(msg);
    }
  }

  private void sendToOwner(Message message) {
    persist(message, msg -> {
      archive.add(message);
      XMPP.send(message, context());
    });
  }

  @SuppressWarnings("ConstantConditions")
  public static ExpLeagueOrder[] replay(LaborExchange.Board board, String roomId) {
    final Stack<ExpLeagueOrder> result = new Stack<>();
    final Archive.Dump dump = Archive.instance().dump(roomId);
    if (dump == null)
      return new ExpLeagueOrder[0];
    dump.stream()
      .flatMap(Functions.instancesOf(Message.class))
      .forEach(message -> {
        final JID from = message.from();
        if (message.has(Create.class) && message.has(Offer.class)) {
          final Offer offer = message.get(Offer.class);
          if (isUserMissing(offer.client())) {
            return;
          }
          result.add(board.register(offer));
        }
        else if (isUserMissing(from)) {
          //noinspection UnnecessaryReturnStatement
          return;
        }
        else if (message.has(Message.Subject.class)) {
          result.add(board.register(Offer.create(message.to(), message.from(), message)));
        }
        else if (!result.isEmpty()) {
          try (final ExpLeagueOrder.Snapshot snapshot = result.lastElement().snapshot(message.getTimestampMs())) {
            final ExpLeagueOrder order = snapshot.order();
            final ExpLeagueOrder.State state = order.state();
            if (message.has(Invite.class)) {
              state.invite(from);
            }
            else if (message.has(Start.class) || message.has(Resume.class)) {
              state.enter(from);
            }
            else if (message.has(Suspend.class)) {
              state.suspend();
            }
            else if (message.has(Cancel.class) && order.role(from) == OWNER) {
              state.cancel();
            }
            else if (message.has(Cancel.class)) {
              state.refused(from);
            }
            else if (message.has(Answer.class)) {
              state.close();
            }
            else if (message.has(Feedback.class)) {
              final Feedback feedback = message.get(Feedback.class);
              order.feedback(feedback.stars(), feedback.payment());
            }
          }
        }
      });
    return result.toArray(new ExpLeagueOrder[result.size()]);
  }

  protected static boolean isUserMissing(final JID userJid) {
    return userJid.local() != null && Roster.instance().user(userJid.local()) == null;
  }

  private ExpLeagueOrder lastOrder() {
    final Stream<ExpLeagueOrder> history = LaborExchange.board().history(jid.local());
    return history.reduce((order1, order2) -> order2).orElse(null);
  }

  private Offer offer(Message msg) {
    final Offer result;
    if (!msg.has(Offer.class) && msg.body().isEmpty() && !msg.has(Image.class))
      return null;
    final ExpLeagueOrder prevOrder = lastOrder();
    if (prevOrder != null) {
      final Offer prevOffer = prevOrder.offer();
      result = prevOffer.copy();
      result.topic(result.topic() + "\n" + (msg.body().length() > 0 ? msg.body() : "Приложение"));
      if (msg.has(Image.class)) {
        result.attach(msg.get(Image.class));
      }
      prevOrder.participants()
          .filter(expert -> EnumSet.of(SLACKER, DENIER).contains(prevOrder.role(expert)))
          .forEach(slacker -> result.filter().reject(slacker));
      prevOrder.participants()
          .filter(expert -> prevOrder.role(expert) == ExpLeagueOrder.Role.ACTIVE)
          .forEach(worker -> result.filter().prefer(worker));
    }
    else result = Offer.create(jid, owner(), msg);
    return result;
  }

  @ActorMethod
  public void invoke(Presence presence) {
    broadcast(presence);
  }

  @ActorMethod
  public void invoke(Iq command) {
    XMPP.send(Iq.answer(command), context());
    if (command.type() == Iq.IqType.SET) {
      XMPP.send(new Message(jid, command.from(), "Room set up and unlocked."), context());
    }
  }

  @ActorMethod
  public void dump(DumpRequest req) {
    sender().tell(archive, self());
  }

  public static class DumpRequest {}

  private Stream<JID> participants() {
    if (order == null || order.status() == ExpLeagueOrder.Status.OPEN)
      return Stream.of(owner());
    return Stream.concat(Stream.of(owner()), order.of(ACTIVE));
  }

  private void broadcast(Stanza stanza) {
    participants()
      .filter(jid -> !jid.bareEq(stanza.from()))
      .forEach(jid -> XMPP.send(copyFromRoomAlias(stanza, jid), context()));
  }

  private <S extends Stanza> S copyFromRoomAlias(final S stanza, final JID to) {
    return stanza.<S>copy()
      .to(to)
      .from(roomAlias(stanza.from()));
  }

  @NotNull
  private JID roomAlias(JID from) {
    return new JID(this.jid.local(), this.jid.domain(), from.local());
  }

  @Override
  public String persistenceId() {
    return jid.local();
  }

  @Override
  public void onReceiveRecover(Object o) throws Exception {
    if (o instanceof Stanza) {
      final Stanza stanza = (Stanza) o;
      archive.add(stanza);
    }
    else if (o instanceof RecoveryCompleted) {
      if (archive.isEmpty()) {
        final Archive.Dump dump = Archive.instance().dump(jid.local());
        if (dump != null)
          dump.stream().forEach(archive::add);
      }
    }
  }
}

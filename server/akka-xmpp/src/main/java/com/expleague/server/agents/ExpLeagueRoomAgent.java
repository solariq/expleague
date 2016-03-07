package com.expleague.server.agents;

import com.expleague.model.Answer;
import com.expleague.model.Offer;
import com.expleague.model.Operations.*;
import com.expleague.server.ExpertManager;
import com.expleague.server.dao.Archive;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Iq;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Message.MessageType;
import com.expleague.xmpp.stanza.Presence;
import com.expleague.xmpp.stanza.Stanza;
import com.spbsu.commons.func.Functions;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * User: solar
 * Date: 16.12.15
 * Time: 13:18
 */
@SuppressWarnings("UnusedParameters")
public class ExpLeagueRoomAgent extends ActorAdapter {
  private static final Logger log = Logger.getLogger(ExpLeagueRoomAgent.class.getName());
  private final JID jid;
  private Archive.Dump dump;
  private ExpLeagueOrder order;

  public ExpLeagueRoomAgent(JID jid) {
    this.jid = jid.bare();
  }

  @Override
  protected void init() {
    dump = Archive.instance().dump(jid.local());
    order = LaborExchange.board().active(jid.local());
  }

  @ActorMethod
  public void invoke(Message msg) {
    final JID from = msg.from();
    dump(msg);
    if (msg.type() == MessageType.GROUP_CHAT &&
        !(dump.owner().bareEq(from) || order != null && order.role(from) == ExpLeagueOrder.Role.ACTIVE)
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
      if (msg.has(Command.class)) {
        switch (order.role(from)) {
          case OWNER:
            if (msg.has(Feedback.class))
              order.feedback(msg.get(Feedback.class).stars());

            if (msg.has(Cancel.class)) {
              order.broker().tell(new Cancel(), self());
              order = null;
            }
            break;
          case ACTIVE:
            if (msg.has(Resume.class)) {
              dump.stream()
                  .flatMap(Functions.instancesOf(Message.class))
                  .filter(message -> message.type() == MessageType.GROUP_CHAT)
                  .forEach(message -> XMPP.send(copyFromRoomAlias(message, from), context()));
              XMPP.send(new Presence(roomAlias(msg.from()), dump.owner(), true), context());
            }
            else if (msg.has(Answer.class)) {
              order = null;
            }
            else if (msg.has(Suspend.class)) {
              XMPP.send(new Presence(roomAlias(msg.from()), dump.owner(), false), context());
            }
            else if (msg.has(Cancel.class)) {
              XMPP.send(new Message(jid, dump.owner(), msg.get(Command.class)), context());
            }
            break;
          case INVITED:
            if (msg.has(Start.class)) {
              dump.stream()
                  .flatMap(Functions.instancesOf(Message.class))
                  .filter(message -> message.type() == MessageType.GROUP_CHAT)
                  .forEach(message -> XMPP.send(copyFromRoomAlias(message, from), context()));
              XMPP.send(new Message(jid, dump.owner(), msg.get(Start.class), ExpertManager.instance().profile(from.bare())), context());
            }
            break;
          default:
            log.warning("Unexpected command " + msg.get(Command.class) + " received from " + from + " playing: " + order.role(from));
        }
      }
      else if (order.role(from) == ExpLeagueOrder.Role.ACTIVE && msg.body().startsWith("{\"type\":\"pageVisited\"")) {
        XMPP.send(new Message(jid, dump.owner(), msg.body()), context());
      }
    }
    else if (msg.has(Command.class))
      log.warning("Command received when no active order exist!");
    else {
      order = LaborExchange.board().register(offer(msg));
      invoke(new Message(XMPP.jid(), jid, new Create(), order.offer()));
      LaborExchange.tell(context(), order, self());
    }

    if (msg.type() == MessageType.GROUP_CHAT) {
      broadcast(msg);
    }
  }

  @SuppressWarnings("ConstantConditions")
  public static ExpLeagueOrder[] replay(LaborExchange.Board board, String roomId) {
    final Queue<ExpLeagueOrder> result = new ArrayDeque<>();
    Archive.instance().dump(roomId).stream().forEach(stanza -> {
      if (!(stanza instanceof Message))
        return;
      final Message message = (Message) stanza;
      final ExpLeagueOrder current = result.peek();
      final ExpLeagueOrder.State state = current != null ? current.state() : null;

      if (message.has(Create.class)) {
        result.add(board.register(message.get(Offer.class)));
      }
      else if (message.has(Invite.class)) {
        state.invite(message.from());
      }
      else if (message.has(Start.class) || message.has(Resume.class)) {
        state.enter(message.from());
      }
      else if (message.has(Suspend.class)) {
        state.suspend();
      }
      else if (message.has(Cancel.class) && current.role(message.from()) == ExpLeagueOrder.Role.OWNER) {
        state.cancel();
      }
      else if (message.has(Cancel.class)) {
        state.refused(message.from());
      }
      else if (message.has(Answer.class)) {
        state.close();
      }
      else if (message.has(Feedback.class)) {
        current.feedback(message.get(Feedback.class).stars());
      }
    });
    return result.toArray(new ExpLeagueOrder[result.size()]);
  }

  private Offer offer(Message msg) {
    final ExpLeagueOrder[] history = LaborExchange.board().history(jid.local());
    final Offer result;
    if (history.length > 0) {
      final ExpLeagueOrder prevOrder = history[history.length - 1];
      final Offer prevOffer = prevOrder.offer();
      result = prevOffer.copy();
      result.topic(result.topic() + "\n" + msg.body());
      order.participants()
          .filter(expert -> order.role(expert) == ExpLeagueOrder.Role.SLACKER || order.role(expert) == ExpLeagueOrder.Role.DENIER)
          .forEach(slacker -> result.filter().reject(slacker));
      order.participants()
          .filter(expert -> order.role(expert) == ExpLeagueOrder.Role.ACTIVE)
          .forEach(worker -> result.filter().prefer(worker));
    }
    else result = Offer.create(jid, dump.owner(), msg);
    return result;
  }

  @ActorMethod
  public void invoke(Presence presence) {
    dump(presence);
    broadcast(presence);
  }

  @ActorMethod
  public void invoke(Iq command) {
    dump(command);
    XMPP.send(Iq.answer(command), context());
    if (command.type() == Iq.IqType.SET) {
      XMPP.send(new Message(jid, command.from(), "Room set up and unlocked."), context());
    }
  }

  private void dump(Stanza stanza) {
    if (dump == null)
      dump = Archive.instance().register(jid.local(), stanza.from().local());
    dump.accept(stanza);
  }

  private Stream<JID> participants() {
    if (order == null || order.status() == ExpLeagueOrder.Status.OPEN)
      return Stream.of(dump.owner());
    return Stream.of(dump.owner(), order.state().active());
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
}

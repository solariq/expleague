package com.expleague.server.agents;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import com.expleague.model.Offer;
import com.expleague.model.Operations;
import com.expleague.model.OrderState;
import com.expleague.model.Tag;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.EnumSet;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static com.expleague.server.agents.ExpLeagueOrder.Role.*;

/**
 * Experts League
 * Created by solar on 03/03/16.
 */
public abstract class ExpLeagueOrder {
  private static final Logger log = Logger.getLogger(LaborExchange.class.getName());

  public static int SIMULTANEOUSLY_INVITED = 3;
  private Offer offer;

  protected OrderState state = OrderState.NONE;
  private volatile ActorRef broker;

  public ExpLeagueOrder(Offer offer) {
    this.offer = offer;
  }

  public OrderState state() {
    return state;
  }

  @Nullable
  protected Status state(ActorRef broker, ActorContext context) {
    if (!broker(broker))
      return null;
    return new Status(context);
  }

  public Offer offer() {
    return offer;
  }

  public void offer(Offer offer) {
    this.offer = offer;
  }

  public ActorRef broker() {
    return broker;
  }

  public synchronized boolean broker(ActorRef broker) {
    if (broker != null && this.broker != null && this.broker != broker) {
      log.warning("Broker " + broker + " assigned while another broker found: " + this.broker);
      return false;
    }
    this.broker = broker;
    return true;
  }

  @NotNull
  public JID room() {
    return offer.room();
  }

  public abstract String id();
  public abstract Role role(JID jid);
  public abstract Stream<JID> participants();
  public abstract void feedback(double stars, @Nullable String payment);
  public abstract Stream<JID> of(Role role);
  public abstract double feedback();
  public abstract Tag[] tags();
  public abstract Stream<StatusHistoryRecord> statusHistoryRecords();
  public abstract long activationTimestampMs();
  public abstract String payment();

  // Write interface
  protected abstract void mapTempRoles(Function<Role, Role> map);
  protected abstract void role(JID bare, Role role, long ts);
  protected abstract void tag(String tag);
  protected abstract void untag(String tag);
  protected abstract void updateActivationTimestampMs(final long timestamp);

  protected void state(OrderState state) {
    state(state, currentTimestampMillis());
  }

  protected void state(OrderState state, long ts) {
    this.state = state;
  }

  protected long currentTimestampMillis() {
    return System.currentTimeMillis();
  }

  public JID owner() {
    //noinspection OptionalGetWithoutIsPresent
    return of(OWNER).findFirst().get();
  }

  public class Status {
    private final ActorContext context;

    public Status(ActorContext context) {
      this.context = context;
    }

    public boolean check(JID expert) {
      if (role(expert) == Role.NONE && of(CANDIDATE).count() < SIMULTANEOUSLY_INVITED) {
        role(expert, CHECKING);
        return true;
      }
      return false;
    }

    public boolean invite(JID expert) {
      if (of(INVITED).count() < SIMULTANEOUSLY_INVITED) {
        role(expert, INVITED);
        return true;
      }
      else if (of(CANDIDATE).count() < SIMULTANEOUSLY_INVITED)
        role(expert, CANDIDATE);
      else
        role(expert, Role.NONE);
      return false;
    }

    public Status enter(JID expert) {
      if (expert != null)
        role(expert, ACTIVE);
      mapTempRoles(role -> role.permanent() ? role : Role.NONE);
      if (state() == OrderState.OPEN && expert != null)
        XMPP.send(new Message(expert, jid(), new Operations.Start(order().id(), expert)), context);
      state(expert != null ? OrderState.IN_PROGRESS : OrderState.OPEN);

      return this;
    }

    public Status cancel() {
      mapTempRoles(role -> role.permanent() ? role : Role.NONE);
      state(OrderState.DONE);
      broker(null);
      return this;
    }

    public Status refused(JID expert) {
      switch (role(expert)) {
        case ACTIVE:
          state(OrderState.OPEN);
          role(expert, SLACKER);
          break;
        case INVITED:
          role(expert, DENIER);
          break;
        case CHECKING:
          role(expert, DND);
          break;
      }
      return this;
    }

    public Status ignored(JID expert) {
      final Role role = role(expert);
      if (role == INVITED)
        role(expert, DND);
      else if (role == CHECKING)
        role(expert, NONE);
      return this;
    }

    public JID nextCandidate() {
      return of(CANDIDATE).findFirst().orElse(null);
    }

    public boolean interview(JID expert) {
      final Role role = role(expert);
      final boolean roleDoesntMatch = EnumSet.of(SLACKER, DENIER, DND).contains(role);
      if (roleDoesntMatch) {
        log.finest("Expert " + expert + " failed interview because she is " + role);
        return false;
      }
      return offer.fit(expert);
    }

    public JID jid() {
      return offer.room();
    }

    public Offer offer() {
      return offer;
    }

    public void nextRound() {
      mapTempRoles(role -> role != DND ? role : Role.NONE);
    }

    public void suspend() {
      suspend(System.currentTimeMillis());
    }

    public void suspend(final long endTimestampMs) {
      state(OrderState.SUSPENDED);
      updateActivationTimestampMs(endTimestampMs);
    }

    public ExpLeagueOrder order() {
      return ExpLeagueOrder.this;
    }

    public Role role(JID jid) {
      return ExpLeagueOrder.this.role(jid);
    }

    protected void role(JID bare, Role checking) {
      ExpLeagueOrder.this.role(bare, checking, System.currentTimeMillis());
    }

    public void close() {
      state(OrderState.DONE);
      broker(null);
    }

    public Stream<JID> experts() {
      return participants().filter(jid -> EnumSet.of(ACTIVE, CANDIDATE, CHECKING, INVITED).contains(role(jid)));
    }

    public void state(OrderState state) {
      if (state != this.state())
        XMPP.send(new Message(jid(), jid(), new Operations.Progress(order().id(), state)), context);
      ExpLeagueOrder.this.state(state);
    }

    public OrderState state() {
      return ExpLeagueOrder.this.state();
    }
  }

  public enum Role {
    NONE(0, false),
    ACTIVE(1, true),
    CHECKING(2, false),
    CANDIDATE(3, false),
    INVITED(4, false),
    DENIER(5, true),
    SLACKER(6, true),
    OWNER(7, true),
    DND(8, false),
    ;

    int index;
    boolean permanent;
    Role(int index, boolean permanent) {
      this.index = index;
      this.permanent = permanent;
    }

    public int index() {
      return index;
    }

    public boolean permanent() {
      return permanent;
    }

    public static Role valueOf(int index) {
      return Stream.of(Role.values()).filter(s -> s.index == index).findAny().orElse(null);
    }
  }

  public static class StatusHistoryRecord {
    private final OrderState status;
    private final Date date;

    public StatusHistoryRecord(final OrderState status, final Date date) {
      this.status = status;
      this.date = date;
    }

    public OrderState getStatus() {
      return status;
    }

    public Date getDate() {
      return date;
    }
  }
}

package com.expleague.server.agents;

import akka.actor.ActorRef;
import com.expleague.model.Offer;
import com.expleague.model.Tag;
import com.expleague.xmpp.JID;
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
  public static final int NO_SNAPSHOT_TIMESTAMP = -1;

  public static int SIMULTANEOUSLY_INVITED = 3;
  private final Offer offer;
  private final State state = new State();

  protected Status status = Status.OPEN;
  private ActorRef broker;

  protected long snapshotTimestamp = NO_SNAPSHOT_TIMESTAMP;

  public ExpLeagueOrder(Offer offer) {
    this.offer = offer;
  }

  public Status status() {
    return status;
  }

  protected State state() {
    return state;
  }

  public Offer offer() {
    return offer;
  }

  public ActorRef broker() {
    return broker;
  }

  public void broker(ActorRef broker) {
    this.broker = broker;
  }

  public JID room() {
    return offer.room();
  }

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
  protected abstract void role(JID bare, Role checking);
  protected abstract void tag(String tag);
  protected abstract void untag(String tag);
  protected abstract void updateActivationTimestampMs(final long timestamp);

  protected void status(Status status) {
    this.status = status;
  }

  protected long currentTimestampMillis() {
    if (snapshotTimestamp != -1) {
      return snapshotTimestamp;
    }
    return System.currentTimeMillis();
  }

  protected Snapshot snapshot(final long timestamp) {
    return new Snapshot(timestamp);
  }

  public JID owner() {
    //noinspection OptionalGetWithoutIsPresent
    return of(OWNER).findFirst().get();
  }

  public class State {
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

    public ExpLeagueOrder.State enter(JID expert) {
      if (expert != null)
        role(expert, ACTIVE);
      mapTempRoles(role -> role.permanent() ? role : Role.NONE);
      status(expert != null ? Status.IN_PROGRESS : Status.OPEN);
      return this;
    }

    public ExpLeagueOrder.State cancel() {
      mapTempRoles(role -> role.permanent() ? role : Role.NONE);
      status(Status.DONE);
      return this;
    }

    public ExpLeagueOrder.State refused(JID expert) {
      switch (role(expert)) {
        case ACTIVE:
          status(Status.OPEN);
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

    public ExpLeagueOrder.State ignored(JID expert) {
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
      status(Status.SUSPENDED);
      updateActivationTimestampMs(endTimestampMs);
    }

    public ExpLeagueOrder order() {
      return ExpLeagueOrder.this;
    }

    public Role role(JID jid) {
      return ExpLeagueOrder.this.role(jid);
    }

    protected void role(JID bare, Role checking) {
      ExpLeagueOrder.this.role(bare, checking);
    }

    public void close() {
      status(Status.DONE);
    }

    public Stream<JID> experts() {
      return participants().filter(jid -> EnumSet.of(ACTIVE, CANDIDATE, CHECKING, INVITED).contains(role(jid)));
    }

    public void status(Status status) {
      ExpLeagueOrder.this.status(status);
    }

    public Status status() {
      return ExpLeagueOrder.this.status();
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

  public enum Status {
    NONE(-1),
    OPEN(0),
    IN_PROGRESS(1),
    SUSPENDED(2),
    DONE(3),
    ;

    int index;

    Status(int index) {
      this.index = index;
    }

    public static Status valueOf(int index) {
      return Stream.of(Status.values()).filter(s -> s.index == index).findAny().orElse(null);
    }

    public int index() {
      return index;
    }
  }

  public static class StatusHistoryRecord {
    private final Status status;
    private final Date date;

    public StatusHistoryRecord(final Status status, final Date date) {
      this.status = status;
      this.date = date;
    }

    public Status getStatus() {
      return status;
    }

    public Date getDate() {
      return date;
    }
  }

  protected class Snapshot implements AutoCloseable {
    public Snapshot(final long timestamp) {
      snapshotTimestamp = timestamp;
    }

    public ExpLeagueOrder order() {
      return ExpLeagueOrder.this;
    }

    @Override
    public void close() {
      snapshotTimestamp = NO_SNAPSHOT_TIMESTAMP;
    }
  }
}

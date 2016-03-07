package com.expleague.server.agents;

import akka.actor.ActorRef;
import com.expleague.model.Offer;
import com.expleague.model.Operations;
import com.expleague.server.dao.Archive;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Experts League
 * Created by solar on 03/03/16.
 */
public abstract class ExpLeagueOrder {
  public static int SIMULTANEOUSLY_INVITED = 3;
  private final Offer offer;
  private final State state = new State();

  protected Status status = Status.OPEN;
  private ActorRef broker;

  public ExpLeagueOrder(Offer offer) {
    this.offer = offer;
  }

  public Status status() {
    return status;
  }

  public State state() {
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
  public abstract void feedback(double stars);

  protected abstract void mapTempRoles(Function<Role, Role> map);
  protected abstract void role(JID bare, Role checking);
  protected abstract Stream<JID> of(Role role);
  protected void status(Status status) {
    this.status = status;
  }

  public class State {
    public boolean check(JID expert) {
      if (role(expert) == Role.NONE && of(Role.CANDIDATE).count() < SIMULTANEOUSLY_INVITED) {
        role(expert, Role.CHECKING);
        return true;
      }
      return false;
    }

    public boolean invite(JID expert) {
      if (of(Role.INVITED).count() < SIMULTANEOUSLY_INVITED) {
        role(expert, Role.INVITED);
        return true;
      }
      else if (of(Role.CANDIDATE).count() < SIMULTANEOUSLY_INVITED)
        role(expert, Role.CANDIDATE);
      else
        role(expert, Role.NONE);
      return false;
    }

    public ExpLeagueOrder.State enter(JID expert) {
      if (expert != null)
        role(expert, Role.ACTIVE);
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
          role(expert, Role.SLACKER);
          break;
        case INVITED:
          role(expert, Role.DENIER);
          break;
        case CHECKING:
          role(expert, Role.DND);
          break;
      }
      return this;
    }

    public ExpLeagueOrder.State ignored(JID expert) {
      role(expert, Role.DND);
      return this;
    }

    public JID nextCandidate() {
      return of(Role.CANDIDATE).findFirst().orElse(null);
    }

    public boolean interview(JID expert) {
      return !EnumSet.of(Role.SLACKER, Role.DENIER, Role.DND).contains(role(expert)) && offer.fit(expert);
    }

    public JID jid() {
      return offer.room();
    }

    public JID active() {
      return of(Role.ACTIVE).findAny().orElse(null);
    }

    public Offer offer() {
      return offer;
    }

    public void nextRound() {
      mapTempRoles(role -> role != Role.DND ? role : Role.NONE);
    }

    public void suspend() {
      status(Status.SUSPENDED);
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
}

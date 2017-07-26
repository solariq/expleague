package com.expleague.model;

import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * User: solar
 * Date: 21.12.15
 * Time: 13:01
 */
@SuppressWarnings("unused")
public class Operations {
  public static final String NS = "http://expleague.com/scheme";

  @XmlRootElement
  public static class Invite extends Command {
    @XmlAttribute
    public double timeout;
  }

  @XmlRootElement
  public static class Ok extends Item {
    public Ok() { }
  }

  @XmlRootElement
  public static class Sync extends Command {
    @XmlAttribute
    private String func;

    @XmlAttribute
    private String data;

    public Sync() { }

    public Sync(final String func, final String data) {
      this.func = func;
      this.data = data;
    }

    public String func() {
      return func;
    }

    public String data() {
      return data;
    }
  }

  @XmlRootElement
  public static class Resume extends Command {
    @XmlElementRef
    private Offer offer;

    @XmlAttribute
    private String order;
    public Resume() {}

    public Resume(Offer offer) {
      this.offer = offer;
    }

    public Resume(String order) {
      this.order = order;
    }

    public Offer offer() {
      return offer;
    }

    public String order() {
      return order;
    }
  }

  @XmlRootElement
  public static class Cancel extends Command {
    @XmlAttribute
    private String order;
    public Cancel() {}

    public Cancel(String order) {
      this.order = order;
    }

    public String order() {
      return order;
    }

    public void order(String order) {
      this.order = order;
    }
  }

  @XmlRootElement
  public static class Ignore extends Command {}

  @XmlRootElement
  public static class Create extends Command {}

  @XmlRootElement
  public static class Clear extends Command {}

  @XmlRootElement
  public static class LastMessage extends Command {}

  @XmlRootElement
  public static class Start extends Command {
    @XmlAttribute
    private String order;

    @XmlAttribute
    private JID expert;

    public Start() {}
    public Start(String orderId, JID expert) {
      order = orderId;
      this.expert = expert;
    }

    public String order() {
      return order;
    }

    public void order(String order) {
      this.order = order;
    }
  }

  @XmlRootElement
  public static class Done extends Command {
    @XmlAttribute
    private String order;

    public Done() {}
    public Done(String order) {
      this.order = order;
    }

    @Nullable
    public String order() {
      return order;
    }
  }

  @XmlRootElement
  public static class Check extends Command {
  }


  @XmlRootElement
  public static class Verified extends Item {
    @XmlAttribute
    private JID authority;

    @XmlAttribute
    private String order;

    public Verified() {}
    public Verified(String order, JID authority) {
      this.authority = authority;
      this.order = order;
    }

    public JID authority() {
      return authority;
    }

    @Nullable
    public String order() {
      return order;
    }
  }

  @XmlRootElement
  public static class Confirm extends Command {}

  @XmlRootElement
  public static class Suspend extends Command {
    @XmlAttribute(name="start")
    private double startTimestamp;

    @XmlAttribute(name="end")
    private double endTimestamp;

    @XmlAttribute
    private String order;

    public Suspend() {
    }

    public Suspend(String order, final long startTimestampMs, final long endTimestampMs) {
      this.order = order;
      this.startTimestamp = startTimestampMs / 1000.;
      this.endTimestamp = endTimestampMs / 1000.;
    }

    public long getStartTimestampMs() {
      return (long)(startTimestamp * 1000);
    }

    public long getEndTimestampMs() {
      return (long)(endTimestamp * 1000);
    }

    @Nullable
    public String order() {
      return order;
    }

    public void order(String order) {
      this.order = order;
    }
  }

  @XmlRootElement(name = "feedback")
  public static class Feedback extends Command {
    @XmlAttribute
    private Integer stars;

    @XmlAttribute
    private String payment;

    public Feedback(int stars) {
      this.stars = stars;
    }

    public Feedback() {}

    public int stars() {
      return stars != null ? stars : 2;
    }

    public String payment() {
      return payment;
    }
  }

  public static abstract class Command extends Item {}

  @XmlRootElement
  public static class Token extends Item {
    @XmlAttribute
    private String client;
    @XmlValue
    private String value;

    public Token() {}
    public Token(String client, String value) {
      this.client = client;
      this.value = value;
    }

    public String value() {
      return value;
    }
    public String client() {
      return client;
    }
  }

  @XmlRootElement
  public static class StatusChange extends Item {
    @XmlAttribute
    private String from;

    @XmlAttribute
    private String to;

    @XmlAttribute(name = "task-state")
    private String taskState;

    public StatusChange() {}
    public StatusChange(String from, String to, String taskState) {
      this.from = from;
      this.to = to;
      this.taskState = taskState;
    }

    public StatusChange(String from, String to) {
      this.from = from;
      this.to = to;
    }

    public String from() {
      return from;
    }

    public String to() {
      return to;
    }

    public String taskState() {
      return taskState;
    }
  }

  @XmlRootElement
  public static class Enter extends Item {
    @XmlAttribute
    private String expert;

    public Enter() {}
    public Enter(String expert) {
      this.expert = expert;
    }

    public String expert() {
      return expert;
    }
  }

  @XmlRootElement
  public static class Exit extends Item {
    @XmlAttribute
    private String expert;

    public Exit() {}
    public Exit(String expert) {
      this.expert = expert;
    }

    public String expert() {
      return expert;
    }
  }

  @XmlRootElement(name = "offer-change")
  public static class OfferChange extends Command {
    @XmlAttribute
    private JID by;

    public OfferChange() {}

    public OfferChange(JID by) {
      this.by = by;
    }

    public JID by() {
      return by;
    }
  }

  @XmlRootElement(name = "room-state-changed")
  public static class RoomStateChanged extends Item {
    @XmlAttribute(name = "state")
    private int stateCode;

    public RoomStateChanged() {}
    public RoomStateChanged(RoomState state) {
      this.stateCode = state.code();
    }

    public RoomState state() {
      return Arrays.stream(RoomState.values()).filter(state -> stateCode == state.code()).findFirst().orElse(null);
    }
  }

  @XmlRootElement(name = "room-role-update")
  public static class RoomRoleUpdate extends Item {
    @XmlAttribute
    private JID expert;

    @XmlAttribute
    private Affiliation affiliation;

    @XmlAttribute
    private Role role;

    @XmlAttribute
    private String order;

    public RoomRoleUpdate() {}
    public RoomRoleUpdate(JID expert, Affiliation affiliation) {
      this.expert = expert;
      this.affiliation = affiliation;
    }

    public RoomRoleUpdate(JID jid, Role role, Affiliation affiliation) {
      this.expert = jid;
      this.role = role;
      this.affiliation = affiliation;
    }

    public Affiliation affiliation() {
      return affiliation;
    }

    public Role role() {
      return role;
    }

    public JID expert() {
      return expert;
    }
  }

  @XmlRootElement(name = "room-message-received")
  public static class RoomMessageReceived extends Progress {
    @XmlAttribute
    private String from;

    @XmlAttribute
    private Integer count;

    @XmlAttribute
    private Boolean expert;

    public RoomMessageReceived() {}
    public RoomMessageReceived(JID from, boolean expert) {
      this.from = from.local();
      this.expert = expert;
    }

    public RoomMessageReceived(int msgCount) {
      count = msgCount;
    }

    public String from() {
      return from;
    }

    public int count() {
      return count != null ? count : 1;
    }

    public boolean expert() {
      return expert != null ? expert : false;
    }
  }

  /**
   * Experts League
   * Created by solar on 28/03/16.
   */

  @XmlRootElement
  public static class Progress extends Item {
    @XmlElement(name="change", namespace = NS)
    private MetaChange metaChange;

    @XmlElement(name="state", namespace = NS)
    private StateChange stateChange;

    @XmlElement(name = "tag", namespace = NS)
    @XmlElementWrapper(name = "assigned", namespace = NS)
    private List<Tag> assigned;

    @XmlAttribute
    private String order;

    public Progress() {
    }

    public Progress(String id, MetaChange metaChange) {
      this.order = id;
      this.metaChange = metaChange;
    }

    public Progress(String id, OrderState state) {
      this.order = id;
      if (state == null)
        throw new RuntimeException("Invalid state!");
      this.stateChange = new StateChange(state);
    }

    public Stream<MetaChange> meta() {
      if (assigned != null) {
        final List<MetaChange> oldFormat = new ArrayList<>();
        return assigned.stream().map(tag -> new MetaChange(tag.name(), MetaChange.Operation.ADD, MetaChange.Target.TAGS));
      }
      else if (metaChange != null)
        return Stream.of(metaChange);
      else
        return Stream.empty();
    }

    public String order() {
      return order;
    }

    public OrderState state() {
      return stateChange != null ? stateChange.state : null;
    }

    public void order(String id) {
      this.order = id;
    }

    @XmlRootElement(name = "change", namespace = NS)
    public static class MetaChange {
      @XmlAttribute
      private Operation operation;

      @XmlAttribute
      private Target target;

      @XmlValue
      private String name;

      @SuppressWarnings("unused")
      public MetaChange() {}

      public MetaChange(String name, Operation op, Target target) {
        this.name = name;
        operation = op;
        this.target = target;
      }

      public String name() {
        return this.name;
      }

      public Target target() {
        return this.target;
      }

      public Operation operation() {
        return this.operation;
      }

      @XmlEnum
      public enum Operation {
        @XmlEnumValue("add") ADD,
        @XmlEnumValue("remove") REMOVE,
        @XmlEnumValue("visit") VISIT
      }

      @XmlEnum
      public enum Target {
        @XmlEnumValue("pattern") PATTERNS,
        @XmlEnumValue("tag") TAGS,
        @XmlEnumValue("phone") PHONE,
        @XmlEnumValue("url") URL,
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final MetaChange that = (MetaChange) o;
        return operation == that.operation && target == that.target && name.equals(that.name);
      }

      @Override
      public int hashCode() {
        int result = operation.hashCode();
        result = 31 * result + target.hashCode();
        result = 31 * result + name.hashCode();
        return result;
      }
    }

    @XmlRootElement(name = "state", namespace = NS)
    public static class StateChange {
      @XmlValue
      private OrderState state;

      @SuppressWarnings("unused")
      public StateChange() {
        this.state = OrderState.NONE;
      }

      public StateChange(OrderState state) {
        this.state = state;
      }
    }
  }
}

package com.expleague.model;

import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;

import javax.xml.bind.annotation.*;
import java.util.Arrays;

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
    public Resume() { }

    public Resume(Offer offer) {
      this.offer = offer;
    }

    public Offer offer() {
      return offer;
    }
  }

  @XmlRootElement
  public static class Cancel extends Command {}

  @XmlRootElement
  public static class Ignore extends Command {}

  @XmlRootElement
  public static class Create extends Command {}

  @XmlRootElement
  public static class Start extends Command {}

  @XmlRootElement
  public static class Done extends Command {}

  @XmlRootElement
  public static class Check extends Command {}

  @XmlRootElement
  public static class Confirm extends Command {}

  @XmlRootElement
  public static class Suspend extends Command {
    @XmlAttribute(name="start")
    private double startTimestamp;

    @XmlAttribute(name="end")
    private double endTimestamp;

    public Suspend() {
    }

    public Suspend(final long startTimestampMs, final long endTimestampMs) {
      this.startTimestamp = startTimestampMs / 1000.;
      this.endTimestamp = endTimestampMs / 1000.;
    }

    public long getStartTimestampMs() {
      return (long)(startTimestamp * 1000);
    }

    public long getEndTimestampMs() {
      return (long)(endTimestamp * 1000);
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

    public RoomMessageReceived() {}
    public RoomMessageReceived(JID from) {
      this.from = from.local();
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
  }

  /**
   * Experts League
   * Created by solar on 28/03/16.
   */

  @XmlRootElement
  public static class Progress extends Command {
    @XmlElement(name="change", namespace = NS)
    private MetaChange metaChange;

    @XmlAttribute
    private String order;

    public Progress() {
    }

    public Progress(MetaChange metaChange) {
      this.metaChange = metaChange;
    }

    public MetaChange change() {
      return metaChange;
    }

    public String order() {
      return order;
    }

    @XmlRootElement(name = "change", namespace = "NS")
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
    }
  }
}

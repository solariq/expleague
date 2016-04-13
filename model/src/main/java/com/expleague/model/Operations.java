package com.expleague.model;

import com.expleague.xmpp.Item;

import javax.xml.bind.annotation.*;
import java.net.URI;

/**
 * User: solar
 * Date: 21.12.15
 * Time: 13:01
 */
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
  public static class Suspend extends Command {}

  @XmlRootElement(name = "feedback")
  public static class Feedback extends Command {
    @XmlAttribute
    private Integer stars;

    public int stars() {
      return stars != null ? stars : 2;
    }
  }

  public static abstract class Command extends Item {}

  @XmlRootElement
  public static class Token extends Item {
    @XmlValue
    private String value;

    public String value() {
      return value;
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

    public Progress() {
    }

    public Progress(MetaChange metaChange) {
      this.metaChange = metaChange;
    }

    public MetaChange change() {
      return metaChange;
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

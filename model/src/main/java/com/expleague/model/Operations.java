package com.expleague.model;

import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;

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
  public static class Cancel extends Command {
  }

  @XmlRootElement
  public static class Ignore extends Command {
  }

  @XmlRootElement
  public static class Create extends Command {}

  @XmlRootElement
  public static class Start extends Command {}

  @XmlRootElement
  public static class Done extends Command {}

  @XmlRootElement
  public static class Suspend extends Command {}

  @XmlRootElement(name = "expert-feedback")
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
}

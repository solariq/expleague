package com.expleague.model;

import com.expleague.xmpp.Item;

import javax.xml.bind.annotation.*;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

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
    @XmlElement(name = "tag", namespace = NS)
    @XmlElementWrapper(name = "assigned", namespace = NS)
    private List<Tag> assigned;

    @XmlElement(namespace = NS)
    private URI visited;

    @XmlElement(namespace = NS)
    private String call;


    public Progress(Tag... tag) {
      assigned = Arrays.asList(tag);
    }

    public Progress() {
    }

    public Progress(String phone) {
      call = phone;
    }

    public Stream<Tag> assigned() {
      return assigned.stream();
    }

    public boolean hasAssigned() {
      return assigned != null;
    }

    public String phone() {
      return call;
    }
  }
}

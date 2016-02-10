package com.expleague.model;

import com.expleague.server.agents.XMPP;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
import scala.concurrent.duration.FiniteDuration;

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
    public Long timeout;

    @XmlRootElement(name = "x", namespace = "http://jabber.org/protocol/muc#user")
    public static class Invitation extends Item {
      @XmlElementRef
      private I invite;

      public Invitation(){}
      public Invitation(JID from) {
        invite = new I();
        invite.from = from;
      }

      @XmlRootElement(name = "invite", namespace = "http://jabber.org/protocol/muc#user")
      public static final class I extends Item {
        @XmlAttribute
        private JID from;
      }
    }

    @XmlRootElement(name = "x", namespace = "jabber:x:conference")
    public static class Reason extends Item {
      @XmlAttribute
      private String reason;

      public Reason() {}
      public Reason(String subj) {
        reason = subj;
      }
    }
    public void form(Message message, Offer offer) {
      message.append(new Invitation(XMPP.jid())).append(new Reason(offer.description())).append(this);
    }
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
    @XmlAttribute
    private Long timeout;

    @XmlElementRef
    private Offer offer;
    public Resume() { }

    public Resume(Offer offer) {
      this.offer = offer;
    }

    public Resume(Offer offer, FiniteDuration inviteTimeout) {
      this.offer = offer;
      timeout = inviteTimeout.toMillis();
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

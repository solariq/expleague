package com.tbts.model;

import com.tbts.server.agents.XMPP;
import com.tbts.xmpp.Item;
import com.tbts.xmpp.JID;
import com.tbts.xmpp.stanza.Message;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * User: solar
 * Date: 21.12.15
 * Time: 13:01
 */
public class Operations {
  public static final String NS = "http://expleague.com/scheme";

  public static class Invite {
    @XmlRootElement(name = "x", namespace = "http://jabber.org/protocol/muc#user")
    public static class Invitation {
      @XmlElementRef
      private I invite;

      public Invitation(){}
      public Invitation(JID from) {
        invite = new I();
        invite.from = from;
      }

      @XmlRootElement(name = "invite", namespace = "http://jabber.org/protocol/muc#user")
      public static final class I {
        @XmlAttribute
        private JID from;
      }
    }

    @XmlRootElement(name = "x", namespace = "jabber:x:conference")
    public static class Reason {
      @XmlAttribute
      private String reason;

      public Reason() {}
      public Reason(String subj) {
        reason = subj;
      }
    }
    public void form(Message message, Offer offer) {
      message.append(new Invitation(XMPP.jid())).append(new Reason(offer.description()));
    }
  }

  @XmlRootElement
  public static class Ok extends Item {
    public Ok() { }
  }

  @XmlRootElement
  public static class Resume extends Item {
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
  public static class Cancel extends Item {
    public Cancel(){ }
  }

  @XmlRootElement
  public static class Start extends Item {}

  @XmlRootElement
  public static class Done extends Item {}
}

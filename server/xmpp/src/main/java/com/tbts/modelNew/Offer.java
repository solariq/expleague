package com.tbts.modelNew;

import akka.actor.ActorRef;
import com.tbts.xmpp.Item;
import com.tbts.xmpp.JID;
import com.tbts.xmpp.stanza.Message;

import javax.xml.bind.annotation.*;
import java.util.Date;

/**
 * User: solar
 * Date: 17.12.15
 * Time: 16:38
 */
@XmlRootElement
public class Offer extends Item {
  public ActorRef broker;

  @XmlAttribute
  private JID room;
  @XmlAttribute
  private JID client;

  @XmlAnyElement(lax = true)
  private Item description;

  @XmlAttribute
  private Urgency type;

  @XmlAttribute
  private Date started;

  public Offer() {
  }

  public Offer(JID room, JID client, Item description) {
    this.room = room;
    this.client = client;
    this.description = description;
  }

  public JID room() {
    return room;
  }

  public String description() {
    if (description instanceof Message.Subject)
      return ((Message.Subject) description).value();
    return description.toString();
  }

  @XmlEnum
  enum Urgency {
    @XmlEnumValue("asap") ASAP,
    @XmlEnumValue("day") DAY,
    @XmlEnumValue("na") NA,
  }
}

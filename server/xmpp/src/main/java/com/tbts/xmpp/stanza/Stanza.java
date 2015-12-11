package com.tbts.xmpp.stanza;

import com.tbts.xmpp.Item;
import com.tbts.xmpp.JID;

import javax.xml.bind.annotation.XmlAttribute;

/**
 * User: solar
 * Date: 06.12.15
 * Time: 13:53
 */
@SuppressWarnings("unused")
public abstract class Stanza extends Item {
  @XmlAttribute
  private JID from;
  @XmlAttribute
  private JID to;

  public JID from() {
    return from;
  }

  public JID to() {
    return to;
  }
}

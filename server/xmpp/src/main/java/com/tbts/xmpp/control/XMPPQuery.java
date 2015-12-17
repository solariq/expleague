package com.tbts.xmpp.control;

import com.tbts.xmpp.Item;
import com.tbts.xmpp.stanza.Iq;

import javax.xml.bind.annotation.XmlTransient;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 18:35
 */
@XmlTransient
public abstract class XMPPQuery extends Item {
  public abstract Item reply(Iq.StanzaType type);
}

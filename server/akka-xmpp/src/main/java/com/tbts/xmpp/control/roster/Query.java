package com.tbts.xmpp.control.roster;

import com.tbts.server.services.Roster;
import com.tbts.server.services.XMPPServices;
import com.tbts.xmpp.Item;
import com.tbts.xmpp.JID;
import com.tbts.xmpp.control.XMPPQuery;
import com.tbts.xmpp.stanza.Iq;

import javax.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 18:19
 */
@XmlRootElement(name = "query")
public class Query extends XMPPQuery {
  static {
    XMPPServices.register("jabber:iq:roster", Roster.class, "roster");
  }
  @XmlElements({
      @XmlElement(type = RosterItem.class, name = "item", namespace = "jabber:iq:roster")
  })
  private final List<RosterItem> items = new ArrayList<>();

  public Query(JID... jids) {
    for (int i = 0; i < jids.length; i++) {
      items.add(new RosterItem(jids[i]));
    }
  }

  public List<RosterItem> items() {
    return Collections.unmodifiableList(items);
  }

  public void add(RosterItem item) {
    items.add(item);
  }

  public Query() {}

  @Override
  public Item reply(Iq.IqType type) {
    return this;
  }

  public static class RosterItem {
    @XmlAttribute
    private JID jid;

    @XmlAttribute
    private Subscription subscription;

    @XmlAttribute
    private String name;

    @XmlAttribute
    private String ask;

    public RosterItem(JID jid) {
      this.jid = jid;
    }

    public RosterItem(JID jid, Subscription subscription, String name) {
      this.jid = jid;
      this.subscription = subscription;
      this.name = name;
    }

    public RosterItem(JID jid, Subscription subscription, String name, String ask) {
      this.jid = jid;
      this.subscription = subscription;
      this.name = name;
      this.ask = ask;
    }

    public RosterItem() {}

    public JID jid() {
      return jid;
    }

    @XmlEnum
    public enum Subscription {
      @XmlEnumValue(value = "none") NONE,
      @XmlEnumValue(value = "both") BOTH,
    }
  }
}

package com.expleague.xmpp.control.roster;

import com.expleague.server.services.RosterService;
import com.expleague.server.services.XMPPServices;
import com.expleague.xmpp.AnyHolder;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.control.XMPPQuery;
import com.expleague.xmpp.stanza.Iq;

import javax.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.expleague.xmpp.control.roster.RosterQuery.NS;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 18:19
 */
@SuppressWarnings("unused")
@XmlRootElement(name = "query", namespace = NS)
public class RosterQuery extends XMPPQuery {
  public static final String NS = "jabber:iq:roster";

  static {
    XMPPServices.register(NS, RosterService.class, "roster");
  }
  @XmlElements({
      @XmlElement(type = RosterItem.class, name = "item", namespace = NS)
  })
  private final List<RosterItem> items = new ArrayList<>();

  @XmlAttribute
  private String version;

  public RosterQuery(JID... jids) {
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

  public RosterQuery() {}

  @Override
  public Item reply(Iq.IqType type) {
    return this;
  }

  public static class RosterItem extends Item implements AnyHolder {
    @XmlAttribute
    private JID jid;

    @XmlAttribute
    private Subscription subscription;

    @XmlAttribute
    private String name;

    @XmlAttribute
    private String ask;

    @XmlElement(namespace = NS)
    private List<String> group;
    @XmlAnyElement(lax = true)
    private List<Item> any;

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

    @Override
    public List<? super Item> any() {
      return any != null ? any : (any = new ArrayList<>());
    }

    public RosterItem() {}

    public JID jid() {
      return jid;
    }

    public void group(String group) {
      (this.group = this.group != null ? this.group : new ArrayList<>())
          .add(group);
    }

    @XmlEnum
    public enum Subscription {
      @XmlEnumValue(value = "none") NONE,
      @XmlEnumValue(value = "to") TO,
      @XmlEnumValue(value = "from") FROM,
      @XmlEnumValue(value = "both") BOTH,
    }
  }
}

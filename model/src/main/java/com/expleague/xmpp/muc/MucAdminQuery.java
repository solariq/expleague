package com.expleague.xmpp.muc;

import com.expleague.model.Affiliation;
import com.expleague.model.Role;
import com.expleague.xmpp.Item;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.annotation.*;

/**
 * Experts League
 * Created by solar on 28.01.17.
 */
@XmlRootElement(name = "query", namespace = MucAdminQuery.NS)
public class MucAdminQuery extends Item {
  public static final String NS = "http://jabber.org/protocol/muc#admin";

  @XmlElementRef(namespace = NS)
  Item item;

  public MucAdminQuery() {}

  public MucAdminQuery(String nick, Affiliation affiliation, Role role) {
    item = new Item(nick, affiliation, role);
  }

  @Nullable
  public Affiliation affiliation() {
    return item != null ? item.affiliation : null;
  }

  @Nullable
  public Role role() {
    return item != null ? item.role : null;
  }

  @Nullable
  public String nick() {
    return item != null ? item.nick : null;
  }

  @XmlRootElement(name = "item", namespace = NS)
  @XmlType(name = "muc-item")
  public static class Item extends com.expleague.xmpp.Item {
    @XmlAttribute
    private String nick;

    @XmlAttribute
    private Affiliation affiliation;

    @XmlAttribute
    private Role role;

    @XmlElement
    private String reason;

    public Item() {}

    public Item(String nick, Affiliation affiliation, Role role) {
      this.nick = nick;
      this.affiliation = affiliation;
      this.role = role;
    }
  }
}

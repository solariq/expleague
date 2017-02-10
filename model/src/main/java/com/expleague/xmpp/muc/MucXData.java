package com.expleague.xmpp.muc;

/**
 * User: solar
 * Date: 28.12.15
 * Time: 22:22
 */

import com.expleague.model.Affiliation;
import com.expleague.model.Role;
import com.expleague.xmpp.AnyHolder;
import com.expleague.xmpp.Item;

import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@XmlRootElement(name = "x", namespace = MucXData.MUC_NS)
public class MucXData extends Item implements AnyHolder {
  public static final String MUC_NS = "http://jabber.org/protocol/muc";
  @XmlAnyElement(lax = true)
  private List<Object> extensions = new ArrayList<>();

  public MucXData(Affiliation affiliation, Role role) {
    append(new MucItem(role, affiliation));
  }

  @Override
  public List<? super Item> any() {
    return extensions;
  }

  public MucXData(Item... items) {
    if (items.length > 0)
      extensions.addAll(Arrays.asList(items));
  }

  public MucXData() {
  }

  public Affiliation affiliation() {
    return has(MucItem.class) ? get(MucItem.class).affiliation() : null;
  }

  public Role role() {
    return has(MucItem.class) ? get(MucItem.class).role() : null;
  }

  public void role(Role role) {
    if (!has(MucItem.class))
      extensions.add(new MucItem());
    get(MucItem.class).role(role);
  }

  public void affiliation(Affiliation affiliation) {
    if (!has(MucItem.class))
      extensions.add(new MucItem());
    get(MucItem.class).affiliation(affiliation);
  }
}

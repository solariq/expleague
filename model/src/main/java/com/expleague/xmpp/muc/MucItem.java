package com.expleague.xmpp.muc;

import com.expleague.model.Affiliation;
import com.expleague.model.Role;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Experts League
 * Created by solar on 25.01.17.
 */
@XmlRootElement(name = "item", namespace = MucXData.MUC_NS)
public class MucItem extends com.expleague.xmpp.Item {
  @XmlAttribute
  private Affiliation affiliation;

  @XmlAttribute
  private Role role;

  public MucItem(Role role, Affiliation affiliation) {
    this.role = role;
    this.affiliation = affiliation;
  }

  public MucItem() {}

  public Affiliation affiliation() {
    return affiliation;
  }

  public Role role() {
    return role;
  }

  public void role(Role role) {
    this.role = role;
  }

  public void affiliation(Affiliation affiliation) {
    this.affiliation = affiliation;
  }
}

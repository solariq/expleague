package com.expleague.xmpp.muc;

import com.expleague.model.UserRole;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Experts League
 * Created by solar on 25.01.17.
 */
@XmlRootElement(name = "item", namespace = MucXData.MUC_NS)
public class MucItem extends com.expleague.xmpp.Item {
  @XmlAttribute
  private String affiliation;

  @XmlAttribute
  private UserRole role;

  public MucItem(UserRole role) {
    this.role = role;
  }

  public MucItem() {
  }

  public UserRole role() {
    return role;
  }
}

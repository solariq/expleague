package com.expleague.model;

import com.expleague.xmpp.Item;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Created by solar on 27/01/16.
 */

@XmlRootElement(name = "expert")
public class ExpertsProfile extends Item {
  @XmlAttribute
  private String name;

  @XmlAttribute
  private String login;

  @XmlAttribute
  private Integer tasksCount;

  @XmlElement(namespace = Operations.NS)
  private String avatar;

  @SuppressWarnings("unused")
  public ExpertsProfile() {}

  public String login() {
    return login;
  }

  public ExpertsProfile(String name, String login, String avatar, int tasksCount) {
    this.name = name;
    this.login = login;
    this.avatar = avatar;
    this.tasksCount = tasksCount;
  }
}

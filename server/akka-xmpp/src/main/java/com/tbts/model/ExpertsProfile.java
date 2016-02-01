package com.tbts.model;

import com.tbts.xmpp.Item;

import javax.xml.bind.annotation.XmlAttribute;
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

  @SuppressWarnings("unused")
  public ExpertsProfile() {}

  public ExpertsProfile(String name, String login, int tasksCount) {
    this.name = name;
    this.login = login;
    this.tasksCount = tasksCount;
  }
}

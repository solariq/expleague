package com.tbts.model;

import com.tbts.xmpp.Item;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * User: solar
 * Date: 28.12.15
 * Time: 14:51
 */
@XmlRootElement(name = "status")
public class ServiceStatus extends Item {
  @XmlAttribute(name = "experts-online")
  int expertsOnline = 0;

  public ServiceStatus() {}

  public ServiceStatus(int expertsOnline) {
    this.expertsOnline = expertsOnline;
  }
}
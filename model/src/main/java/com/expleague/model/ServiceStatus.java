package com.expleague.model;

import com.expleague.xmpp.Item;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * User: solar
 * Date: 28.12.15
 * Time: 14:51
 */
@XmlRootElement(name = "status")
public class ServiceStatus extends Item {
  @XmlAttribute(name = "experts")
  int expertsOnline = 0;
  @XmlAttribute(name = "experts-online")
  int expertsAvailable = 0;
  @XmlAttribute(name = "starving-tasks")
  int starvingTasks = 0;

  public ServiceStatus() {}
  public ServiceStatus(ServiceStatus copy) {
    expertsOnline = copy.expertsOnline;
    expertsAvailable = copy.expertsAvailable;
    starvingTasks = copy.starvingTasks;
  }

  public void brokerStarving() {
    starvingTasks++;
  }

  public void brokerFed() {
    starvingTasks--;
  }

  public void expertAvailable() {
    expertsAvailable++;
  }

  public void expertBusy() {
    expertsAvailable--;
  }

  public void expertOnline() {
    expertsOnline++;
  }

  public void expertOffline() {
    expertsOnline--;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ServiceStatus that = (ServiceStatus) o;

    return expertsOnline == that.expertsOnline && expertsAvailable == that.expertsAvailable && starvingTasks == that.starvingTasks;
  }

  @Override
  public int hashCode() {
    int result = expertsOnline;
    result = 31 * result + expertsAvailable;
    result = 31 * result + starvingTasks;
    return result;
  }
}

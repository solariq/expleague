package com.expleague.xmpp.control;

import com.expleague.xmpp.Item;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * Experts League
 * Created by solar on 21.02.17.
 */
@SuppressWarnings("unused")
@XmlRootElement(name = "query", namespace = DeliveryQuery.NS)
public class DeliveryQuery extends Item {
  public static final String NS = "http://expleague.com/delivery";

  @XmlElementRef(namespace = NS)
  private DeliveryQuery.Item item;

  public DeliveryQuery() {}

  public DeliveryQuery(String id, String resource) {
    item = new Item(id, resource);
  }

  public String id() {
    return item != null ? item.id : null;
  }

  public String resource() {
    return item != null ? item.resource : null;
  }

  @XmlRootElement(name = "item", namespace = NS)
  @XmlType(name = "delivery-item")
  public static class Item extends com.expleague.xmpp.Item {
    @XmlAttribute(name = "id", namespace = NS)
    private String id;

    @XmlAttribute(name = "resource", namespace = NS)
    private String resource;

    public Item() {}

    public Item(String id, String resource) {
      this.id = id;
      this.resource = resource;
    }
  }
}

package com.expleague.xmpp;

import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * User: solar
 * Date: 24.12.15
 * Time: 15:14
 */
@XmlRootElement(name = "body", namespace = BoshBody.NS)
public class BoshBody extends Item {
  public static final String NS = "http://jabber.org/protocol/httpbind";
  @XmlAttribute
  private String content;

  @XmlAttribute
  private String type;

  @XmlAttribute
  private JID from;

  @XmlAttribute
  private String to;

  @XmlAttribute
  private Long rid;

  @XmlAttribute
  private String sid;

  @XmlAttribute
  private Integer wait;

  @XmlAttribute
  private Integer hold;

  @XmlAttribute
  private Integer requests;

  @XmlAnyElement(lax = true)
  private List<Item> contents = new ArrayList<>();

  public String sid() {
    return sid;
  }

  public void sid(String sid) {
    this.sid = sid;
  }

  public List<Item> items() {
    return contents;
  }

  public void requests(int count) {
    requests = count;
  }

  public void type(String terminate) {
    this.type = terminate;
  }

  public boolean terminate() {
    return "terminame".equals(type);
  }
}

package com.tbts.xmpp;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * User: solar
 * Date: 06.12.15
 * Time: 17:17
 */
@SuppressWarnings("unused")
@XmlRootElement(name = "stream", namespace = "http://etherx.jabber.org/streams")
public class Stream {
  @XmlAttribute
  private String version;
  @XmlAttribute(namespace = "http://www.w3.org/XML/1998/namespace")
  private String lang;
  @XmlElements({
                   @XmlElement(name = "message", namespace = "jabber:client", type = Message.class),
                   @XmlElement(name = "iq", namespace = "jabber:client", type = IQ.class),
                   @XmlElement(name = "presence", namespace = "jabber:client", type = Presence.class)
  })
  private List<? extends Stanza> contents = new ArrayList<>();

  public List<? extends Stanza> contents() {
    return contents;
  }

  public String version() {
    return version;
  }

  public String lang() {
    return lang;
  }
}

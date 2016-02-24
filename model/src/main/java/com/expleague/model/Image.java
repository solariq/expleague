package com.expleague.model;

import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;

/**
 * Created by solar on 21.02.16.
 */
@XmlRootElement
public class Image extends Item {
  public static final String MAGIC_CONST = "OSYpRdXPNGZgRvsY";
  @XmlValue
  private String src;

  public Image() {
  }

  public Image(String src, JID from) {
    this.src = storageByJid(from) + src;
  }

  public static String storageByJid(JID from) {
    if ("localhost".equals(from.domain())) {
      return "http://localhost:8067/";
    } else
      return "https://img." + from.domain() + "/" + MAGIC_CONST + "/";
  }

  public String url() {
    return src;
  }
}

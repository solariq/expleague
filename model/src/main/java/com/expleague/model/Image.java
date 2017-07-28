package com.expleague.model;

import com.expleague.xmpp.JID;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;

/**
 * Experts League
 * Created by solar on 21.02.16.
 */
@XmlRootElement
public class Image extends Attachment {
  public static final String MAGIC_CONST = "OSYpRdXPNGZgRvsY";
  @XmlValue
  private String src;

  public Image() {
  }

  public Image(String src) {
    this.src = src;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Image image = (Image) o;

    return src != null ? src.equals(image.src) : image.src == null;
  }

  @Override
  public int hashCode() {
    return src != null ? src.hashCode() : 0;
  }
}

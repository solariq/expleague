package com.expleague.model.patch;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Experts League
 * Created by solar on 15/02/16.
 */
@XmlRootElement(name = "link-patch")
public class LinkPatch extends Patch {
  @XmlElement(namespace = Patch.NS)
  private String title;

  @XmlElement(namespace = Patch.NS)
  private String link;

  public LinkPatch() {}
  public LinkPatch(String source, String title, String link) {
    super(source);
    this.title = title;
    this.link = link;
  }

  public String title() {
    return title;
  }

  public String link() {
    return link;
  }

  @Override
  public String toMD() {
    return String.format(">[%s](%s)\n[Источник](%s)", title, link, source());
  }
}

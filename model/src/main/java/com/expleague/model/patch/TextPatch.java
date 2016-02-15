package com.expleague.model.patch;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Experts League
 * Created by solar on 15/02/16.
 */
@XmlRootElement(name = "text-patch")
public class TextPatch extends Patch {
  @XmlElement(namespace = Patch.NS)
  private String title;

  @XmlElement(namespace = Patch.NS)
  private String text;

  public TextPatch() {}

  @Override
  public String toMD() {
    return String.format("### %s\n%s\n\n[Источник](%s)", title, text, source());
  }

  public TextPatch(String source, String title, String text) {
    super(source);
    this.title = title;
    this.text = text;
  }

  public String title() {
    return title;
  }

  public String text() {
    return text;
  }
}

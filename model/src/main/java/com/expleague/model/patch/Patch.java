package com.expleague.model.patch;

import com.expleague.xmpp.Item;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.File;

/**
 * Experts League
 * Created by solar on 15/02/16.
 */
@XmlRootElement
public abstract class Patch extends Item {
  public static final String NS = "http://expleague.com/expert/patch";

  @XmlAttribute
  private String source;

  public Patch() {}
  public Patch(String source) {
    this.source = source;
  }

  public String source() {
    return source;
  }

  public abstract String toMD();

  private File file;
  public void file(File file) {
    this.file = file;
  }

  public File file() {
    return file;
  }
}

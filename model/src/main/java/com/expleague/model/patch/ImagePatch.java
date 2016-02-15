package com.expleague.model.patch;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Experts League
 * Created by solar on 15/02/16.
 */
@XmlRootElement(name = "image-patch")
public class ImagePatch extends Patch {
  @XmlElement(namespace = Patch.NS)
  private String title;

  @XmlElement(namespace = Patch.NS)
  private String image;


  public ImagePatch(){}

  public ImagePatch(String source, String title, String image) {
    super(source);
    this.title = title;
    this.image = image;
  }

  public String title() {
    return title;
  }

  public String image() {
    return image;
  }

  @Override
  public String toMD() {
    return String.format("![%s](%s)\n[Источник](%s)", title, image, source());
  }
}

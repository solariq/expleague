package com.expleague.xmpp;

import com.expleague.xmpp.control.XMPPFeature;

import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: solar
 * Date: 08.12.15
 * Time: 17:51
 */
@XmlRootElement
public class Features extends Item {
  public Features(XMPPFeature... features) {
    this.features.addAll(Arrays.asList(features));
  }
  public Features() {
  }

  @XmlAnyElement(lax = true)
  private List<XMPPFeature> features = new ArrayList<>();


  public List<XMPPFeature> features() {
    return features;
  }
}

package com.tbts.xmpp;

import com.tbts.xmpp.control.XMPPFeature;
import com.tbts.xmpp.control.register.Register;
import com.tbts.xmpp.control.sasl.Mechanisms;
import com.tbts.xmpp.control.tls.StartTLS;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
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

  @XmlElements({
    @XmlElement(name = "starttls", namespace = "urn:ietf:params:xml:ns:xmpp-tls", type = StartTLS.class),
    @XmlElement(name = "mechanisms", namespace = "urn:ietf:params:xml:ns:xmpp-sasl", type = Mechanisms.class),
    @XmlElement(name = "register", namespace = "jabber:iq:register", type = Register.class),
  })
  private List<XMPPFeature> features = new ArrayList<>();


  public List<XMPPFeature> features() {
    return features;
  }
}

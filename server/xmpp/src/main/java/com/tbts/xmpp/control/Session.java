package com.tbts.xmpp.control;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 18:11
 */
@XmlRootElement(namespace = Session.NS)
public class Session extends XMPPFeature {
  public static final String NS = "urn:ietf:params:xml:ns:xmpp-session";
}

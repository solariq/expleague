package com.expleague.xmpp.control.receipts;

import com.expleague.xmpp.Item;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author vpdelta
 */
@XmlRootElement(namespace = Request.NS)
public class Request extends Item {
  public static final String NS = "urn:xmpp:receipts";
}

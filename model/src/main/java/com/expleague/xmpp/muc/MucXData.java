package com.expleague.xmpp.muc;

/**
 * User: solar
 * Date: 28.12.15
 * Time: 22:22
 */

import com.expleague.xmpp.AnyHolder;
import com.expleague.xmpp.Item;

import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Arrays;
import java.util.List;

@XmlRootElement(name = "x", namespace = MucXData.MUC_NS)
public class MucXData extends Item implements AnyHolder {
  public static final String MUC_NS = "http://jabber.org/protocol/muc";
  @XmlAnyElement(lax = true)
  private List<Object> extensions;

  @Override
  public List<? super Item> any() {
    return extensions;
  }

  public MucXData(Item... items) {
    if (items.length > 0)
      extensions = Arrays.asList(items);
  }
}

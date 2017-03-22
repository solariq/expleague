package com.expleague.xmpp.control.expleague;

import com.expleague.server.services.HistoryService;
import com.expleague.server.services.RestoreService;
import com.expleague.server.services.XMPPServices;
import com.expleague.xmpp.AnyHolder;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Stanza;

import javax.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Experts League
 * Created by solar on 28/03/16.
 */
@SuppressWarnings({"unused", "FieldCanBeLocal"})
@XmlRootElement(name = "query", namespace = UserHistoryQuery.NS)
public class UserHistoryQuery extends Item implements AnyHolder {
  public static final String NS = "http://expleague.com/scheme/history";

  @XmlAttribute
  private String client;

  @XmlElementWrapper(name = "content", namespace = NS)
  @XmlAnyElement
  private List<Item> content;

  static {
    XMPPServices.register(NS, HistoryService.class, "story");
  }

  public UserHistoryQuery() {
  }

  public String client() {
    return client;
  }

  @Override
  public List<? super Item> any() {
    if (content == null)
      content = new ArrayList<>();
    return content;
  }
}

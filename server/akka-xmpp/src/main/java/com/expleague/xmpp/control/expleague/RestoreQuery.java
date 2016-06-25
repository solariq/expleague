package com.expleague.xmpp.control.expleague;

import com.expleague.model.Offer;
import com.expleague.model.Operations;
import com.expleague.server.services.BestAnswerService;
import com.expleague.server.services.RestoreService;
import com.expleague.server.services.XMPPServices;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.stanza.Stanza;

import javax.xml.bind.annotation.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Experts League
 * Created by solar on 28/03/16.
 */
@SuppressWarnings({"unused", "FieldCanBeLocal"})
@XmlRootElement(name = "query", namespace = RestoreQuery.NS)
public class RestoreQuery extends Item {
  public static final String NS = "http://expleague.com/scheme/restore";
  static {
    XMPPServices.register(NS, RestoreService.class, "restore");
  }

  @XmlElements(
      @XmlElement(name = "payment", namespace = NS)
  )
  private List<String> payments;

  @XmlElements(
      @XmlElement(name = "room", namespace = NS)
  )
  private Set<String> rooms;


  public RestoreQuery() {
  }

  public String[] payments() {
    return payments != null ? payments.toArray(new String[payments.size()]) : new String[0];
  }

  public void addRoom(String id) {
    if (rooms == null)
      rooms = new HashSet<>();
    rooms.add(id);
  }
}

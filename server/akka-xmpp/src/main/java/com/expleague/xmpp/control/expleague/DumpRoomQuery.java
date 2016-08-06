package com.expleague.xmpp.control.expleague;

import com.expleague.model.Offer;
import com.expleague.model.Operations;
import com.expleague.server.services.BestAnswerService;
import com.expleague.server.services.DumpRoomService;
import com.expleague.server.services.XMPPServices;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.stanza.Stanza;

import javax.xml.bind.annotation.*;
import java.util.List;

/**
 * Experts League
 * Created by solar on 28/03/16.
 */
@SuppressWarnings({"unused", "FieldCanBeLocal"})
@XmlRootElement(name = "query", namespace = DumpRoomQuery.NS)
public class DumpRoomQuery extends Item {
  public static final String NS = "http://expleague.com/scheme/dump-room";
  static {
    XMPPServices.register(NS, DumpRoomService.class, "dump-room");
  }

  @XmlAttribute
  private String room;

  @XmlElement(namespace = Operations.NS)
  private Offer offer;

  @XmlElementWrapper(name = "content", namespace = NS)
  @XmlAnyElement
  private List<Stanza> content;

  public String room() {
    return room;
  }

  public DumpRoomQuery() {
  }

  public DumpRoomQuery(Offer offer, List<Stanza> content) {
    this.offer = offer;
    this.content = content;
  }
}

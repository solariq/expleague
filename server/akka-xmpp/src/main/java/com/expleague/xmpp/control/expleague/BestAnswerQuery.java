package com.expleague.xmpp.control.expleague;

import com.expleague.model.Offer;
import com.expleague.model.Operations;
import com.expleague.model.Pattern;
import com.expleague.server.services.BestAnswerService;
import com.expleague.server.services.PatternsService;
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
@XmlRootElement(name = "query", namespace = BestAnswerQuery.NS)
public class BestAnswerQuery extends Item {
  public static final String NS = "http://expleague.com/scheme/best-answer";
  static {
    XMPPServices.register(NS, BestAnswerService.class, "best-answer");
  }

  @XmlElement(namespace = Operations.NS)
  private Offer offer;

  @XmlElementWrapper(name = "content", namespace = NS)
  @XmlAnyElement
  private List<Stanza> content;


  public BestAnswerQuery() {
  }

  public BestAnswerQuery(Offer offer, List<Stanza> content) {
    this.offer = offer;
    this.content = content;
  }
}

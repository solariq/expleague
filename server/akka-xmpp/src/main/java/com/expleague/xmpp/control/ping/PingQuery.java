package com.expleague.xmpp.control.ping;

import com.expleague.server.services.PingService;
import com.expleague.server.services.RosterService;
import com.expleague.server.services.XMPPServices;
import com.expleague.xmpp.AnyHolder;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.control.XMPPQuery;
import com.expleague.xmpp.stanza.Iq;

import javax.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.expleague.xmpp.control.roster.RosterQuery.NS;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 18:19
 */
@SuppressWarnings("unused")
@XmlRootElement(name = "ping", namespace = PingQuery.NS)
public class PingQuery extends XMPPQuery {
  public static final String NS = "urn:xmpp:ping";

  static {
    XMPPServices.register(NS, PingService.class, "ping");
  }

  @Override
  public Item reply(Iq.IqType type) {
    return this;
  }
}

package com.expleague.server;

import com.expleague.xmpp.JID;

/**
 * Experts League
 * Created by solar on 02/03/16.
 */
public class XMPPUser {
  private final String id;
  private final String name;
  private final String country;
  private final String city;
  private final String avatar;
  private JID jid;

  public XMPPUser(String id, String name, String country, String city, String avatar) {
    this.id = id;
    this.name = name;
    this.country = country;
    this.city = city;
    this.avatar = avatar;
    jid = new JID(id, ExpLeagueServer.config().domain(), null);
  }

  public String avatar() {
    return avatar;
  }

  public String city() {
    return city;
  }

  public String country() {
    return country;
  }

  public String name() {
    return name;
  }

  public String id() {
    return id;
  }

  public XMPPDevice[] devices() {
    return Roster.instance().devices(this.id);
  }

  public JID jid() {
    return jid;
  }
}

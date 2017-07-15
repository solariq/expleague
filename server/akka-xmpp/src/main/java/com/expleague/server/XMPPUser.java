package com.expleague.server;

import com.expleague.model.ExpertsProfile;
import com.expleague.model.Tag;
import com.expleague.util.stream.RequiresClose;
import com.expleague.xmpp.JID;

import java.util.Date;
import java.util.stream.Stream;

/**
 * Experts League
 * Created by solar on 02/03/16.
 */
public abstract class XMPPUser {
  public static final XMPPUser NO_SUCH_USER = new XMPPUser("", "", "", "No Such User", 0, 0, new Date(), "", ExpertsProfile.Authority.NONE, null) {
    @Override
    public void updateUser(String substitutedBy) {}
  };
  private final String id;
  private final String name;
  private final String country;
  private final String city;
  private final String avatar;
  private final ExpertsProfile.Authority authority;
  private JID jid;
  protected String substitutedBy;

  public XMPPUser(String id, String country, String city, String name, int sex, int age, Date created, String avatar, ExpertsProfile.Authority authority, String substitutedBy) {
    this.id = id;
    this.name = name;
    this.country = country;
    this.city = city;
    this.avatar = avatar;
    this.authority = authority;
    this.substitutedBy = substitutedBy;
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

  public String substitutedBy() {
    return substitutedBy;
  }

  public XMPPDevice[] devices() {
    return Roster.instance().devices(this.id);
  }

  @RequiresClose
  public Stream<Tag> tags() {
    return Roster.instance().specializations(this.jid);
  }

  public JID jid() {
    return jid;
  }

  public ExpertsProfile.Authority authority() {
    return authority;
  }

  public abstract void updateUser(String substitutedBy);
}

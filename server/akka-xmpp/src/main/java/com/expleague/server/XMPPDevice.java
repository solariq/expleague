package com.expleague.server;

import com.expleague.xmpp.JID;
import org.jetbrains.annotations.Nullable;

/**
 * User: solar
 * Date: 11.12.15
 * Time: 18:39
 */
public abstract class XMPPDevice {
  private final XMPPUser user;
  private final String passwd;
  private final String name;
  private final boolean expert;
  private final String platform;
  protected String token;

  public XMPPDevice(XMPPUser user, String name, String passwd, boolean expert, String platform, String token) {
    this.user = user;
    this.passwd = passwd;
    this.name = name;
    this.expert = expert;
    this.platform = platform;
    this.token = token;
  }

  public String name() {
    return name;
  }

  public String passwd() {
    return passwd;
  }

  @Nullable
  public String avatar() {
    return user.avatar();
  }

  @Nullable
  public String city() {
    return user.city();
  }

  @Nullable
  public String country() {
    return user.country();
  }

  @Nullable
  public String realName() {
    return user.name();
  }

  public XMPPUser user() {
    return user;
  }

  public String token() {
    return token;
  }

  public abstract void updateToken(String token);
}

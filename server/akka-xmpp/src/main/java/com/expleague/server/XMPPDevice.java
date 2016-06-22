package com.expleague.server;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: solar
 * Date: 11.12.15
 * Time: 18:39
 */
public abstract class XMPPDevice {
  private XMPPUser user;
  private final String passwd;
  private final String name;
  private final boolean expert;
  protected String clientVersion;
  protected String token;

  public XMPPDevice(XMPPUser user, String name, String passwd, boolean expert, String clientVersion, String token) {
    this.user = user;
    this.passwd = passwd;
    this.name = name;
    this.expert = expert;
    this.clientVersion = clientVersion;
    this.token = token;
  }

  public String name() {
    return name;
  }

  public String passwd() {
    return passwd;
  }

  public XMPPUser user() {
    return user;
  }

  public String token() {
    return token;
  }

  public abstract void updateDevice(String token, String clientVersion);

  public boolean expert() {
    return expert;
  }

  public static Pattern versionPattern = Pattern.compile("(.+) ([\\d\\.]+) build (\\d+) @(.+)");
  public int build() {
    if (clientVersion == null)
      return 0;
    final Matcher matcher = versionPattern.matcher(clientVersion);
    if (matcher.find()) {
      return Integer.parseInt(matcher.group(3));
    }
    return 0;
  }

  public String platform() {
    if (clientVersion == null)
      return "iOS";
    final Matcher matcher = versionPattern.matcher(clientVersion);
    if (matcher.find()) {
      return matcher.group(4);
    }
    return "unknown";
  }

  public void updateUser(XMPPUser user) {
    this.user = user;
  }
}

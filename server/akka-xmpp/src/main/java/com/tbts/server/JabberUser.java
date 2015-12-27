package com.tbts.server;

/**
 * User: solar
 * Date: 11.12.15
 * Time: 18:39
 */
public class JabberUser {
  private final String passwd;
  private final String name;

  public JabberUser(String name, String passwd) {
    this.passwd = passwd;
    this.name = name;
  }

  public String name() {
    return name;
  }

  public String passwd() {
    return passwd;
  }
}

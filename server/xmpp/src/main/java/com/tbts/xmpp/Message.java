package com.tbts.xmpp;

/**
 * User: solar
 * Date: 06.12.15
 * Time: 17:41
 */
public class Message extends Stanza {

  @Override
  public String name() {
    return "message";
  }
}

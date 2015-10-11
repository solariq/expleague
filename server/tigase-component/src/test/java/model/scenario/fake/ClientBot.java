package model.scenario.fake;

/**
 * User: solar
 * Date: 11.10.15
 * Time: 19:14
 */
public class ClientBot {
  public final String xmppServer;
  public final String name;

  public ClientBot(String xmppServer, String name) {
    this.xmppServer = xmppServer;
    this.name = name;
  }
}

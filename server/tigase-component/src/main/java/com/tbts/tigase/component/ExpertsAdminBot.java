package com.tbts.tigase.component;

import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.modules.presence.PresenceModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Presence;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.J2SEPresenceStore;
import tigase.jaxmpp.j2se.J2SESessionObject;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.jaxmpp.j2se.connectors.socket.SocketConnector;

/**
 * User: solar
 * Date: 04.11.15
 * Time: 20:22
 */
public class ExpertsAdminBot {
  private final Jaxmpp connection = new Jaxmpp(new J2SESessionObject());
  public static final String EXPERTS_ADMIN_LONG_PASSWORD = "experts-admin-long-password";

  private int expertsCount = -1;
  private final Thread botThread;

  public ExpertsAdminBot(String name) {
    try {
      if (!connection.isConnected()) { // create admin user if necessarily
        BareJID jid = BareJID.bareJIDInstance(name);
        connection.getProperties().setUserProperty(SessionObject.USER_BARE_JID, jid);
        connection.getProperties().setUserProperty(SessionObject.PASSWORD, EXPERTS_ADMIN_LONG_PASSWORD);
        connection.getSessionObject().setProperty(SocketConnector.TLS_DISABLED_KEY, true);
        PresenceModule.setPresenceStore(connection.getSessionObject(), new J2SEPresenceStore());
        connection.getModulesManager().register(new PresenceModule());
        connection.login();
      }
    }
    catch (JaxmppException e) {
      throw new RuntimeException(e);
    }

    botThread = new Thread("Experts admin bot") {
      @Override
      public void run() {
        { // sending presence with experts count
          synchronized (ExpertsAdminBot.this) {
            //noinspection InfiniteLoopStatement
            while (true) {
              try {
                ExpertsAdminBot.this.wait();
                final Presence presence = Stanza.createPresence();
                presence.setStatus(expertsCount + " experts online");
                presence.setShow(Presence.Show.online);
                connection.send(presence);
              } catch (JaxmppException e) {
                throw new RuntimeException(e);
              } catch (InterruptedException ignore) {
              }
              System.out.println("Presence sent");
            }
          }
        }
      }
    };
    botThread.setDaemon(true);
    botThread.start();
  }

  public synchronized void updateExpertsCount(int expertsCount) {
    this.expertsCount = expertsCount;
    notifyAll();
  }
}

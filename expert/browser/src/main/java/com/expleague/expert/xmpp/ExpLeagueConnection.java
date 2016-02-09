package com.expleague.expert.xmpp;

import com.expleague.expert.profile.UserProfile;
import com.spbsu.commons.func.WeakListenerHolder;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import com.spbsu.commons.random.FastRandom;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.sasl.SASLError;
import org.jivesoftware.smack.sasl.SASLErrorException;
import org.jivesoftware.smack.sasl.javax.SASLDigestMD5Mechanism;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smack.util.TLSUtils;
import org.jivesoftware.smackx.iqregister.AccountManager;

import javax.net.ssl.*;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Experts League
 * Created by solar on 09/02/16.
 */
public class ExpLeagueConnection extends WeakListenerHolderImpl<ExpLeagueConnection.Status> implements ConnectionListener {
  private static final Logger log = Logger.getLogger(ExpLeagueConnection.class.getName());
  private static ExpLeagueConnection instance;
  private SSLContext sslctx;
  private FastRandom fastRandom = new FastRandom();

  public ExpLeagueConnection(SSLContext sslctx) {
    this.sslctx = sslctx;
  }

  public static synchronized ExpLeagueConnection instance() {
    if (instance == null) {
      final SSLContext sslctx;
      try {
        sslctx = SSLContext.getInstance("TLS");
        sslctx.init(null, new TrustManager[]{
            new TLSUtils.AcceptAllTrustManager()
        }, new SecureRandom());
      }
      catch (KeyManagementException | NoSuchAlgorithmException e) {
        log.log(Level.SEVERE, "Unable to create SSL context needed to connect to ExpLeague servers");
        throw new RuntimeException(e);
      }
      instance = new ExpLeagueConnection(sslctx);
      SASLAuthentication.registerSASLMechanism(new SASLDigestMD5Mechanism());
    }
    return instance;
  }

  private XMPPTCPConnection connection;
  public void start(UserProfile profile) {
    start(profile, true);
  }

  private void start(UserProfile profile, boolean tryToRegister) {
    if (connection != null && connection.isConnected())
      connection.disconnect();
    final String domain = profile.get(UserProfile.Key.EXP_LEAGUE_DOMAIN);
    XMPPTCPConnectionConfiguration config =
        XMPPTCPConnectionConfiguration.builder()
            .setServiceName(domain)
            .setHost(domain)
            .setPort(5222)
            .setHostnameVerifier((s, sslSession) -> true)
            .setCustomSSLContext(sslctx)
            .setUsernameAndPassword(profile.get(UserProfile.Key.EXP_LEAGUE_USER), profile.get(UserProfile.Key.EXP_LEAGUE_PASSWORD))
            .build();
    connection = new XMPPTCPConnection(config);
    connection.addConnectionListener(this);
    try {
      connection.connect();
      try {
        connection.login();
      }
      catch (SASLErrorException e) {
        if (e.getSASLFailure().getSASLError() == SASLError.not_authorized && tryToRegister) {
          register(profile);
          start(profile, false);
        }
        else throw e;
      }
    }
    catch (SmackException | IOException | XMPPException e) {
      log.log(Level.WARNING, "Unable to connect to " + connection.getHost() + " by " + config.getUsername());
    }
  }

  @Override
  public void connected(XMPPConnection connection) {
    log.fine("Connected to " + connection.getHost());
  }

  @Override
  public void authenticated(XMPPConnection connection, boolean resumed) {
    log.fine("Authenticated as " + connection.getUser());
    invoke(Status.CONNECTED);
  }

  @Override
  public void connectionClosed() {
    invoke(Status.DISCONNECTED);
    log.fine("Connection closed");
  }

  @Override
  public void connectionClosedOnError(Exception e) {
    log.log(Level.WARNING, "Connection closed on error", e);
    invoke(Status.DISCONNECTED);
  }

  @Override
  public void reconnectingIn(int seconds) {
    log.fine("Reconnecting in " + seconds + " seconds");
  }

  @Override
  public void reconnectionFailed(Exception e) {
    log.log(Level.WARNING, "Reconnection failed", e);
    invoke(Status.DISCONNECTED);
  }

  @Override
  public void reconnectionSuccessful() {
    log.fine("Reconnected");
    invoke(Status.CONNECTED);
  }

  public void register(UserProfile profile) {
    final String domain = profile.get(UserProfile.Key.EXP_LEAGUE_DOMAIN);
    final String passwd = fastRandom.nextBase64String(15);
    profile.set(UserProfile.Key.EXP_LEAGUE_PASSWORD, passwd);
    XMPPTCPConnectionConfiguration config =
        XMPPTCPConnectionConfiguration.builder()
            .setServiceName(domain)
            .setHost(domain)
            .setPort(5222)
            .setHostnameVerifier((s, sslSession) -> true)
            .setCustomSSLContext(sslctx)
            .build();
    XMPPTCPConnection connection = new XMPPTCPConnection(config);
    final AccountManager accountManager = AccountManager.getInstance(connection);
    final Map<String, String> map = new HashMap<>();
    map.put("name", profile.name());
    map.put("misc", profile.get(UserProfile.Key.AVATAR_URL));
    map.put("city", profile.get(UserProfile.Key.CITY));
    map.put("state", profile.get(UserProfile.Key.COUNTRY));
    try {
      connection.connect();
      accountManager.createAccount(profile.get(UserProfile.Key.EXP_LEAGUE_USER), passwd, map);
      profile.set(UserProfile.Key.EXP_LEAGUE_ID, profile.get(UserProfile.Key.EXP_LEAGUE_USER) + "@" + profile.get(UserProfile.Key.EXP_LEAGUE_DOMAIN));
    }
    catch (XMPPException | IOException | SmackException e) {
      throw new RuntimeException("Unable to register new user at domain: " + profile.get(UserProfile.Key.EXP_LEAGUE_DOMAIN), e);
    }
  }

  public enum Status {
    CONNECTED,
    DISCONNECTED,
  }
}

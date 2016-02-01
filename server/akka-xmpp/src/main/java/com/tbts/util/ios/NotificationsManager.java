package com.tbts.util.ios;

import com.relayrides.pushy.apns.ApnsClient;
import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;
import com.tbts.server.TBTSServer;
import com.tbts.xmpp.JID;
import io.netty.util.concurrent.Future;

import java.io.File;
import java.util.Calendar;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by solar on 01/02/16.
 */
public class NotificationsManager {
  private static final Logger log = Logger.getLogger(NotificationsManager.class.getName());
  private static NotificationsManager instance;

  public static synchronized NotificationsManager instance() {
    if (instance == null) {
      instance = new NotificationsManager("certs/apns.p12", "");
    }
    return instance;
  }

  private static class UpdatePushNotification extends SimpleApnsPushNotification {
    public UpdatePushNotification(String token, JID from) {
      super(token, "Update", "{" +
          "\"alert\": \"Получено новое сообщение от " + from.local() + "\"," +
          "\"badge\": 1" +
          "}", tomorrow());
    }

    static Date tomorrow() {
      Calendar calendar = Calendar.getInstance();
      calendar.add(Calendar.DATE, 1);
      return calendar.getTime();
    }
  }

  private final ApnsClient<UpdatePushNotification> client;
  public NotificationsManager(String pathToCert, String certPasswd) {
    ApnsClient<UpdatePushNotification> client;
    try {
      client = new ApnsClient<>(new File(pathToCert), certPasswd);
      Future<Void> connect = client.connect(TBTSServer.config().type() == TBTSServer.Cfg.Type.PRODUCTION ? ApnsClient.PRODUCTION_APNS_HOST : ApnsClient.DEVELOPMENT_APNS_HOST);
      connect.await();
    }
    catch (Exception e) {
      log.log(Level.SEVERE, "Unable to start iOS notifications manager!", e);
      client = null;
    }
    this.client = client;
  }

  public void sendPush(JID jid, String token) {
    if (client != null) {
      client.sendNotification(new UpdatePushNotification(token, jid));
    }
    else log.warning("Unable to send push notification to " + jid.toString());
  }
}

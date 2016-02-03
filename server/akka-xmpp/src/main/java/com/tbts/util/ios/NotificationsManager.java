package com.tbts.util.ios;

import com.relayrides.pushy.apns.ApnsClient;
import com.relayrides.pushy.apns.PushNotificationResponse;
import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;
import com.tbts.model.ExpertsProfile;
import com.tbts.server.TBTSServer;
import com.tbts.xmpp.JID;
import com.tbts.xmpp.stanza.Message;
import io.netty.util.concurrent.Future;

import java.io.File;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.ExecutionException;
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
      instance = new NotificationsManager("certs/apns.p12", "tg30239");
    }
    return instance;
  }

  private final ApnsClient<SimpleApnsPushNotification> client;
  public NotificationsManager(String pathToCert, String certPasswd) {
    ApnsClient<SimpleApnsPushNotification> client;
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

  public void sendPush(Message message, String token) {
    if (client != null) {
      log.info("Sending push notification from " + message.from());
      Future<PushNotificationResponse<SimpleApnsPushNotification>> future = null;
      if (message.from().resource().isEmpty()) { // system message
        if (message.has(ExpertsProfile.class)) {
          future = client.sendNotification(new ExpertFoundNotification(token, message.from(), message.get(ExpertsProfile.class)));
        }
      }
      else {
        if (message.body().startsWith("{\"type\":\"response\"")) {
          future = client.sendNotification(new ResponseReceivedNotification(token, message.from()));
        }
        else if (!message.body().isEmpty()){
          future = client.sendNotification(new MessageReceivedNotification(token, message.from(), message.body()));
        }
      }
      try {
        if (future == null)
          return;
        final PushNotificationResponse<SimpleApnsPushNotification> now = future.get();
        if (now.isAccepted())
          log.fine("Successfully have sent push notification to " + message.to().local() + ".");
        else
          log.warning("Failed to sent push notification to " + message.to().local() + " with reason: " + now.getRejectionReason() + " token used: " + token);
      }
      catch (InterruptedException | ExecutionException e) {
        log.log(Level.WARNING, "Exception during push notification send.", e);
      }
    }
    else log.warning("Unable to send push notification to " + message.to());
  }

  private static class ExpertFoundNotification extends SimpleApnsPushNotification {
    public ExpertFoundNotification(String token, JID from, ExpertsProfile profile) {
      super(token, "com.expleague.ios.unSearch", "{\"aps\":{" +
          "\"alert\": \"Эксперт найден! Для Вас работает " + profile.login() + "!\", " +
          "\"content-available\": 1" +
          "}, \"order\": \"" + from.local() + "\"}", tomorrow());
    }
  }

  private static class ResponseReceivedNotification extends SimpleApnsPushNotification {
    public ResponseReceivedNotification(String token, JID from) {
      super(token, "com.expleague.ios.unSearch", "{\"aps\":{" +
          "\"alert\": \"Получен ответ на задание от " + from.resource() + "\", " +
          "\"content-available\": 1" +
          "}, \"order\": \"" + from.local() + "\"}", tomorrow());
    }
  }

  private static class MessageReceivedNotification extends SimpleApnsPushNotification {
    public MessageReceivedNotification(String token, JID from, String message) {
      super(token, "com.expleague.ios.unSearch", "{\"aps\":{" +
          "\"alert\": \"Получено новое сообщение от " + from.resource() + ": '" + message + "'\", " +
          "\"content-available\": 1"+
          "}, \"order\": \"" + from.local() + "\"}", tomorrow());
    }
  }

  private static Date tomorrow() {
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.DATE, 1);
    return calendar.getTime();
  }
}

package com.expleague.util.ios;

import com.expleague.model.Answer;
import com.expleague.model.Operations;
import com.expleague.server.Roster;
import com.expleague.server.agents.LaborExchange;
import com.expleague.server.agents.XMPP;
import com.relayrides.pushy.apns.ApnsClient;
import com.relayrides.pushy.apns.PushNotificationResponse;
import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;
import com.expleague.model.ExpertsProfile;
import com.expleague.server.ExpLeagueServer;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
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
    // todo: hard to test this singleton
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
        Future<Void> connect = client.connect(ExpLeagueServer.config().type() == ExpLeagueServer.Cfg.Type.PRODUCTION ? ApnsClient.PRODUCTION_APNS_HOST : ApnsClient.DEVELOPMENT_APNS_HOST);
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
      SimpleApnsPushNotification notification = null;
      if (message.from().resource().isEmpty()) { // system message
        if (message.has(Operations.Start.class) && message.has(ExpertsProfile.class)) {
          notification = new ExpertFoundNotification(token, message.from(), message.get(ExpertsProfile.class));
        }
      }
      else {
        final ExpertsProfile profile = Roster.instance().profile(XMPP.jid(message.from().resource()));
        if (message.has(Answer.class)) {
          notification = new ResponseReceivedNotification(token, message.from(), profile);
        }
        else if (!message.body().isEmpty()){
          notification = new MessageReceivedNotification(token, message.from(), profile, message.body());
        }
      }
      if (notification == null)
        return;
      sendPush(notification, message.to().local());
    }
    else log.warning("Unable to send push notification to " + message.to());
  }

  private void sendPush(SimpleApnsPushNotification notification, String to) {
    try {
      Future<PushNotificationResponse<SimpleApnsPushNotification>> future = client.sendNotification(notification);
      final PushNotificationResponse<SimpleApnsPushNotification> now = future.get();
      if (now.isAccepted())
        log.fine("Successfully have sent push notification to " + to + ". Text: " + notification.getPayload());
      else
        log.warning("Failed to sent push notification to " + to + " with reason: " + now.getRejectionReason() + " token used: " + notification.getToken());
    }
    catch (InterruptedException | ExecutionException e) {
      log.log(Level.WARNING, "Exception during push notification send.", e);
    }
  }

  public void notifyBestAnswer(LaborExchange.AnswerOfTheWeek aow) {
    Roster.instance().allDevices().forEach(device -> {
      if (device.token() != null) {
        sendPush(new SimpleApnsPushNotification(device.token(), "com.expleague.ios.unSearch", "{\"aps\":{" +
            "\"alert\": \"Новый ответ недели на тему '" + aow.topic().replace('"', ' ') + "'\", " +
            "\"content-available\": 1," +
            "\"sound\": \"owl.wav\"" +
            "}, \"aow\": \"" + aow.roomId() + "\"}"
        ), device.name());
      }
    });
  }

  private static class ExpertFoundNotification extends SimpleApnsPushNotification {
    public ExpertFoundNotification(String token, JID from, ExpertsProfile profile) {
      super(token, "com.expleague.ios.unSearch", "{\"aps\":{" +
          "\"alert\": \"Эксперт найден! Для Вас работает " + profile.name() + "!\", " +
          "\"content-available\": 1," +
          "\"sound\": \"owl.wav\"" +
          "}, \"order\": \"" + from.local() + "\"}", tomorrow());
    }
  }

  private static class ResponseReceivedNotification extends SimpleApnsPushNotification {
    public ResponseReceivedNotification(String token, JID from, ExpertsProfile expert) {
      super(token, "com.expleague.ios.unSearch", "{\"aps\":{" +
          "\"alert\": \"Получен ответ на задание от " + expert.name() + "\", " +
          "\"content-available\": 1," +
          "\"sound\": \"owl.wav\"" +
          "}, \"order\": \"" + from.local() + "\"}", tomorrow());
    }
  }

  private static class MessageReceivedNotification extends SimpleApnsPushNotification {
    public MessageReceivedNotification(String token, JID from, ExpertsProfile expert, String message) {
      super(token, "com.expleague.ios.unSearch", "{\"aps\":{" +
          "\"alert\": \"Получено новое сообщение от " + expert.name() + ": '" + message.replace("\"", "") + "'\", " +
          "\"content-available\": 1,"+
          "\"sound\": \"owl.wav\"" +
          "}, \"order\": \"" + from.local() + "\"}", tomorrow());
    }
  }

  private static Date tomorrow() {
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.DATE, 1);
    return calendar.getTime();
  }
}

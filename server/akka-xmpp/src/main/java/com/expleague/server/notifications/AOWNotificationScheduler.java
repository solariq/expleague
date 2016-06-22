package com.expleague.server.notifications;

import com.expleague.server.agents.LaborExchange;
import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

import java.util.Date;

/**
 * Experts League
 * Created by solar on 18.06.16.
 */
class AOWNotificationScheduler extends NotificationScheduler {
  private final LaborExchange.AnswerOfTheWeek aow;

  public AOWNotificationScheduler(LaborExchange.AnswerOfTheWeek aow) {
    super(null);
    this.aow = aow;
  }

  @Override
  SimpleApnsPushNotification visibleNotification(String token) {
    return new SimpleApnsPushNotification(token, "com.expleague.ios.unSearch", "{\"aps\":{" +
        "\"alert\": \"Новый ответ недели на тему '" + aow.topic().replace('"', ' ') + "'\", " +
        "\"content-available\": 1," +
        "\"sound\": \"owl.wav\"" +
        "}, \"aow\": \"" + aow.roomId() + "\"}"
    );
  }

  @Override
  SimpleApnsPushNotification failedToDeliverNotification(String token) {
    return new SimpleApnsPushNotification(token, "com.expleague.ios.unSearch", "{\"aps\":{" +
        "\"alert\": \"Новый ответ недели на тему '" + aow.topic().replace('"', ' ') + "', который мы пока не смогли доставить\", " +
        "\"content-available\": 1," +
        "\"sound\": \"owl.wav\"" +
        "}, \"aow\": \"" + aow.roomId() + "\", \"visible\": 1}"
    );
  }

  @Override
  SimpleApnsPushNotification hiddenNotification(String token, Date relevant) {
    return new SimpleApnsPushNotification(token, "com.expleague.ios.unSearch", "{\"aps\":{\"content-available\": 1}, \"aow\": \"" + aow.roomId() + "\"}");
  }
}

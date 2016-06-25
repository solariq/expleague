package com.expleague.server.notifications;

import com.expleague.server.agents.LaborExchange;
import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

import java.util.Date;

/**
 * Experts League
 * Created by solar on 18.06.16.
 */
class AOWNotificationScheduler extends NotificationScheduler {
  final String topic;
  private final LaborExchange.AnswerOfTheWeek aow;

  public AOWNotificationScheduler(LaborExchange.AnswerOfTheWeek aow) {
    super(null);
    this.aow = aow;
    topic = aow.topic().replace('"', ' ');
  }

  @Override
  SimpleApnsPushNotification visibleNotification(String token) {
    return new SimpleApnsPushNotification(token, "com.expleague.ios.unSearch", "{\"aps\":{" +
        "\"alert\": \"Новый ответ недели на тему '" + topic + "'\", " +
        "\"content-available\": 1," +
        "\"sound\": \"owl.wav\"" +
        "}, \"aow\": \"" + aow.roomId() + "\"}"
    );
  }

  @Override
  SimpleApnsPushNotification failedToDeliverNotification(String token) {
    return new SimpleApnsPushNotification(token, "com.expleague.ios.unSearch", "{\"aps\":{" +
        "\"alert\": \"Новый ответ недели на тему '" + topic + "', который мы пока не смогли доставить\", " +
        "\"content-available\": 1," +
        "\"sound\": \"owl.wav\"" +
        "}, \"aow\": \"" + topic + "\", \"visible\": 1}"
    );
  }

  @Override
  SimpleApnsPushNotification hiddenNotification(String token, Date relevant) {
    return new SimpleApnsPushNotification(token, "com.expleague.ios.unSearch", "{\"aps\":{\"content-available\": 1}, \"aow\": \"" + topic + "\"}");
  }
}

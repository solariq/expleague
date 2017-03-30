package com.expleague.server.notifications;

import com.expleague.server.XMPPDevice;
import com.expleague.server.agents.LaborExchange;
import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.List;

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
        "\"alert\": \"Пятница — время для ответа недели: '" + topic + "'\", " +
        "\"content-available\": 1," +
        "\"badge\": 1," +
        "\"sound\": \"owl.wav\"" +
        "}, \"aow\": \"" + aow.roomId() + "\"}"
    );
  }

  @Override
  SimpleApnsPushNotification failedToDeliverNotification(String token) {
    return new SimpleApnsPushNotification(token, "com.expleague.ios.unSearch", "{\"aps\":{" +
        "\"alert\": \"Пятница — время для ответа недели:  '" + topic + "', который мы пока не смогли доставить\", " +
        "\"content-available\": 1," +
        "\"badge\": 1," +
        "\"sound\": \"owl.wav\"" +
        "}, \"aow\": \"" + aow.roomId() + "\"}"
    );
  }

  @Override
  SimpleApnsPushNotification hiddenNotification(String token, Date relevant) {
    return new SimpleApnsPushNotification(token, "com.expleague.ios.unSearch", "{\"aps\":{\"content-available\": 1}, \"aow\": \"" + aow.roomId() + "\", \"title\": \"" + topic + "\"}");
  }

  @Nullable
  @Override
  SimpleApnsPushNotification build(XMPPDevice device, List<ScheduledNotification> laterQueue) {
    if (device.build() < 50)
      return visibleNotification(device.token());
    else
      return super.build(device, laterQueue);
  }
}

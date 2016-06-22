package com.expleague.server.notifications;

import com.expleague.server.XMPPDevice;
import com.expleague.xmpp.stanza.Message;
import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;
import org.jetbrains.annotations.Nullable;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Experts League
 * Created by solar on 18.06.16.
 */
abstract class NotificationScheduler {
  protected final Message msg;

  NotificationScheduler(Message msg) {
    this.msg = msg;
  }

  @Nullable
  SimpleApnsPushNotification build(XMPPDevice device, List<ScheduledNotification> laterQueue) {
    final String token = device.token();
    final Calendar calendar = Calendar.getInstance(NotificationsManager.TIME_ZONE);
    final Calendar toCalendar = Calendar.getInstance(NotificationsManager.TIME_ZONE);
    if (token == null)
      return null;
    if (device.build() < 23) {
      return visibleNotification(token);
    } else if (device.build() < 31) {
      calendar.add(Calendar.MINUTE, 10);
      toCalendar.add(Calendar.MINUTE, 10);
      laterQueue.add(new ScheduledNotification(calendar.getTime(), visibleNotification(token)));
      return hiddenNotification(token, toCalendar.getTime());
    } else {
      toCalendar.add(Calendar.MINUTE, 2);
      final SimpleApnsPushNotification first = hiddenNotification(token, toCalendar.getTime());
      calendar.add(Calendar.MINUTE, 2);
      toCalendar.add(Calendar.MINUTE, 5);
      laterQueue.add(new ScheduledNotification(calendar.getTime(), hiddenNotification(token, toCalendar.getTime())));
      calendar.add(Calendar.MINUTE, 5);
      toCalendar.add(Calendar.MINUTE, 10);
      laterQueue.add(new ScheduledNotification(calendar.getTime(), hiddenNotification(token, toCalendar.getTime())));
      calendar.add(Calendar.MINUTE, 10);
      laterQueue.add(new ScheduledNotification(calendar.getTime(), failedToDeliverNotification(token)));
      return first;
    }
  }

  abstract SimpleApnsPushNotification visibleNotification(String token);

  abstract SimpleApnsPushNotification failedToDeliverNotification(String token);

  SimpleApnsPushNotification hiddenNotification(String token, Date relevant) {
    return new SimpleApnsPushNotification(token, "com.expleague.ios.unSearch", "{\"aps\":{\"content-available\": 1}, \"id\": \"" + msg.id() + "\"}", relevant);
  }

  static Date tomorrow() {
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.DATE, 1);
    return calendar.getTime();
  }
}

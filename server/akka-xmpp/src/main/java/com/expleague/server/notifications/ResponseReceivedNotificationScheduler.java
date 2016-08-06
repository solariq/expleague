package com.expleague.server.notifications;

import com.expleague.model.ExpertsProfile;
import com.expleague.server.Roster;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

/**
 * Experts League
 * Created by solar on 18.06.16.
 */
class ResponseReceivedNotificationScheduler extends NotificationScheduler {
  private final JID from;
  private final ExpertsProfile expertProfile;

  public ResponseReceivedNotificationScheduler(Message msg) {
    super(msg);
    from = msg.from();
    expertProfile = Roster.instance().profile(msg.from().resource());
  }

  @Override
  SimpleApnsPushNotification visibleNotification(String token) {
    return new SimpleApnsPushNotification(token, "com.expleague.ios.unSearch", "{\"aps\":{" +
        "\"alert\": \"Получен ответ на задание от " + expertProfile.name() + "\", " +
        "\"content-available\": 1," +
        "\"sound\": \"owl.wav\"" +
        "}, \"order\": \"" + from.local() + "\"}", NotificationScheduler.tomorrow());
  }

  @Override
  SimpleApnsPushNotification failedToDeliverNotification(String token) {
    return new SimpleApnsPushNotification(token, "com.expleague.ios.unSearch", "{\"aps\":{" +
        "\"alert\": \"Получен ответ на задание от " + expertProfile.name() + ", который мы никак не можем доставить\", " +
        "\"content-available\": 1," +
        "\"sound\": \"owl.wav\"" +
        "}, \"id\": \"" + msg.id() + "\", \"visible\": 1}", NotificationScheduler.tomorrow());
  }
}

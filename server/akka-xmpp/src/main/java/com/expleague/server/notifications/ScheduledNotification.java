package com.expleague.server.notifications;

import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

import java.util.Date;

/**
 * Experts League
 * Created by solar on 18.06.16.
 */
class ScheduledNotification {
  Date when;
  SimpleApnsPushNotification notification;

  public ScheduledNotification(Date when, SimpleApnsPushNotification notification) {
    this.when = when;
    this.notification = notification;
  }
}

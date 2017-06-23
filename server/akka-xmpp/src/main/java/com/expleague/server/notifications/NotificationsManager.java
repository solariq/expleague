package com.expleague.server.notifications;

import akka.actor.ActorContext;
import akka.actor.UntypedActor;
import akka.util.Timeout;
import com.expleague.model.Answer;
import com.expleague.model.ExpertsProfile;
import com.expleague.model.Operations;
import com.expleague.server.ExpLeagueServer;
import com.expleague.server.Roster;
import com.expleague.server.XMPPDevice;
import com.expleague.server.agents.LaborExchange;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.util.akka.AkkaTools;
import com.expleague.xmpp.stanza.Message;
import com.relayrides.pushy.apns.ApnsClient;
import com.relayrides.pushy.apns.PushNotificationResponse;
import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;
import io.netty.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;
import scala.concurrent.duration.Duration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Experts League
 * Created by solar on 01/02/16.
 */
public class NotificationsManager extends ActorAdapter<UntypedActor> {
  private static final Logger log = Logger.getLogger(NotificationsManager.class.getName());
  public static final TimeZone TIME_ZONE = TimeZone.getTimeZone("UTC/Greenwich");
  private static final String NOTIFICATIONS_ACTOR_PATH = "/user/notifications";

  public static void send(Message message, ActorContext context) {
    context.actorSelection(NOTIFICATIONS_ACTOR_PATH).forward(message, context);
  }

  public static void delivered(String id, XMPPDevice device, ActorContext context) {
    context.actorSelection(NOTIFICATIONS_ACTOR_PATH).forward(new Delivered(id, device), context);
  }

  private Map<String, List<ScheduledNotification>> undelivered = new HashMap<>();
  private LaborExchange.AnswerOfTheWeek aow = LaborExchange.board().answerOfTheWeek();
  private final ApnsClient<SimpleApnsPushNotification> client;

  public NotificationsManager(String pathToCert, String certPasswd) {
    if (pathToCert == null || certPasswd == null) {
      this.client = null;
      return;
    }

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

  @ActorMethod
  public void notify(Message msg) {
    NotificationScheduler scheduler = null;

    if (msg.from().isSystem()) { // system message
      if (msg.has(Operations.Start.class) && msg.has(ExpertsProfile.class)) {
        scheduler = new ExpertFoundNotificationScheduler(msg);
      }
    }
    else if (msg.has(Answer.class)) {
      scheduler = new ResponseReceivedNotificationScheduler(msg);
    }
    else if (!msg.body().isEmpty()) {
      scheduler = new MessageReceivedNotificationScheduler(msg);
    }
    if (scheduler == null)
      return;

    final XMPPDevice[] devices = Roster.instance().user(msg.to().local()).devices();
    final String id = msg.id();
    for (final XMPPDevice device : devices) {
      schedule(id, scheduler, device);
    }
  }

  @ActorMethod
  public void delivered(Delivered delivered) {
    undelivered.remove(key(delivered.id, delivered.device));
  }

  @ActorMethod
  public void tick(Timeout to) {
    try {
      { // notify scheduled
        final Date now = new Date();
        undelivered.values().forEach(queue -> {
          SimpleApnsPushNotification notification = null;
          final Iterator<ScheduledNotification> it = queue.iterator();
          while (it.hasNext()) {
            ScheduledNotification next = it.next();
            if (next.when.after(now))
              break;
            notification = next.notification;
            it.remove();
          }
          sendPush(notification);
        });
      }

      { // cleanup
        final String[] keys = undelivered.keySet().toArray(new String[undelivered.size()]);
        for (String key : keys) {
          if (undelivered.get(key).isEmpty())
            undelivered.remove(key);
        }
      }

      { // aow
        final LaborExchange.AnswerOfTheWeek aow = LaborExchange.board().answerOfTheWeek();
        if (this.aow == null) {
          this.aow = aow;
        }
        else if (aow != null && !aow.equals(this.aow)) {
          AOWNotificationScheduler scheduler = new AOWNotificationScheduler(aow);
          try (final Stream<XMPPDevice> allDevices = Roster.instance().allDevices()) {
            allDevices.forEach(device -> schedule(aow.roomId(), scheduler, device));
          }
          this.aow = aow;
        }
      }
    }
    finally {
      AkkaTools.scheduleTimeout(context(), Duration.apply(5, TimeUnit.SECONDS), self()); // tick
    }
  }

  @Override
  public void preStart() throws Exception {
    super.preStart();
    AkkaTools.scheduleTimeout(context(), Duration.apply(5, TimeUnit.SECONDS), self()); // tick start
  }

  private void schedule(String id, NotificationScheduler scheduler, XMPPDevice device) {
    if (device.token() == null)
      return;
    List<ScheduledNotification> latter = new ArrayList<>();
    final SimpleApnsPushNotification notification = scheduler.build(device, latter);
    if (notification != null)
      sendPush(notification);
    if (!latter.isEmpty()) {
      undelivered.put(key(id, device), latter);
    }
  }

  @NotNull
  private String key(String id, XMPPDevice device) {
    return device.name() + "-" + id;
  }

  private void sendPush(SimpleApnsPushNotification notification) {
    if (notification == null || client == null)
      return;
    try {
      Future<PushNotificationResponse<SimpleApnsPushNotification>> future = client.sendNotification(notification);
      final PushNotificationResponse<SimpleApnsPushNotification> now = future.get(10, TimeUnit.SECONDS);
      if (now.isAccepted())
        log.fine("Successfully have sent push notification to " + notification.getToken() + ". Text: " + notification.getPayload());
      else
        log.warning("Failed to sent push notification to " + notification.getToken() + " with reason: " + now.getRejectionReason() + " token used: " + notification.getToken());
    }
    catch (InterruptedException | ExecutionException | TimeoutException e) {
      log.log(Level.WARNING, "Exception during push notification send.", e);
    }
  }

  private static class Delivered {
    String id;
    XMPPDevice device;

    public Delivered(String id, XMPPDevice device) {
      this.id = id;
      this.device = device;
    }
  }
}

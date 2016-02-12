package com.expleague.expert.forms.chat;

import javafx.application.Platform;
import javafx.scene.control.Label;

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Experts League
 * Created by solar on 11/02/16.
 */
public class TimeoutUtil {
  public static String formatPeriodRussian(long interval) {
    int days = (int)Math.floor(interval / (24 * 60 * 60 * 1000));
    int ending = days % 10;
    String text = "";
    if (ending == 0 && days > 0) {
      text += days + " дней, ";
    }
    else if (ending > 0 && ending < 2 && (days < 10 || days > 20)) {
      text += days + " день, ";
    }
    else if (ending > 2 && ending < 5 && (days < 10 || days > 20)) {
      text += days + " дня, ";
    }
    else if (ending >= 5) {
      text += days + " дней, ";
    }
    final DateFormat formatter = days == 0 ? new SimpleDateFormat("H:mm:ss") : new SimpleDateFormat("H:mm");
    formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
    text += formatter.format(new Date(interval));
    return text;
  }

  public static void formatTimeout(long interval, Label label, boolean isShort) {
    Platform.runLater(() -> {
      if (interval > 0) {
        label.setText((!isShort ? "Осталось: " : "") + formatPeriodRussian(interval));
        label.setStyle("-fx-text-fill: lightgray;");
      }
      else {
        label.setText((!isShort ? "Просрочено: " : "") + formatPeriodRussian(-interval));
        label.setStyle("-fx-text-fill: red;");
      }
    });
  }

  public static void setTimer(Label timerLabel, Date expires, boolean isShort) {
    final Timer timer = new Timer(true);
    final WeakReference<Label> owner = new WeakReference<>(timerLabel);
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        final Label label = owner.get();
        if (label == null)
          timer.cancel();
        else
          TimeoutUtil.formatTimeout(expires.getTime() - System.currentTimeMillis(), label, isShort);
      }
    }, 0, 1000);
  }
}

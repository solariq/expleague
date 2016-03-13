package com.expleague.util.akka;

/**
 * @author vpdelta
 */
public class MessageCaptureTestCase {
  public static void setUpMessageCapture(final MessageCapture messageCapture) {
    MessageCapture.Holder.capture = messageCapture;
  }
}
package com.expleague.server.admin.reports.dump.quality_monitoring.visitors;

import com.expleague.model.Operations;
import com.expleague.server.admin.reports.dump.visitors.SpecifiedOrderVisitor;
import com.expleague.xmpp.stanza.Message;

/**
 * User: Artem
 * Date: 01.09.2017
 */
public class FeedbackVisitor extends SpecifiedOrderVisitor<FeedbackVisitor.FeedbackResult> {
  public static final int NO_SCORE = -1;

  private int stars = NO_SCORE;
  private String comment = "";
  private Message potentialComment = null;

  public FeedbackVisitor(String startMessageId, String stopMessageId) {
    super(startMessageId, stopMessageId);
  }

  @Override
  public FeedbackResult result() {
    return new FeedbackResult(stars, comment);
  }

  @Override
  protected void process(Message message) {
    super.process(message);
    if (message.has(Message.Body.class)) {
      potentialComment = message;
    } else if (message.has(Operations.Feedback.class)) {
      final Operations.Feedback feedback = message.get(Operations.Feedback.class);
      stars = feedback.stars();
      if (potentialComment != null && message.ts() == potentialComment.ts()) {
        comment = potentialComment.body();
      }
      done();
    }
  }

  public static class FeedbackResult {
    private final int stars;
    private final String comment;

    public FeedbackResult(int stars, String comment) {
      this.stars = stars;
      this.comment = comment;
    }

    public int stars() {
      return stars;
    }

    public String comment() {
      return comment;
    }
  }
}

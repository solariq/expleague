package com.expleague.server.admin.reports.dump.quality_monitoring;

import com.expleague.model.Offer;
import com.expleague.server.admin.reports.dump.BaseDumpReportHandler;
import com.expleague.server.admin.reports.dump.DumpVisitor;
import com.expleague.server.admin.reports.dump.quality_monitoring.visitors.*;
import com.expleague.server.admin.reports.dump.visitors.OrdersSearchVisitor;
import com.expleague.server.admin.reports.dump.visitors.RoomVisitor;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.stanza.Message;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * User: Artem
 * Date: 21.08.2017
 */
public class QualityMonitoringReportHandler extends BaseDumpReportHandler {
  private ReportRequest reportRequest;

  @ActorMethod
  public void report(ReportRequest reportRequest) {
    this.reportRequest = reportRequest;
    headers("ts", "room", "status", "topic", "continue", "location", "urgency", "client", "score", "score comment");
    startProcessing(reportRequest.start(), reportRequest.end());
    sender().tell(build(), self());
  }

  @Override
  protected void process(String roomId, List<Message> dump) {
    final OrdersSearchVisitor ordersSearchVisitor = new ClientOrdersSearchVisitor(reportRequest.clientId());
    dump.forEach(ordersSearchVisitor::visit);

    final List<OrdersSearchVisitor.Order> orders = ordersSearchVisitor.result();
    final List<List<RoomVisitor<?>>> intervalVisitors = orders
        .stream()
        .filter(order -> order.ts() > reportRequest.start() && order.ts() < reportRequest.end())
        .map(order -> Arrays.asList(
            new OfferVisitor(order.firstMessageId(), order.lastMessageId()),
            new StatusVisitor(order.firstMessageId(), order.lastMessageId()),
            new ContinueVisitor(order.firstMessageId()),
            new FeedbackVisitor(order.firstMessageId(), order.lastMessageId())
        ))
        .collect(Collectors.toList());

    final Format format = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
    dump.forEach(message -> intervalVisitors.forEach(interval -> interval.forEach(visitor -> visitor.visit(message))));
    intervalVisitors.forEach(visitors -> {
      Message offerMessage = null;
      StatusVisitor.OrderStatus shortAnswer = null;
      String continueText = "";
      FeedbackVisitor.FeedbackResult feedbackResult = null;
      for (DumpVisitor visitor : visitors) {
        if (visitor instanceof OfferVisitor)
          offerMessage = ((OfferVisitor) visitor).result();
        else if (visitor instanceof StatusVisitor)
          shortAnswer = ((StatusVisitor) visitor).result();
        else if (visitor instanceof ContinueVisitor)
          continueText = ((ContinueVisitor) visitor).result();
        else if (visitor instanceof FeedbackVisitor)
          feedbackResult = ((FeedbackVisitor) visitor).result();
      }
      {
        assert offerMessage != null;
        assert shortAnswer != null;
        assert feedbackResult != null;
      }

      final Offer offer = offerMessage.get(Offer.class);
      row(
          format.format(new Date(offerMessage.ts())),
          roomId,
          Integer.toString(shortAnswer.index()),
          offer.topic(),
          continueText,
          offer.location() == null ? "" : offer.location().latitude() + " ; " + offer.location().longitude(),
          offer.urgency() == null ? "" : offer.urgency().name(),
          offer.client() == null ? offerMessage.from().local() : offer.client().local(),
          feedbackResult.stars() == FeedbackVisitor.NO_SCORE ? "" : Integer.toString(feedbackResult.stars()),
          feedbackResult.comment()
      );
    });
  }

  public static class ReportRequest {
    private final long start;
    private final long end;
    private final String clientId;


    public ReportRequest(long start, long end, String clientId) {
      this.start = start;
      this.end = end;
      this.clientId = clientId;
    }

    long start() {
      return start;
    }

    long end() {
      return end;
    }

    String clientId() {
      return clientId;
    }
  }
}
